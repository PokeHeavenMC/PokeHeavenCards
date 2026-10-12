package com.howlite.cobblemoncards.render;

import com.cobblemon.mod.common.api.pokemon.PokemonSpecies;
import com.cobblemon.mod.common.client.gui.PokemonGuiUtilsKt;
import com.cobblemon.mod.common.client.render.SpriteType;
import com.cobblemon.mod.common.client.render.models.blockbench.FloatingState;
import com.cobblemon.mod.common.client.render.models.blockbench.repository.VaryingModelRepository;
import com.cobblemon.mod.common.entity.PoseType;
import com.cobblemon.mod.common.util.math.QuaternionUtilsKt;
import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.pipeline.TextureTarget;
import com.mojang.blaze3d.platform.Lighting;
import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexSorting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.entity.EntityRenderDispatcher;
import net.minecraft.resources.ResourceLocation;
import org.joml.Matrix4f;
import org.joml.Matrix4fStack;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Set;

/**
 * Rend le modèle 3D Cobblemon d'une espèce dans un framebuffer hors écran et relit les pixels,
 * pour en faire un faux sprite 2D. La pose est {@link PoseType#PROFILE}, c'est-à-dire exactement
 * celle du PC / de la boîte de rangement.
 *
 * ⚠️ NE JAMAIS APPELER DEPUIS UNE MÉTHODE DE RENDU.
 * {@code drawProfilePokemon} vide ({@code flush}) le {@code MultiBufferSource.BufferSource} partagé
 * de Minecraft. L'appeler au milieu du rendu d'un item viderait la géométrie déjà empilée par
 * d'autres mods et corromprait la frame. Le seul point d'appel légitime est
 * {@link PokemonProfileTextures#processQueue()}, exécuté au tick client : hors de toute passe de
 * dessin, avec le framebuffer principal lié, et hors du pipeline d'Iris.
 */
final class PokemonProfileBaker {

    private static final Logger LOGGER = LoggerFactory.getLogger("cobblemon-cards");

    /** Résultat d'une tentative de cuisson. */
    enum Status {
        /** Image produite. */
        OK,
        /** Les modèles Cobblemon ne sont pas encore chargés : réessayer plus tard, sans pénalité. */
        NOT_READY,
        /** Échec définitif pour cette espèce (modèle absent, poser cassé, exception de rendu). */
        FAILED
    }

    record Result(Status status, NativeImage image) {
        static final Result NOT_READY = new Result(Status.NOT_READY, null);
        static final Result FAILED = new Result(Status.FAILED, null);
    }

    /**
     * Le contenu utile occupe le coin haut-gauche du canevas ; le reste est du padding transparent.
     * Les ratios contenu/canevas doivent rester 40/48 et 30/32, parce que le quad de la carte
     * échantillonne toujours avec UV_40x30_U et UV_40x30_V (voir CardItemRenderer).
     */
    static final int CONTENT_W = 160;
    static final int CONTENT_H = 120;
    static final int CANVAS_W = 192;   // 160 / 192 == 40 / 48
    static final int CANVAS_H = 128;   // 120 / 128 == 30 / 32

    /** Taille de la boîte du ModelWidget dans le PC de Cobblemon, dont on reprend le cadrage. */
    private static final float PC_BOX = 66.0f;
    /** Facteur d'échelle entre la boîte du PC et notre contenu. */
    private static final float K = CONTENT_H / PC_BOX;

    /** Réglage de cadrage. Augmenter agrandit le Pokémon dans la vignette. */
    private static final float ZOOM = 1.0f;

    /** Réutilisée entre les bakes : allouer un framebuffer par espèce serait du gâchis. */
    private static RenderTarget target;

    private PokemonProfileBaker() {}

    /** Libère le framebuffer. Appelé au rechargement des ressources. */
    static void releaseTarget() {
        if (target != null) {
            target.destroyBuffers();
            target = null;
        }
    }

    static Result bake(ResourceLocation speciesId, Set<String> aspects) {
        RenderSystem.assertOnRenderThread();

        // --- Pré-vol, sans toucher à OpenGL ---
        // getPoser() lève une NPE si le dépôt de modèles n'a pas encore été rechargé, et
        // getByIdentifier() est checkNotNull'd côté Kotlin : les deux doivent être écartés ici.
        var variations = VaryingModelRepository.INSTANCE.getVariations();
        if (variations.isEmpty() || !variations.containsKey(speciesId)) {
            return Result.NOT_READY;
        }
        try {
            if (PokemonSpecies.INSTANCE.getByIdentifier(speciesId) == null) {
                return Result.FAILED;
            }
        } catch (Throwable t) {
            return Result.FAILED;
        }

        Minecraft mc = Minecraft.getInstance();
        EntityRenderDispatcher dispatcher = mc.getEntityRenderDispatcher();
        Quaternionf previousCamera = new Quaternionf(dispatcher.cameraOrientation());
        Matrix4fStack modelView = RenderSystem.getModelViewStack();

        RenderSystem.backupProjectionMatrix();
        modelView.pushMatrix();

        try {
            if (target == null) {
                target = new TextureTarget(CANVAS_W, CANVAS_H, true, Minecraft.ON_OSX);
            }
            target.setClearColor(0.0f, 0.0f, 0.0f, 0.0f);
            target.clear(Minecraft.ON_OSX);
            target.bindWrite(true);

            // Projection GUI classique : une unité ortho == un pixel de canevas, ce qui permet de
            // transposer directement les constantes de cadrage du PC.
            RenderSystem.setProjectionMatrix(
                    new Matrix4f().setOrtho(0.0f, CANVAS_W, CANVAS_H, 0.0f, 1000.0f, 21000.0f),
                    VertexSorting.ORTHOGRAPHIC_Z);
            modelView.identity().translate(0.0f, 0.0f, -11000.0f);
            RenderSystem.applyModelViewMatrix();
            RenderSystem.enableDepthTest();
            RenderSystem.depthMask(true);

            FloatingState state = new FloatingState();
            state.setCurrentAspects(aspects);

            // drawProfilePokemon court-circuite vers un sprite 2D si l'espèce en fournit un, et
            // saute alors toute la transformation de profil : le cadrage n'est pas le même.
            boolean spritePath = VaryingModelRepository.INSTANCE.getSprite(speciesId, state, SpriteType.PROFILE) != null;

            PoseStack pose = new PoseStack();
            if (spritePath) {
                // renderSprite émet un quad x ∈ [-1,1], y ∈ [0,2] après mise à l'échelle.
                pose.translate(CONTENT_W * 0.5f, CONTENT_H * 0.5f, 0.0f);
                float s = (CONTENT_H * 0.9f) / (2.0f * 20.0f) * ZOOM;
                pose.scale(s, s, s);
            } else {
                // Recette reprise telle quelle du ModelWidget du PC (offsetY -10, baseScale 2.0).
                pose.translate(CONTENT_W * 0.5f, -10.0f * K * ZOOM, 0.0f);
                float s = 2.0f * K * ZOOM;
                pose.scale(s, s, s);
            }

            // ⚠️ drawProfilePokemon appelle conjugate() SUR PLACE : un Quaternionf réutilisé
            // retournerait le modèle un bake sur deux. Toujours en allouer un neuf.
            Quaternionf rotation = QuaternionUtilsKt.fromEulerXYZDegrees(
                    new Quaternionf(), new Vector3f(13.0f, 325.0f, 0.0f));

            PokemonGuiUtilsKt.drawProfilePokemon(
                    speciesId,
                    pose,
                    rotation,
                    PoseType.PROFILE,
                    state,
                    0.0f,   // partialTicks — FloatingState les accumule, 0 sur un état neuf = frame figée
                    20.0f,  // scale (la valeur par défaut de Cobblemon)
                    true,   // applyProfileTransform : utilise le cadrage de profil du modèle
                    false,  // applyBaseScale
                    false,  // doQuirks : les quirks sont aléatoires, on veut un rendu déterministe
                    1.0f, 1.0f, 1.0f, 1.0f,
                    0.0f, 0.0f);

            NativeImage image = new NativeImage(NativeImage.Format.RGBA, CANVAS_W, CANVAS_H, false);
            // Délier avant de relire : lire une texture encore attachée au framebuffer lié est
            // un comportement indéfini côté GL.
            target.unbindWrite();
            RenderSystem.bindTexture(target.getColorTextureId());
            image.downloadTexture(0, false);
            image.flipY(); // OpenGL rend les lignes de bas en haut

            zeroPadding(image);
            return new Result(Status.OK, image);

        } catch (Throwable t) {
            LOGGER.warn("Échec du rendu de l'artwork de carte pour {} : {}", speciesId, t.toString());
            return Result.FAILED;
        } finally {
            // drawProfilePokemon laisse fuir de l'état global ; on restaure dans l'ordre inverse.
            dispatcher.overrideCameraOrientation(previousCamera);
            Lighting.setupFor3DItems();
            modelView.popMatrix();
            RenderSystem.applyModelViewMatrix();
            RenderSystem.restoreProjectionMatrix();
            mc.getMainRenderTarget().bindWrite(true);
            RenderSystem.defaultBlendFunc();
            RenderSystem.enableDepthTest();
            RenderSystem.depthMask(true);
        }
    }

    /**
     * Force le padding à transparent. Un modèle très haut peut déborder du contenu utile, et le
     * crop UV de la carte ne couvre pas cette zone : sans ce nettoyage, le débordement resterait
     * visible sur les bords.
     */
    private static void zeroPadding(NativeImage image) {
        for (int y = 0; y < CANVAS_H; y++) {
            int from = (y < CONTENT_H) ? CONTENT_W : 0;
            for (int x = from; x < CANVAS_W; x++) {
                image.setPixelRGBA(x, y, 0);
            }
        }
    }
}
