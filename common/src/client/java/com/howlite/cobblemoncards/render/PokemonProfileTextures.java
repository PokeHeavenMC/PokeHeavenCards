package com.howlite.cobblemoncards.render;

import com.cobblemon.mod.common.pokemon.Species;
import com.howlite.cobblemoncards.CobblemonCardsConfig;
import com.howlite.cobblemoncards.util.CardUtil;
import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.ResourceLocation;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

/**
 * Fournit un faux sprite 2D pour les espèces qui n'en ont aucun, en cuisant leur modèle 3D
 * Cobblemon (voir {@link PokemonProfileBaker}).
 *
 * {@link #get} est appelé depuis le rendu, à chaque frame et pour chaque carte visible : il ne doit
 * jamais bloquer ni cuire quoi que ce soit. Il se contente de consulter le cache et, au premier
 * défaut, d'inscrire l'espèce dans une file traitée par {@link #processQueue()} au tick client.
 */
public final class PokemonProfileTextures {

    private enum State { PENDING, READY, DEAD }

    private static final class Entry {
        State state = State.PENDING;
        DynamicTexture texture;
        ResourceLocation location;
        ResourceLocation speciesId;
        Set<String> aspects;
        int failures;
        long retryAfterTick;
    }

    /** Au-delà, l'espèce est abandonnée pour la session : voir {@link State#DEAD}. */
    private static final int MAX_FAILURES = 3;
    /** Délai avant de réessayer une espèce dont les modèles n'étaient pas encore chargés. */
    private static final long NOT_READY_RETRY_TICKS = 40;
    /** Le rendu hors écran plus la relecture des pixels coûtent cher : un seul par tick. */
    private static final int BAKES_PER_TICK = 1;
    /** Relire un PNG de 192x128 est négligeable, on peut en enchaîner plusieurs. */
    private static final int DISK_LOADS_PER_TICK = 8;

    /** Accès ordonné : la tête est l'entrée la moins récemment utilisée. */
    private static final LinkedHashMap<String, Entry> CACHE = new LinkedHashMap<>(64, 0.75f, true);
    private static final Deque<String> QUEUE = new ArrayDeque<>();
    /** Mémorise la dérivation clé <- identifiant de carte, qui passe par une recherche d'espèce. */
    private static final Map<String, String> KEY_CACHE = new HashMap<>();

    private static long tickCounter;
    private static boolean diskInitialized;

    private PokemonProfileTextures() {}

    /**
     * @param cardPokemonId identifiant de l'espèce porté par la carte, préfixe "silhouette_" déjà retiré
     * @return la texture cuite si elle est prête, sinon {@code null} — l'appelant retombe alors sur
     *         la poupée Substitut
     */
    public static ResourceLocation get(String cardPokemonId, boolean shiny) {
        if (!CobblemonCardsConfig.enableFakemonCardArt) {
            return null;
        }

        String key = keyFor(cardPokemonId, shiny);
        if (key == null) {
            return null;
        }

        Entry entry = CACHE.get(key);
        if (entry == null) {
            entry = new Entry();
            if (!fillSpecies(entry, cardPokemonId, shiny)) {
                return null;
            }
            CACHE.put(key, entry);
            QUEUE.add(key);
            return null;
        }

        return entry.state == State.READY ? entry.location : null;
    }

    /** Traite la file de cuisson. À appeler une fois par tick client, jamais pendant une frame. */
    public static void processQueue() {
        if (QUEUE.isEmpty()) {
            return;
        }
        tickCounter++;

        if (!diskInitialized) {
            diskInitialized = true;
            PokemonProfileDiskCache.init();
        }

        int bakes = 0;
        int diskLoads = 0;
        int inspected = 0;
        int queued = QUEUE.size();

        while (!QUEUE.isEmpty() && inspected < queued && bakes < BAKES_PER_TICK) {
            inspected++;
            String key = QUEUE.poll();
            Entry entry = CACHE.get(key);
            if (entry == null || entry.state != State.PENDING) {
                continue;
            }
            if (tickCounter < entry.retryAfterTick) {
                QUEUE.add(key); // pas encore l'heure de réessayer
                continue;
            }

            // 1. Le disque d'abord : c'est gratuit comparé à un rendu.
            if (diskLoads < DISK_LOADS_PER_TICK) {
                diskLoads++;
                NativeImage stored = PokemonProfileDiskCache.load(key, entry.speciesId, entry.aspects);
                if (stored != null) {
                    register(key, entry, stored);
                    continue;
                }
            }

            // 2. Sinon on cuit.
            PokemonProfileBaker.Result result = PokemonProfileBaker.bake(entry.speciesId, entry.aspects);
            bakes++;
            switch (result.status()) {
                case OK -> {
                    PokemonProfileDiskCache.store(key, entry.speciesId, entry.aspects, result.image());
                    register(key, entry, result.image());
                }
                case NOT_READY -> {
                    // Les modèles Cobblemon ne sont pas encore chargés : on réessaiera, et ça ne
                    // compte pas comme un échec.
                    entry.retryAfterTick = tickCounter + NOT_READY_RETRY_TICKS;
                    QUEUE.add(key);
                }
                case FAILED -> {
                    entry.failures++;
                    if (entry.failures >= MAX_FAILURES) {
                        entry.state = State.DEAD;
                    } else {
                        entry.retryAfterTick = tickCounter + NOT_READY_RETRY_TICKS;
                        QUEUE.add(key);
                    }
                }
            }
        }

        evictIfNeeded();
    }

    /** Libère toutes les textures et vide les caches. À appeler au rechargement des ressources. */
    public static void clear() {
        for (Entry entry : CACHE.values()) {
            release(entry);
        }
        CACHE.clear();
        QUEUE.clear();
        KEY_CACHE.clear();
        PokemonProfileBaker.releaseTarget();
        PokemonProfileDiskCache.refreshPackFingerprint();
        diskInitialized = false;
    }

    // -------------------------------------------------------------- interne

    private static void register(String key, Entry entry, NativeImage image) {
        DynamicTexture texture = new DynamicTexture(image);
        ResourceLocation location = ResourceLocation.fromNamespaceAndPath(
                "cobblemon-cards", "dynamic/profile/" + sanitize(key));
        Minecraft.getInstance().getTextureManager().register(location, texture);
        entry.texture = texture;
        entry.location = location;
        entry.state = State.READY;
    }

    private static void release(Entry entry) {
        if (entry.location != null) {
            Minecraft.getInstance().getTextureManager().release(entry.location);
        }
        if (entry.texture != null) {
            entry.texture.close();
        }
        entry.texture = null;
        entry.location = null;
    }

    /** Applique le plafond mémoire en libérant les entrées les moins récemment lues. */
    private static void evictIfNeeded() {
        int max = Math.max(16, CobblemonCardsConfig.fakemonCardArtCacheSize);
        if (CACHE.size() <= max) {
            return;
        }
        Iterator<Map.Entry<String, Entry>> it = CACHE.entrySet().iterator();
        while (it.hasNext() && CACHE.size() > max) {
            Map.Entry<String, Entry> oldest = it.next();
            if (oldest.getValue().state != State.READY) {
                continue; // une entrée encore en file n'a rien à libérer
            }
            release(oldest.getValue());
            it.remove();
        }
    }

    /**
     * Construit la clé de cache : identifiant de ressource de l'espèce + aspects triés. C'est bien
     * l'identifiant Cobblemon qui compte, pas la chaîne brute de la carte, parce que plusieurs
     * orthographes mènent à la même espèce.
     *
     * @return {@code null} si l'espèce est introuvable côté Cobblemon
     */
    private static String keyFor(String cardPokemonId, boolean shiny) {
        String memoKey = cardPokemonId.toLowerCase() + (shiny ? "|s" : "|r");
        if (KEY_CACHE.containsKey(memoKey)) {
            return KEY_CACHE.get(memoKey);
        }
        String key = computeKey(cardPokemonId, shiny);
        KEY_CACHE.put(memoKey, key);
        return key;
    }

    private static String computeKey(String cardPokemonId, boolean shiny) {
        Species species = resolveSpecies(cardPokemonId);
        if (species == null || species.getResourceIdentifier() == null) {
            return null;
        }
        return species.getResourceIdentifier() + "|" + String.join(",", aspectsFor(cardPokemonId, shiny));
    }

    private static boolean fillSpecies(Entry entry, String cardPokemonId, boolean shiny) {
        Species species = resolveSpecies(cardPokemonId);
        if (species == null || species.getResourceIdentifier() == null) {
            return false;
        }
        entry.speciesId = species.getResourceIdentifier();
        entry.aspects = aspectsFor(cardPokemonId, shiny);
        return true;
    }

    private static Species resolveSpecies(String cardPokemonId) {
        return CardUtil.getSpecies(CardTextureResolver.getBaseSpeciesName(cardPokemonId));
    }

    /** Les aspects font partie de la clé : c'est par eux que Cobblemon choisit la forme et le modèle. */
    private static Set<String> aspectsFor(String cardPokemonId, boolean shiny) {
        Set<String> aspects = new TreeSet<>();
        if (shiny) {
            aspects.add("shiny");
        }
        String lower = cardPokemonId.toLowerCase().replace('-', '_');
        for (Map.Entry<String, String> mapping : FORM_ASPECTS.entrySet()) {
            if (lower.endsWith(mapping.getKey())) {
                aspects.add(mapping.getValue());
                break;
            }
        }
        return aspects;
    }

    /**
     * Suffixes de forme les plus courants. Un aspect inconnu d'une espèce est simplement ignoré par
     * Cobblemon, qui retombe sur la forme de base — inutile donc d'être exhaustif ici.
     */
    private static final Map<String, String> FORM_ASPECTS = new LinkedHashMap<>();
    static {
        FORM_ASPECTS.put("_mega_x", "mega-x");
        FORM_ASPECTS.put("_mega_y", "mega-y");
        FORM_ASPECTS.put("_mega", "mega");
        FORM_ASPECTS.put("_alolan", "alolan");
        FORM_ASPECTS.put("_galarian", "galarian");
        FORM_ASPECTS.put("_hisuian", "hisuian");
        FORM_ASPECTS.put("_paldean_combat", "paldean-combat");
        FORM_ASPECTS.put("_paldean_blaze", "paldean-blaze");
        FORM_ASPECTS.put("_paldean_aqua", "paldean-aqua");
        FORM_ASPECTS.put("_paldean", "paldean");
        FORM_ASPECTS.put("_gigantamax", "gmax");
        FORM_ASPECTS.put("_gmax", "gmax");
    }

    private static String sanitize(String key) {
        return key.toLowerCase().replace(':', '/').replaceAll("[^a-z0-9/._-]", "_");
    }
}
