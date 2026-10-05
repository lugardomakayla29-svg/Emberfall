package com.solme.emberfall.relic;

import java.util.function.DoubleSupplier;

/**
 * Decision rules for the offensive relics, PURE (no Minecraft types) so the maths can be proven on its own.
 *
 * One player hit on a hostile is handled in two steps:
 *  BEFORE the hit: {@link #scaledDamage} = incoming x Anvil x Crown x Soul x (Big Bonk x20 on its proc).
 *  AFTER the hit lands: {@link #lifesteal} heals from the REAL damage dealt, {@link #censerDamage} is each neighbour's
 *  share of that damage, and {@link #frostOutcome} says whether a proc chills or freezes.
 */
public final class RelicOffence {
    private RelicOffence() {}

    public enum Frost { NONE, CHILL, FREEZE }

    /** Damage after every multiplier relic. {@code soulKills} is this run's kills. Non-positive input is returned unchanged. */
    public static float scaledDamage(RelicStats stats, float incoming, int soulKills, DoubleSupplier roll) {
        if (incoming <= 0.0F) {
            return incoming;
        }
        double m = stats.damageMultiplier(soulKills) * stats.bonkMultiplier(roll);
        return (float) (incoming * m);
    }

    /** Health restored for a hit that really dealt {@code damageDealt}. Zero for no Blood Chalice or no damage. */
    public static float lifesteal(RelicStats stats, float damageDealt) {
        return damageDealt > 0.0F ? (float) (damageDealt * stats.lifestealShare()) : 0.0F;
    }

    /** True when this hit sets off the Spiked Censer. Consumes a roll only when the relic is held. */
    public static boolean censerProcs(RelicStats stats, DoubleSupplier roll) {
        double c = stats.censerChance();
        return c > 0.0 && roll.getAsDouble() < c;
    }

    /** Damage a Censer blast deals to each neighbour of a hit that dealt {@code damageDealt}. */
    public static float censerDamage(float damageDealt) {
        return damageDealt > 0.0F ? (float) (damageDealt * RelicStats.CENSER_SHARE) : 0.0F;
    }

    /** What a Frostbound Ring does on this hit. A proc on an already chilled foe freezes it. */
    public static Frost frostOutcome(RelicStats stats, boolean alreadyChilled, DoubleSupplier roll) {
        double c = stats.frostChance();
        if (c <= 0.0 || roll.getAsDouble() >= c) {
            return Frost.NONE;
        }
        return alreadyChilled ? Frost.FREEZE : Frost.CHILL;
    }
}
