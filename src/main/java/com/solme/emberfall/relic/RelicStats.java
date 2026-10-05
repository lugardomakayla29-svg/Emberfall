package com.solme.emberfall.relic;

import java.util.Map;
import java.util.function.DoubleSupplier;

/**
 * Every relic number, as a PURE function of the stacks a player holds (no Minecraft types, so a standalone check
 * proves them). The game systems ask this class; nothing else stores relic numbers, so two code paths cannot disagree.
 * Per-stack values are flat and small because every relic has a low stack cap (1 to 10).
 */
public final class RelicStats {
    private final Map<String, Integer> s;

    public RelicStats(Map<String, Integer> stacks) {
        this.s = stacks;
    }

    private int n(String id) {
        return s.getOrDefault(id, 0);
    }

    // ---- economy / progression ----
    public double luck()               { return 8.0 * n("clover"); }
    public double goldMultiplier()     { return 1.0 + 0.15 * n("gold_nugget"); }
    public double xpMultiplier()       { return 1.0 + 0.10 * n("clockwork_charm"); }
    public double keyChance()          { return RelicMath.keyChance(n("ember_key")); }
    public boolean capsChestPrice()    { return n("ember_ledger") > 0; }
    public double pickupRangeMultiplier() { return pickupRangeMultiplier(n("magnet_stone")); }
    /** Also used by the pickup loop, which reads the stack count directly to avoid allocating every tick. */
    public static double pickupRangeMultiplier(int magnetStacks) { return 1.0 + 0.25 * Math.max(0, magnetStacks); }

    // ---- attributes (applied by RelicEffects as one fixed-id modifier each) ----
    public double bonusMaxHealth()     { return 4.0 * n("oat_loaf") + 40.0 * n("dragons_heart"); }
    public double speedMultiplierDelta() { return 0.08 * n("iron_boots"); }

    // ---- defence ----
    public double dodgeChance()        { return Math.min(0.40, 0.08 * n("pearl_shard")); }
    public double thornDamage()        { return 2.0 * n("thorn_vest"); }
    /** Health per second while standing still (Campfire Core) plus the Dragon's Heart trickle. */
    public double stillRegenPerSecond() { return 0.5 * n("campfire_core"); }
    public double regenPerSecond()     { return 0.5 * n("dragons_heart"); }
    public boolean hasTotem()          { return n("totem_of_returning") > 0; }
    public boolean hasMirror()         { return n("mirror_shard") > 0; }
    public boolean hasHourglass()      { return n("hourglass") > 0; }
    /** Mirror Shard: damage returned to the attacker as a share of the hit, and the invulnerability window in ticks. */
    public static final double MIRROR_REFLECT_SHARE = 0.5;
    public static final int MIRROR_INVULN_TICKS = 20;
    public static final int MIRROR_COOLDOWN_TICKS = 200;
    /** Hourglass: below this health share foes slow to half speed. */
    public static final double HOURGLASS_HEALTH_SHARE = 0.5;
    public static final double HOURGLASS_SLOW = 0.5;

    // ---- offence ----
    public int extraStrikes()          { return n("quiver_of_plenty"); }
    public double cooldownMultiplier() { return Math.max(0.5, 1.0 - 0.10 * n("wizards_cowl")); }
    public double lifestealShare()     { return 0.03 * n("blood_chalice"); }
    public double bonkChance()         { return 0.02 * n("big_bonk"); }
    public static final double BONK_MULTIPLIER = 20.0;
    public double censerChance()       { return 0.10 * n("spiked_censer"); }
    public double frostChance()        { return 0.10 * n("frostbound_ring"); }
    /** Spiked Censer blast: radius in blocks and the share of the triggering hit it deals to each other foe. */
    public static final double CENSER_RADIUS = 3.0;
    public static final double CENSER_SHARE = 0.60;
    public static final int CENSER_MAX_VICTIMS = 6;
    /** Frostbound Ring: a proc chills (Slowness II) for this long; a second proc on a chilled foe freezes it (Slowness V). */
    public static final int FROST_CHILL_TICKS = 60;
    public static final int FROST_FREEZE_TICKS = 40;
    /** Anvil of Dawn: flat bonus to every weapon strike. */
    public double anvilBonus()         { return 0.25 * n("anvil_of_dawn"); }
    /** Wither Crown: damage bonus. The run-danger cost is {@link #threatBonus()}. */
    public double crownDamageBonus()   { return 0.50 * n("wither_crown"); }
    public double threatBonus()        { return 4.0 * n("wither_crown"); }
    /** Soul Lantern: permanent damage per kill, capped so it cannot run away. */
    public static final double SOUL_PER_KILL = 0.002;
    public static final double SOUL_CAP = 1.0;

    /**
     * Combined damage multiplier for one hit, from everything that scales damage. {@code soulKills} is this run's kill
     * count for Soul Lantern. Big Bonk is rolled separately by {@link #bonkMultiplier}.
     */
    public double damageMultiplier(int soulKills) {
        double soul = n("soul_lantern") > 0 ? Math.min(SOUL_CAP, SOUL_PER_KILL * Math.max(0, soulKills)) : 0.0;
        return (1.0 + anvilBonus()) * (1.0 + crownDamageBonus()) * (1.0 + soul);
    }

    /** 1.0 normally, {@link #BONK_MULTIPLIER} when the Big Bonk Hammer procs. */
    public double bonkMultiplier(DoubleSupplier roll) {
        double c = bonkChance();
        return c > 0 && roll.getAsDouble() < c ? BONK_MULTIPLIER : 1.0;
    }
}
