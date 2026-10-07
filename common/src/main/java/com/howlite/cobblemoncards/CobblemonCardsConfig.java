package com.howlite.cobblemoncards;

import com.howlite.cobblemoncards.component.CardStat;
import eu.midnightdust.lib.config.MidnightConfig;

public class CobblemonCardsConfig extends MidnightConfig {
    @Entry
    public static boolean enableCardStats = true;

    @Entry
    public static boolean enablePlayerStats = true;

    @Entry
    public static boolean enableSpawnBoostStats = true;

    @Entry(min = 0.0f, max = 100.0f)
    public static float globalStatMultiplier = 10.0f;

    @Entry(min = 0.0f, max = 100.0f)
    public static float playerStatMultiplier = 1.0f;

    @Entry(min = 0.0f, max = 100.0f)
    public static float spawnBoostStatMultiplier = 1.0f;

    /**
     * Hard ceiling on the spawn weight multiplier a binder can grant to an elemental type,
     * applied in {@link com.howlite.cobblemoncards.event.BinderSpawnModifier}. This is the
     * spawn-side counterpart of the per-stat caps below; 5.0 means a boosted type can be at
     * most 5x more likely to spawn.
     */
    @Entry(min = 0.0f, max = 10000.0f)
    public static float maxSpawnBoostMultiplier = 5.0f;

    @Entry(min = 1, max = 1200)
    public static int recyclerProcessTime = 40;

    @Entry(min = 0.0f, max = 100.0f)
    public static float godPackTicketChance = 1.0f;

    @Entry(min = 1, max = 12000)
    public static int gradingStationProcessTime = 100;
    
    @Entry(min = 0.0f, max = 100.0f)
    public static float cardDropChance = 1.0f;

    @Entry(min = 0, max = 64)
    public static int gradingStationDustCost = 5;

    @Entry(min = 1, max = 1000)
    public static int restorerBaseCost = 5;

    @Entry
    public static boolean enableBoosterChestSpawn = true;

    @Entry(min = 0.0f, max = 100.0f)
    public static float boosterChestSpawnChance = 2.0f;

    @Entry
    public static boolean displayPercentStatOnCards = true;

    // --- Binder Config Options ---

    @Entry
    public static boolean enableLeatherBinder = true;
    @Entry
    public static boolean enableIronBinder = true;
    @Entry
    public static boolean enableGoldBinder = true;
    @Entry
    public static boolean enableDiamondBinder = true;
    @Entry
    public static boolean enableNetheriteBinder = true;
    @Entry
    public static boolean enableMasterAlbum = true;

    @Entry(min = 1, max = 1000)
    public static int leatherBinderPages = 1;
    @Entry(min = 1, max = 1000)
    public static int ironBinderPages = 2;
    @Entry(min = 1, max = 1000)
    public static int goldBinderPages = 3;
    @Entry(min = 1, max = 1000)
    public static int diamondBinderPages = 6;
    @Entry(min = 1, max = 1000)
    public static int netheriteBinderPages = 10;
    @Entry(min = 1, max = 2000)
    public static int masterAlbumPages = 1000;

    /**
     * When false (default), Master Album tier binders grant no passive stats at all.
     * A Master Album holds 1000 pages x 12 slots = 12,000 cards, so letting it contribute
     * its full linear sum dwarfs every other binder tier. It stays a pure storage item.
     */
    @Entry
    public static boolean doesMasterBinderProvideStats = false;

    // --- Per-stat multipliers for the vanilla-attribute / player stats ---
    // Each of these is applied on top of globalStatMultiplier * playerStatMultiplier.

    @Entry(min = 0.0f, max = 100.0f)
    public static float miningSpeedStatMultiplier = 1.0f;

    @Entry(min = 0.0f, max = 100.0f)
    public static float movementSpeedStatMultiplier = 1.0f;

    @Entry(min = 0.0f, max = 100.0f)
    public static float attackDamageStatMultiplier = 1.0f;

    @Entry(min = 0.0f, max = 100.0f)
    public static float attackSpeedStatMultiplier = 1.0f;

    @Entry(min = 0.0f, max = 100.0f)
    public static float luckStatMultiplier = 0.1f;

    @Entry(min = 0.0f, max = 100.0f)
    public static float armorStatMultiplier = 1.0f;

    @Entry(min = 0.0f, max = 100.0f)
    public static float maxHealthStatMultiplier = 1.0f;

    @Entry(min = 0.0f, max = 100.0f)
    public static float cardDropChanceStatMultiplier = 1.0f;

    /**
     * When false (default), species whose National Pokédex number is outside [1, 1025]
     * (i.e. Fakemon added by addon mods) are excluded from card drops and booster packs.
     * Set to true to allow cards for any registered Cobblemon species.
     */
    @Entry
    public static boolean allowFakemonCards = false;


    // --- Stat caps ---
    // Applied to the EFFECTIVE value (after every multiplier), in display units: a flat
    // amount for FLAT stats (Max Health, Armor, Luck, Mining Speed) and a percentage for
    // PERCENT stats. Without these, a high-capacity binder sums its cards linearly and
    // without bound. Spawn stats are excluded: they are already bounded by
    // maxSpawnBoostMultiplier in BinderSpawnModifier.

    @Entry
    public static boolean enableStatCaps = true;

    @Entry(min = 0.0f, max = 10000.0f)
    public static float miningSpeedStatCap = 15.0f;

    @Entry(min = 0.0f, max = 10000.0f)
    public static float movementSpeedStatCap = 50.0f;

    @Entry(min = 0.0f, max = 10000.0f)
    public static float attackDamageStatCap = 50.0f;

    @Entry(min = 0.0f, max = 10000.0f)
    public static float attackSpeedStatCap = 50.0f;

    @Entry(min = 0.0f, max = 10000.0f)
    public static float luckStatCap = 10.0f;

    @Entry(min = 0.0f, max = 10000.0f)
    public static float armorStatCap = 10.0f;

    @Entry(min = 0.0f, max = 10000.0f)
    public static float maxHealthStatCap = 20.0f;

    @Entry(min = 0.0f, max = 10000.0f)
    public static float cardDropChanceStatCap = 50.0f;

    public static float getStatMultiplier(CardStat stat) {
        if (stat == null || !enableCardStats || (!enablePlayerStats && isPlayerStat(stat)) || (!enableSpawnBoostStats && isSpawnStat(stat))) {
            return 0.0f;
        }

        if (isPlayerStat(stat)) {
            return globalStatMultiplier * playerStatMultiplier * getPerStatMultiplier(stat);
        }
        if (isSpawnStat(stat)) {
            return globalStatMultiplier * spawnBoostStatMultiplier;
        }

        // Fallback return, shouldn't get hit unless a stat does not count as either a player or spawn boost stat
        return globalStatMultiplier;
    }

    /**
     * Per-stat multiplier for the "vanilla attribute" / player stats.
     * Returns 1.0f for any stat without a dedicated config entry.
     */
    public static float getPerStatMultiplier(CardStat stat) {
        if (stat == null) return 1.0f;
        return switch (stat) {
            case MINING_SPEED -> miningSpeedStatMultiplier;
            case MOVEMENT_SPEED -> movementSpeedStatMultiplier;
            case ATTACK_DAMAGE -> attackDamageStatMultiplier;
            case ATTACK_SPEED -> attackSpeedStatMultiplier;
            case LUCK -> luckStatMultiplier;
            case ARMOR -> armorStatMultiplier;
            case MAX_HEALTH -> maxHealthStatMultiplier;
            case CARD_DROP_CHANCE -> cardDropChanceStatMultiplier;
            default -> 1.0f;
        };
    }

    /**
     * Cap for a stat's effective value, in the same units as
     * {@link com.howlite.cobblemoncards.util.CardStatUtil#getEffectiveValue}.
     * <p>
     * Returns {@link Float#MAX_VALUE} (i.e. no cap) when caps are disabled, for spawn stats
     * (already bounded by {@link #maxSpawnBoostMultiplier}), and for any stat without a
     * dedicated config entry.
     */
    public static float getStatCap(CardStat stat) {
        if (stat == null || !enableStatCaps || isSpawnStat(stat)) {
            return Float.MAX_VALUE;
        }
        return switch (stat) {
            case MINING_SPEED -> miningSpeedStatCap;
            case MOVEMENT_SPEED -> movementSpeedStatCap;
            case ATTACK_DAMAGE -> attackDamageStatCap;
            case ATTACK_SPEED -> attackSpeedStatCap;
            case LUCK -> luckStatCap;
            case ARMOR -> armorStatCap;
            case MAX_HEALTH -> maxHealthStatCap;
            case CARD_DROP_CHANCE -> cardDropChanceStatCap;
            default -> Float.MAX_VALUE;
        };
    }

    public static boolean isPlayerStat(CardStat stat) {
        return stat == CardStat.MINING_SPEED || stat == CardStat.MOVEMENT_SPEED
                || stat == CardStat.ATTACK_DAMAGE || stat == CardStat.ATTACK_SPEED
                || stat == CardStat.LUCK || stat == CardStat.ARMOR
                || stat == CardStat.MAX_HEALTH || stat == CardStat.CARD_DROP_CHANCE;
    }

    public static boolean isSpawnStat(CardStat stat) {
        return stat != null && stat.getSerializedName().endsWith("_spawn");
    }

    public static int getBinderPages(com.howlite.cobblemoncards.item.custom.BinderTier tier, int defaultPages) {
        if (tier == null) return defaultPages;
        return switch (tier) {
            case LEATHER -> leatherBinderPages;
            case IRON -> ironBinderPages;
            case GOLD -> goldBinderPages;
            case DIAMOND -> diamondBinderPages;
            case NETHERITE -> netheriteBinderPages;
            case MASTER -> masterAlbumPages;
        };
    }

    public static boolean isBinderTierEnabled(com.howlite.cobblemoncards.item.custom.BinderTier tier) {
        if (tier == null) return true;
        return switch (tier) {
            case LEATHER -> enableLeatherBinder;
            case IRON -> enableIronBinder;
            case GOLD -> enableGoldBinder;
            case DIAMOND -> enableDiamondBinder;
            case NETHERITE -> enableNetheriteBinder;
            case MASTER -> enableMasterAlbum;
        };
    }
}
