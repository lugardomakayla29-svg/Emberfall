package com.solme.emberfall.relic;

/**
 * Weapon cadence and strike count from relics, PURE so it can be proven without a server.
 *
 * Wizard's Cowl shortens every weapon's delay between swings; Quiver of Plenty adds strikes to weapons that fire volleys.
 * A delay never drops below {@link #MIN_DELAY_TICKS}, so a stack of Cowls cannot turn a weapon into a once-per-tick machine
 * gun (the cowl is already capped at -50% by {@link RelicStats#cooldownMultiplier()}).
 */
public final class RelicTempo {
    private RelicTempo() {}

    public static final int MIN_DELAY_TICKS = 2;
    /** Quiver of Plenty can add at most this many strikes to one volley, however many are held. */
    public static final int MAX_EXTRA_STRIKES = 6;

    /** The delay (ticks) between swings after the Cowl. Rounded, never below the floor, never above the base. */
    public static int delay(RelicStats stats, int baseDelayTicks) {
        if (baseDelayTicks <= MIN_DELAY_TICKS) {
            return Math.max(1, baseDelayTicks);
        }
        int scaled = (int) Math.round(baseDelayTicks * stats.cooldownMultiplier());
        return Math.min(baseDelayTicks, Math.max(MIN_DELAY_TICKS, scaled));
    }

    /**
     * The ATTACK_SPEED modifier amount (ADD_MULTIPLIED_BASE) that makes the Cowl's delay multiplier true. A weapon's swing delay is
     * 1 / attackSpeed, so a delay of x0.5 needs attack speed x2, which is an amount of +1.0. Zero without a Cowl.
     */
    public static double attackSpeedBonus(RelicStats stats) {
        double m = stats.cooldownMultiplier();
        return m >= 1.0 ? 0.0 : (1.0 / m) - 1.0;
    }

    /** Extra strikes a volley gets from Quiver of Plenty, capped. */
    public static int extraStrikes(RelicStats stats) {
        return Math.min(MAX_EXTRA_STRIKES, Math.max(0, stats.extraStrikes()));
    }

    /** A volley of {@code base} strikes after the Quiver. A base of zero stays zero (a weapon that does not volley). */
    public static int strikes(RelicStats stats, int base) {
        return base <= 0 ? base : base + extraStrikes(stats);
    }
}
