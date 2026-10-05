package com.solme.emberfall.relic;

/**
 * Hourglass: while the holder is below half health, foes move at about half speed. PURE so it can be proven without a server.
 *
 * Vanilla Slowness takes 15% off movement per level (bytecode: -0.15, ADD_MULTIPLIED_TOTAL), so Slowness III (amplifier 2) is x0.55,
 * the closest step to "half". The effect is re-applied each one second sweep with a short duration, so it fades by itself the moment
 * the holder heals, drops the relic, dies, or leaves: nothing is stored on the foe and nothing has to be undone.
 */
public final class RelicHourglass {
    private RelicHourglass() {}

    /** Slowness amplifier used (0 based): III. */
    public static final int AMPLIFIER = 2;
    /** Duration of each application: a little over the 20 tick sweep so there is never a gap, short so it fades at once. */
    public static final int DURATION_TICKS = 30;

    /** The movement factor the amplifier gives, 1 + (-0.15 * (amplifier + 1)). */
    public static double speedFactor() {
        return 1.0 - 0.15 * (AMPLIFIER + 1);
    }

    /** True when the holder owns the relic and is strictly below the health share. Dead, zero max health or NaN never counts. */
    public static boolean slowsFoes(RelicStats stats, double health, double maxHealth) {
        if (!stats.hasHourglass() || !(maxHealth > 0.0) || !(health > 0.0)) {
            return false;
        }
        return health / maxHealth < RelicStats.HOURGLASS_HEALTH_SHARE;
    }
}
