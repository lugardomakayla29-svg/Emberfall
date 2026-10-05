package com.solme.emberfall.relic;

import java.util.function.DoubleSupplier;

/**
 * The decision rules for incoming damage, as PURE code (no Minecraft types) so the ordering can be proven.
 *
 * Order for one incoming hit, each step only if the relic is held:
 *  1. Mirror Shard invulnerability window: while active, the hit is ignored entirely.
 *  2. Ender Pearl Shard: the hit is dodged with the stat's chance.
 *  3. Otherwise the hit lands. Afterwards Thorn Vest and Mirror Shard return damage to a melee attacker.
 * A fatal hit is handled separately: Totem of Returning saves the player once per run.
 */
public final class RelicDefence {
    private RelicDefence() {}

    public enum Verdict { IGNORE_INVULNERABLE, DODGED, LANDS }

    /** Decides what happens to one incoming hit. {@code invulnerableTicksLeft} is the Mirror window still running. */
    public static Verdict judge(RelicStats stats, int invulnerableTicksLeft, DoubleSupplier roll) {
        if (invulnerableTicksLeft > 0) {
            return Verdict.IGNORE_INVULNERABLE;
        }
        double dodge = stats.dodgeChance();
        if (dodge > 0.0 && roll.getAsDouble() < dodge) {
            return Verdict.DODGED;
        }
        return Verdict.LANDS;
    }

    /**
     * Damage sent back at a melee attacker after a hit LANDS: Thorn Vest's flat amount plus Mirror Shard's share of the
     * damage taken. Zero when neither is held or when {@code damageTaken} is not positive.
     */
    public static float reflected(RelicStats stats, float damageTaken) {
        if (damageTaken <= 0.0F) {
            return 0.0F;
        }
        double total = stats.thornDamage();
        if (stats.hasMirror()) {
            total += damageTaken * RelicStats.MIRROR_REFLECT_SHARE;
        }
        return (float) total;
    }

    /** True when Mirror Shard should start (or restart) its invulnerability window: held and off cooldown. */
    public static boolean mirrorReady(RelicStats stats, int cooldownTicksLeft) {
        return stats.hasMirror() && cooldownTicksLeft <= 0;
    }

    /** Totem of Returning: saves the player from a fatal hit if held and not yet spent this run. */
    public static boolean totemSaves(RelicStats stats, boolean alreadySpent) {
        return stats.hasTotem() && !alreadySpent;
    }

    /** Health the Totem restores: half of max health, never below 1 heart. */
    public static float totemHealth(float maxHealth) {
        return Math.max(2.0F, maxHealth * 0.5F);
    }
}
