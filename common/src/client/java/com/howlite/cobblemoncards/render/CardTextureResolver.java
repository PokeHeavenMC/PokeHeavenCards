package com.howlite.cobblemoncards.render;

import com.cobblemon.mod.common.pokemon.Species;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;

import java.util.HashMap;
import java.util.Map;

/**
 * Résout la texture d'artwork d'une carte à partir de son identifiant d'espèce, et mémorise
 * le résultat.
 *
 * POURQUOI CE CACHE :
 * La chaîne de résolution sonde le ResourceManager jusqu'à ~40 fois (la boucle de suffixes de
 * {@link #resolveEntityIcon}). Elle tournait auparavant en ligne dans
 * {@link CardItemRenderer#render}, donc à chaque frame et pour chaque carte visible — un classeur
 * ouvert représentait des milliers de lookups par frame.
 *
 * Le cache est vidé au rechargement des ressources (F3+T, changement de resource pack), puisque
 * c'est le seul moment où la présence d'un fichier peut changer.
 *
 * CONTRAT : on ne mémorise QUE les textures réellement présentes. Quand rien n'est trouvé,
 * {@link #resolve} renvoie {@code null} et laisse l'appelant décider du repli (rendu du modèle 3D
 * via {@link PokemonProfileTextures}, sinon la poupée Substitut). Ce repli dépend d'un état qui
 * évolue en cours de session, il n'a donc rien à faire dans le cache.
 */
public final class CardTextureResolver {

    private static final String BASE = "textures/item/cards/pokemon/";

    /** Clé : identifiant d'espèce en minuscules + "|s" (shiny) ou "|r" (normal). */
    private static final Map<String, ResourceLocation> CACHE = new HashMap<>();

    private CardTextureResolver() {}

    /**
     * @param resolvedPokemonId identifiant déjà débarrassé du préfixe "silhouette_" ; ne doit
     *                          jamais être un identifiant "player_"
     * @return la texture trouvée, ou {@code null} si aucune n'existe pour cette espèce
     */
    public static ResourceLocation resolve(String resolvedPokemonId, boolean shiny) {
        String key = resolvedPokemonId.toLowerCase() + (shiny ? "|s" : "|r");
        if (CACHE.containsKey(key)) {
            return CACHE.get(key);
        }
        ResourceLocation resolved = resolveUncached(resolvedPokemonId, shiny);
        CACHE.put(key, resolved);
        return resolved;
    }

    /** Vide le cache. À appeler au rechargement des ressources. */
    public static void clear() {
        CACHE.clear();
    }

    private static ResourceLocation resolveUncached(String resolvedPokemonId, boolean shiny) {
        ResourceLocation easterEgg = resolveEasterEgg(resolvedPokemonId);
        if (easterEgg != null) {
            return exists(easterEgg) ? easterEgg : null;
        }

        // Niveau 1 : dossiers regular/ et shiny/, réservés aux resource packs et aux addons.
        // Ils sont vides dans les assets du mod : c'est le point d'entrée documenté pour fournir
        // un sprite de carte dessiné à la main, qui doit primer sur tout le reste.
        ResourceLocation packTex = resolveResourcePackSprite(resolvedPokemonId, shiny);
        if (packTex != null) {
            return packTex;
        }

        // Niveau 2 : les 1025 dossiers entity_icon/ fournis par le mod.
        return resolveEntityIcon(resolvedPokemonId, shiny);
    }

    private static ResourceLocation resolveEasterEgg(String id) {
        String file = switch (id.toLowerCase()) {
            case "missingno", "you_and_mew", "ghost", "god_bidoof",
                 "crystal_onix", "shadow_lugia", "pride_sylveon" -> id.toLowerCase();
            default -> null;
        };
        return file == null ? null : tex("easter_egg/" + file + ".png");
    }

    private static ResourceLocation resolveResourcePackSprite(String id, boolean shiny) {
        String folder = shiny ? "shiny" : "regular";
        String sanitizedName = sanitizeStandardPath(id);

        ResourceLocation primaryTex = tex(folder + "/" + sanitizedName + ".png");
        if (exists(primaryTex)) {
            return primaryTex;
        }

        // Les Méga et les formes régionales sont nommées avec des tirets dans ces dossiers
        ResourceLocation dashedTex = tex(folder + "/" + sanitizedName.replace("_", "-") + ".png");
        return exists(dashedTex) ? dashedTex : null;
    }

    private static ResourceLocation resolveEntityIcon(String resolvedPokemonId, boolean shiny) {
        String baseSpecies = getBaseSpeciesName(resolvedPokemonId);
        Species species = com.howlite.cobblemoncards.util.CardUtil.getSpecies(baseSpecies);
        if (species == null) {
            return null;
        }

        String cleanName = sanitizeEntityIconPath(species.getName());
        String dexString = String.format("%04d", species.getNationalPokedexNumber());
        String folder = dexString + "_" + cleanName + "/";

        // A. La texture exacte de la variante (ex: vulpix_alolan.png)
        String vName = resolvedPokemonId.toLowerCase();
        String shinySuffix = shiny ? "_shiny" : "";
        String[] variantFiles = {
            vName + shinySuffix,
            vName.replace("-", "_") + shinySuffix,
            vName.replace("_", "-") + shinySuffix
        };

        for (String vf : variantFiles) {
            ResourceLocation vTex = tex("entity_icon/" + folder + sanitizeStandardPath(vf) + ".png");
            if (exists(vTex)) {
                return vTex;
            }
        }

        // B. Le fichier standard avec le nom remappé
        String remappedName = getRemappedEntityName(cleanName);
        ResourceLocation standardTex = tex("entity_icon/" + folder + remappedName + (shiny ? "_shiny.png" : ".png"));
        if (exists(standardTex)) {
            return standardTex;
        }

        // C. Les suffixes courants (genres, formes, segments, numéros de sprite)
        for (String suffix : ENTITY_ICON_SUFFIXES) {
            if (shiny) {
                // Premier placement : nom + suffixe + "_shiny.png" (ex: gimmighoul_chest_shiny.png)
                ResourceLocation t1 = tex("entity_icon/" + folder + remappedName + suffix + "_shiny.png");
                if (exists(t1)) {
                    return t1;
                }
                // Second placement : nom + "_shiny" + suffixe + ".png" (ex: ceruledge_shiny_1.png)
                ResourceLocation t2 = tex("entity_icon/" + folder + remappedName + "_shiny" + suffix + ".png");
                if (exists(t2)) {
                    return t2;
                }
            } else {
                ResourceLocation t = tex("entity_icon/" + folder + remappedName + suffix + ".png");
                if (exists(t)) {
                    return t;
                }
            }
        }

        return null;
    }

    private static final String[] ENTITY_ICON_SUFFIXES = {
        "_1", "_male", "_m", "_female", "_f", "_chest", "_roaming", "_zero", "_hero",
        "_solo", "_school", "_amped", "_lowkey", "_curly", "_droopy", "_stretchy",
        "_two_segment", "_three_segment", "_green", "_blue", "_yellow", "_gray",
        "_spring", "_summer", "_autumn", "_winter", "_west", "_east",
        "_redstripe", "_bluestripe", "_whitestripe", "_a", "_shadow",
        "_incarnate", "_therian", "_vanilla"
    };

    private static ResourceLocation tex(String path) {
        return ResourceLocation.fromNamespaceAndPath("cobblemon-cards", BASE + path);
    }

    private static boolean exists(ResourceLocation loc) {
        return Minecraft.getInstance().getResourceManager().getResource(loc).isPresent();
    }

    /**
     * Réduit un identifiant de variante à l'espèce de base, pour retrouver le dossier entity_icon.
     * Public parce que {@link PokemonProfileTextures} en a besoin pour résoudre la Species.
     */
    public static String getBaseSpeciesName(String name) {
        String lower = name.toLowerCase();
        if (lower.startsWith("eternamax_")) return "eternatus";
        if (lower.endsWith("_rapidstrike_gmax")) return "urshifu";
        if (lower.endsWith("_alolan")) return lower.substring(0, lower.length() - "_alolan".length());
        if (lower.endsWith("_galarian")) return lower.substring(0, lower.length() - "_galarian".length());
        if (lower.endsWith("_hisuian")) return lower.substring(0, lower.length() - "_hisuian".length());
        if (lower.endsWith("_paldean_combat")) return "tauros";
        if (lower.endsWith("_paldean_blaze")) return "tauros";
        if (lower.endsWith("_paldean_aqua")) return "tauros";
        if (lower.endsWith("_paldean")) return lower.substring(0, lower.length() - "_paldean".length());
        if (lower.endsWith("_mega_x")) return lower.substring(0, lower.length() - "_mega_x".length());
        if (lower.endsWith("_mega_y")) return lower.substring(0, lower.length() - "_mega_y".length());
        if (lower.endsWith("_mega")) return lower.substring(0, lower.length() - "_mega".length());
        if (lower.endsWith("_gmax")) return lower.substring(0, lower.length() - "_gmax".length());
        if (lower.endsWith("_gigantamax")) return lower.substring(0, lower.length() - "_gigantamax".length());

        if (lower.endsWith("-alolan")) return lower.substring(0, lower.length() - "-alolan".length());
        if (lower.endsWith("-galarian")) return lower.substring(0, lower.length() - "-galarian".length());
        if (lower.endsWith("-hisuian")) return lower.substring(0, lower.length() - "-hisuian".length());
        if (lower.endsWith("-paldean-combat")) return "tauros";
        if (lower.endsWith("-paldean-blaze")) return "tauros";
        if (lower.endsWith("-paldean-aqua")) return "tauros";
        if (lower.endsWith("-paldean")) return lower.substring(0, lower.length() - "-paldean".length());
        if (lower.endsWith("-mega-x")) return lower.substring(0, lower.length() - "-mega-x".length());
        if (lower.endsWith("-mega-y")) return lower.substring(0, lower.length() - "-mega-y".length());
        if (lower.endsWith("-mega")) return lower.substring(0, lower.length() - "-mega".length());
        if (lower.endsWith("-gmax")) return lower.substring(0, lower.length() - "-gmax".length());
        if (lower.endsWith("-gigantamax")) return lower.substring(0, lower.length() - "-gigantamax".length());
        return lower;
    }

    private static String sanitizeStandardPath(String id) {
        if (id == null) return "missing";
        return id.toLowerCase()
                 .replace(".", "")
                 .replace(" ", "-")
                 .replaceAll("[^a-z0-9/._-]", "");
    }

    private static String sanitizeEntityIconPath(String id) {
        if (id == null) return "missing";
        return id.toLowerCase()
                 .replace(".", "")
                 .replace(" ", "")
                 .replace("-", "")
                 .replaceAll("[^a-z0-9/._-]", "");
    }

    private static String getRemappedEntityName(String cleanName) {
        return switch (cleanName) {
            case "mrmime" -> "mr_mime";
            case "mimejr" -> "mime_jr";
            case "mrrime" -> "mr_rime";
            case "hooh" -> "ho_oh";
            case "porygonz" -> "porygon-z";
            case "jangmoo" -> "jangmo-o";
            case "hakamoo" -> "hakamo-o";
            case "kommoo" -> "kommo-o";
            default -> cleanName;
        };
    }
}
