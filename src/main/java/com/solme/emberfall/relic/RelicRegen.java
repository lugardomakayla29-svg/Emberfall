package com.solme.emberfall.relic;

/**
 * Health regeneration from relics, as PURE code so the rules can be proven without a server.
 *
 * Dragon's Heart heals {@link RelicStats#regenPerSecond()} always. Campfire Core heals {@link RelicStats#stillRegenPerSecond()}
 * only while the player has stood still for {@link #STILL_TICKS_REQUIRED} ticks (so running never heals, but a brief
 * pause does not either). The game calls {@link #healFor} once per {@link #PERIOD_TICKS}; players with neither relic
 * cost nothing because {@link #active} is checked first.
 */
public final class RelicRegen {
    private RelicRegen() {}

    /** The game ticks this often, so the whole feature is one cheap check per player per second. */
    public static final int PERIOD_TICKS = 20;
    /** A player counts as "standing still" after this many consecutive still ticks (1.5 s). */
    public static final int STILL_TICKS_REQUIRED = 30;
    /** Horizontal movement below this many blocks per tick is "still" (vanilla walking is about 0.2). */
    public static final double STILL_SPEED = 0.02;

    /** True when this player holds any regeneration relic, so the caller can skip everyone else at once. */
    public static boolean active(RelicStats stats) {
        return stats.regenPerSecond() > 0.0 || stats.stillRegenPerSecond() > 0.0;
    }

    /** Whether the player moved this tick, from the squared horizontal distance since the previous tick. */
    public static boolean isStill(double dx, double dz) {
        return dx * dx + dz * dz < STILL_SPEED * STILL_SPEED;
    }

    /** Consecutive still ticks after one more tick: resets to 0 the moment the player moves. */
    public static int nextStillTicks(int stillTicks, boolean still) {
        return still ? Math.min(stillTicks + 1, 10_000) : 0;
    }

    /** Health to restore for one period. {@code stillTicks} is the player's current consecutive still ticks. */
    public static float healFor(RelicStats stats, int stillTicks) {
        double perSecond = stats.regenPerSecond();
        if (stillTicks >= STILL_TICKS_REQUIRED) {
            perSecond += stats.stillRegenPerSecond();
        }
        return (float) (perSecond * PERIOD_TICKS / 20.0);
    }
}
