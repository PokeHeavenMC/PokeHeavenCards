package com.howlite.cobblemoncards.render;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.howlite.cobblemoncards.util.PlatformHelper;
import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

/**
 * Persiste les artworks cuits par {@link PokemonProfileBaker} sous forme de PNG, pour qu'une
 * seconde session n'ait rien à recuire.
 *
 * Disposition :
 * <pre>
 * &lt;gameDir&gt;/cobblemon-cards/profile-cache/
 *     index.json
 *     &lt;namespace&gt;/&lt;species&gt;__&lt;variante&gt;.png
 * </pre>
 *
 * INVALIDATION : chaque entrée de l'index porte une empreinte. Si l'empreinte recalculée diffère,
 * le PNG est ignoré et l'espèce est recuite. L'empreinte inclut la version du mod propriétaire du
 * namespace, si bien que la mise à jour d'un addon ne recuit que ses espèces à lui et laisse les
 * autres namespaces intacts.
 */
final class PokemonProfileDiskCache {

    private static final Logger LOGGER = LoggerFactory.getLogger("cobblemon-cards");
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    /** À incrémenter dès que le cadrage ou la géométrie du bake change. */
    private static final int BAKE_FORMAT_VERSION = 1;

    private static final String INDEX_FILE = "index.json";

    /** clé logique -> empreinte enregistrée */
    private static final Map<String, String> INDEX = new TreeMap<>();

    private static Path root;
    private static boolean loaded;
    /** Passe à true si l'écriture échoue (répertoire en lecture seule) : le cache mémoire suffit. */
    private static boolean writeDisabled;
    private static String packFingerprint = "";

    private PokemonProfileDiskCache() {}

    // ------------------------------------------------------------------ API

    /** Relit l'index et purge les entrées dont le mod n'est plus chargé. */
    static void init() {
        loaded = false;
        INDEX.clear();
        writeDisabled = false;
        refreshPackFingerprint();
        loadIndex();
        prune();
    }

    /** Invalide l'empreinte des resource packs ; à appeler au rechargement des ressources. */
    static void refreshPackFingerprint() {
        try {
            List<String> ids = new ArrayList<>(Minecraft.getInstance().getResourcePackRepository().getSelectedIds());
            packFingerprint = Integer.toHexString(String.join("\u0000", ids).hashCode());
        } catch (Throwable t) {
            packFingerprint = "none";
        }
    }

    /** @return l'image stockée pour cette clé, ou {@code null} en cas d'absence ou d'empreinte périmée. */
    static NativeImage load(String key, ResourceLocation speciesId, Set<String> aspects) {
        loadIndex();
        String stored = INDEX.get(key);
        if (stored == null || !stored.equals(fingerprint(speciesId))) {
            return null;
        }
        Path file = pngPath(speciesId, aspects);
        if (file == null || !Files.isRegularFile(file)) {
            return null;
        }
        try (InputStream in = Files.newInputStream(file)) {
            NativeImage image = NativeImage.read(in);
            if (image.getWidth() != PokemonProfileBaker.CANVAS_W || image.getHeight() != PokemonProfileBaker.CANVAS_H) {
                image.close();
                return null;
            }
            return image;
        } catch (Throwable t) {
            return null;
        }
    }

    /** Écrit l'image et met l'index à jour. Un échec est non fatal : le cache mémoire reste valable. */
    static void store(String key, ResourceLocation speciesId, Set<String> aspects, NativeImage image) {
        if (writeDisabled) {
            return;
        }
        Path file = pngPath(speciesId, aspects);
        if (file == null) {
            return;
        }
        try {
            Files.createDirectories(file.getParent());
            image.writeToFile(file);
            INDEX.put(key, fingerprint(speciesId));
            saveIndex();
        } catch (Throwable t) {
            writeDisabled = true;
            LOGGER.warn("Cache disque des artworks désactivé (écriture impossible dans {}) : {}", file, t.toString());
        }
    }

    // -------------------------------------------------------------- interne

    private static Path root() {
        if (root == null) {
            root = Minecraft.getInstance().gameDirectory.toPath()
                    .resolve("cobblemon-cards").resolve("profile-cache");
        }
        return root;
    }

    private static Path pngPath(ResourceLocation speciesId, Set<String> aspects) {
        String variant = variantSuffix(aspects);
        String safeSpecies = speciesId.getPath().replaceAll("[^a-z0-9._-]", "_");
        String safeNamespace = speciesId.getNamespace().replaceAll("[^a-z0-9._-]", "_");
        return root().resolve(safeNamespace).resolve(safeSpecies + "__" + variant + ".png");
    }

    /** "r" (normal), "s" (shiny), ou une forme hachée quand d'autres aspects entrent en jeu. */
    private static String variantSuffix(Set<String> aspects) {
        boolean shiny = aspects.contains("shiny");
        if (aspects.isEmpty()) {
            return "r";
        }
        if (aspects.size() == 1 && shiny) {
            return "s";
        }
        // aspects arrive triés (TreeSet), le hachage est donc stable d'une session à l'autre
        String joined = String.join(",", aspects);
        return (shiny ? "s-" : "r-") + String.format("%08x", joined.hashCode());
    }

    private static String fingerprint(ResourceLocation speciesId) {
        String namespace = speciesId.getNamespace();
        String modVersion = PlatformHelper.INSTANCE.getModVersion(namespace);
        String cobblemonVersion = PlatformHelper.INSTANCE.getModVersion("cobblemon");
        return BAKE_FORMAT_VERSION
                + ":" + PokemonProfileBaker.CANVAS_W + "x" + PokemonProfileBaker.CANVAS_H
                + ":" + namespace + "@" + modVersion
                + ":" + cobblemonVersion
                + ":pk_" + packFingerprint;
    }

    private static void loadIndex() {
        if (loaded) {
            return;
        }
        loaded = true;
        Path index = root().resolve(INDEX_FILE);
        if (!Files.isRegularFile(index)) {
            return;
        }
        try {
            JsonObject json = GSON.fromJson(Files.readString(index), JsonObject.class);
            if (json == null || !json.has("entries")) {
                return;
            }
            JsonObject entries = json.getAsJsonObject("entries");
            for (String key : entries.keySet()) {
                INDEX.put(key, entries.get(key).getAsString());
            }
        } catch (Throwable t) {
            LOGGER.warn("Index du cache d'artworks illisible, il sera reconstruit : {}", t.toString());
            INDEX.clear();
        }
    }

    private static void saveIndex() throws IOException {
        JsonObject entries = new JsonObject();
        INDEX.forEach(entries::addProperty);
        JsonObject json = new JsonObject();
        json.addProperty("format", BAKE_FORMAT_VERSION);
        json.add("entries", entries);
        Files.createDirectories(root());
        Files.writeString(root().resolve(INDEX_FILE), GSON.toJson(json));
    }

    /** Supprime les PNG des namespaces dont le mod n'est plus installé. */
    private static void prune() {
        List<String> stale = new ArrayList<>();
        for (Map.Entry<String, String> entry : INDEX.entrySet()) {
            String namespace = entry.getKey().contains(":")
                    ? entry.getKey().substring(0, entry.getKey().indexOf(':'))
                    : null;
            if (namespace == null) {
                stale.add(entry.getKey());
                continue;
            }
            // "minecraft" n'est pas un mod chargé au sens du loader, mais n'est jamais un namespace
            // d'espèce non plus : tout ce qui renvoie "absent" est obsolète.
            if ("absent".equals(PlatformHelper.INSTANCE.getModVersion(namespace))) {
                stale.add(entry.getKey());
            }
        }
        if (stale.isEmpty()) {
            return;
        }
        for (String key : stale) {
            INDEX.remove(key);
        }
        try {
            // On supprime les dossiers de namespace devenus inutiles
            if (Files.isDirectory(root())) {
                try (var dirs = Files.list(root())) {
                    for (Path dir : dirs.filter(Files::isDirectory).toList()) {
                        String namespace = dir.getFileName().toString();
                        if ("absent".equals(PlatformHelper.INSTANCE.getModVersion(namespace))) {
                            deleteRecursively(dir);
                        }
                    }
                }
            }
            saveIndex();
        } catch (Throwable t) {
            LOGGER.warn("Purge du cache d'artworks incomplète : {}", t.toString());
        }
    }

    private static void deleteRecursively(Path dir) throws IOException {
        try (var paths = Files.walk(dir)) {
            for (Path path : paths.sorted(java.util.Comparator.reverseOrder()).toList()) {
                Files.deleteIfExists(path);
            }
        }
    }
}
