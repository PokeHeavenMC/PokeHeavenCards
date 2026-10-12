package com.howlite.cobblemoncards.neoforge.client;

import com.howlite.cobblemoncards.client.CobblemonCardsClient;
import com.howlite.cobblemoncards.component.ModDataComponents;
import com.howlite.cobblemoncards.network.OpenBinderPayload;
import com.howlite.cobblemoncards.render.CardTextureResolver;
import com.howlite.cobblemoncards.render.ModShaders;
import com.howlite.cobblemoncards.render.PokemonProfileTextures;
import com.howlite.cobblemoncards.render.ProceduralTextureCache;
import com.howlite.cobblemoncards.screen.AdvancedHoloProjectorScreen;
import com.howlite.cobblemoncards.screen.BinderScreen;
import com.howlite.cobblemoncards.screen.CardCabinetScreen;
import com.howlite.cobblemoncards.screen.CardRecyclerScreen;
import com.howlite.cobblemoncards.screen.CardRestorerScreen;
import com.howlite.cobblemoncards.menu.ModMenuTypes;
import com.mojang.blaze3d.platform.InputConstants;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManagerReloadListener;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.client.event.RegisterClientReloadListenersEvent;
import net.neoforged.neoforge.client.event.RegisterMenuScreensEvent;
import net.neoforged.neoforge.client.event.RegisterShadersEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import net.neoforged.neoforge.common.NeoForge;
import org.lwjgl.glfw.GLFW;

import java.io.IOException;

@SuppressWarnings("null")
public class NeoForgeCobblemonCardsClient {
    private static KeyMapping openBinderKey;
    private static KeyMapping openShowcaseKey;

    public static void registerClient(IEventBus modEventBus) {
        // Enregistrer les événements sur le bus du jeu pour les ticks client
        NeoForge.EVENT_BUS.addListener(NeoForgeCobblemonCardsClient::onClientTick);

        // Enregistrer les événements sur le bus du mod
        modEventBus.addListener(NeoForgeCobblemonCardsClient::clientSetup);
        modEventBus.addListener(NeoForgeCobblemonCardsClient::registerScreens);
        modEventBus.addListener(NeoForgeCobblemonCardsClient::registerShaders);
        modEventBus.addListener(NeoForgeCobblemonCardsClient::registerKeyMappings);
        modEventBus.addListener(NeoForgeCobblemonCardsClient::registerReloadListeners);

        // World render hook: draw shown cards in the world after entities are rendered
        NeoForge.EVENT_BUS.addListener(NeoForgeCobblemonCardsClient::onRenderLevelStage);
    }

    /**
     * Invalidation des caches de textures au rechargement des ressources. Cet écouteur ne fait
     * qu'invalider : il peut tourner AVANT le rechargement du dépôt de modèles de Cobblemon, donc
     * on ne cuit jamais ici.
     */
    public static void registerReloadListeners(RegisterClientReloadListenersEvent event) {
        event.registerReloadListener((ResourceManagerReloadListener) manager -> {
            CardTextureResolver.clear();
            PokemonProfileTextures.clear();
            ProceduralTextureCache.clear();
        });
    }

    public static void clientSetup(FMLClientSetupEvent event) {
        event.enqueueWork(() -> {
            CobblemonCardsClient.init();
        });
    }

    public static void registerScreens(RegisterMenuScreensEvent event) {
        event.register(ModMenuTypes.BINDER_MENU, BinderScreen::new);
        event.register(ModMenuTypes.CARD_RECYCLER_MENU, CardRecyclerScreen::new);
        event.register(ModMenuTypes.CARD_RESTORER_MENU, CardRestorerScreen::new);
        event.register(ModMenuTypes.ADVANCED_HOLO_PROJECTOR_MENU, AdvancedHoloProjectorScreen::new);
        event.register(ModMenuTypes.CARD_CABINET_MENU, CardCabinetScreen::new);
    }

    public static void registerShaders(RegisterShadersEvent event) {
        try {
            event.registerShader(
                    new net.minecraft.client.renderer.ShaderInstance(
                            event.getResourceProvider(),
                            ResourceLocation.fromNamespaceAndPath("cobblemon-cards", "card_procedural_bg"),
                            DefaultVertexFormat.NEW_ENTITY
                    ),
                    shader -> ModShaders.bgShader = shader
            );
            event.registerShader(
                    new net.minecraft.client.renderer.ShaderInstance(
                            event.getResourceProvider(),
                            ResourceLocation.fromNamespaceAndPath("cobblemon-cards", "card_procedural_holo"),
                            DefaultVertexFormat.NEW_ENTITY
                    ),
                    shader -> ModShaders.holoShader = shader
            );
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public static void registerKeyMappings(RegisterKeyMappingsEvent event) {
        openBinderKey = new KeyMapping(
                "key.cobblemon-cards.open_binder",
                InputConstants.Type.KEYSYM,
                GLFW.GLFW_KEY_B,
                "category.cobblemon-cards"
        );
        openShowcaseKey = new KeyMapping(
                "key.cobblemon-cards.open_showcase",
                InputConstants.Type.KEYSYM,
                GLFW.GLFW_KEY_V,
                "category.cobblemon-cards"
        );
        event.register(openBinderKey);
        event.register(openShowcaseKey);
    }

    public static void onClientTick(ClientTickEvent.Post event) {
        // Cuisson des artworks manquants : au tick, donc hors de toute passe de dessin.
        // Placé avant la garde sur les touches, qui n'a rien à voir.
        PokemonProfileTextures.processQueue();

        if (openBinderKey == null || openShowcaseKey == null) return;
        
        while (openBinderKey.consumeClick()) {
            com.howlite.cobblemoncards.util.PlatformHelper.INSTANCE.sendToServer(new OpenBinderPayload());
        }

        while (openShowcaseKey.consumeClick()) {
            Minecraft mc = Minecraft.getInstance();
            if (mc.player != null && mc.level != null) {
                java.util.List<net.minecraft.world.item.ItemStack> showcaseCards = new java.util.ArrayList<>();
                for (net.minecraft.world.item.ItemStack stack : mc.player.getInventory().items) {
                    if (stack.has(ModDataComponents.CARD_DATA)) {
                        showcaseCards.add(stack.copy());
                        if (showcaseCards.size() >= 10) break;
                    }
                }
                if (!showcaseCards.isEmpty()) {
                    mc.setScreen(new com.howlite.cobblemoncards.screen.CardShowcaseScreen(showcaseCards));
                }
            }
        }
    }

    public static void onRenderLevelStage(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_ENTITIES) return;
        net.minecraft.client.Minecraft mc = net.minecraft.client.Minecraft.getInstance();
        if (mc.level == null) return;
        com.howlite.cobblemoncards.render.CardShowRenderer.renderAll(
                event.getPoseStack(),
                mc.renderBuffers().bufferSource(),
                event.getCamera(),
                mc.level
        );
    }
}
