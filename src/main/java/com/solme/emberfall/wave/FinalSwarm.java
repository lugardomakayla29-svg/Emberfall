package com.solme.emberfall.wave;

/**
 * The end-of-run Final Swarm, as PURE numbers so every rule is provable without a server.
 *
 * Once every boss is dead the map is flooded with powerful mobs and an optional escape portal opens. A silver MULTIPLIER
 * climbs in slow steps; leaving through the portal cashes out the current multiplier, staying keeps it rising and keeps the
 * swarm growing. The multiplier is capped at {@link #CAP}; at the cap a player who is still alive meets the Wrath phase.
 *
 * Steps are a table (not a formula) so the pace is exactly what the design says: slow at the start (0.1, 0.2, 0.3, 0.4, 0.5),
 * then 0.5 to 1.0 in tenths, then 1.1 to 2.0, 2.1 to 3.0 and 3.1 to 5.0 each one tenth per step. The tier names follow
 * the ranges: 0.1-1.0 Easy, 1.1-2.0 Medium, 2.1-3.0 Hard, 3.1-5.0 Nightmare.
 */
public final class FinalSwarm {
    private FinalSwarm() {}

    /** Seconds between two multiplier steps. */
    public static final int STEP_SECONDS = 20;
    /** The highest multiplier, in tenths. */
    public static final int CAP_TENTHS = 50;
    public static final double CAP = CAP_TENTHS / 10.0;
    /** Hard ceiling on swarm mobs alive at once, whatever the step: the entity budget. */
    public static final int MOB_CEILING = 60;
    /** Extra movement speed at the cap, as a fraction (0.45 = 45% faster than normal). */
    public static final double SPEED_AT_CAP = 0.45;

    public enum Tier {
        EASY("Easy"), MEDIUM("Medium"), HARD("Hard"), NIGHTMARE("Nightmare");

        private final String label;

        Tier(String label) { this.label = label; }

        public String label() { return label; }
    }

    /** The multiplier, in tenths, after {@code steps} steps have passed (step 0 is the moment the swarm begins: 0.1x). */
    public static int tenthsAtStep(int steps) {
        return Math.max(1, Math.min(CAP_TENTHS, 1 + Math.max(0, steps)));
    }

    public static double multiplierAtStep(int steps) {
        return tenthsAtStep(steps) / 10.0;
    }

    /** How many whole steps have passed after {@code seconds} of swarm. */
    public static int stepsAfter(long seconds) {
        return (int) Math.max(0, seconds / STEP_SECONDS);
    }

    /** The step at which the cap is reached. */
    public static int stepsToCap() {
        return CAP_TENTHS - 1;
    }

    public static boolean atCap(long seconds) {
        return stepsAfter(seconds) >= stepsToCap();
    }

    public static Tier tierOf(int tenths) {
        if (tenths <= 10) {
            return Tier.EASY;
        }
        if (tenths <= 20) {
            return Tier.MEDIUM;
        }
        if (tenths <= 30) {
            return Tier.HARD;
        }
        return Tier.NIGHTMARE;
    }

    /** Fraction of the way to the cap, 0..1, used to scale both the crowd and its speed. */
    public static double progress(int tenths) {
        return (Math.max(1, Math.min(CAP_TENTHS, tenths)) - 1) / (double) (CAP_TENTHS - 1);
    }

    /** Swarm mobs that should be alive now for this many players: a crowd that grows with the multiplier, never above the ceiling. */
    public static int targetMobs(int tenths, int players) {
        int p = Math.max(1, players);
        double perPlayer = 6 + 14 * progress(tenths);
        double extraForParty = 1.0 + 0.6 * (Math.min(p, 10) - 1);
        return (int) Math.min(MOB_CEILING, Math.round(perPlayer * extraForParty));
    }

    /** Extra movement speed (fraction) the swarm has at this multiplier. */
    public static double speedBonus(int tenths) {
        return SPEED_AT_CAP * progress(tenths);
    }

    /** The pure scaled amount: the base reward times the multiplier, rounded, never below zero. */
    public static long scaled(long baseSilver, int tenths) {
        return Math.max(0, Math.round(Math.max(0, baseSilver) * (tenths / 10.0)));
    }

    /**
     * The silver paid when a player LEAVES THROUGH THE PORTAL at this multiplier. Never less than the plain base: walking out
     * early must not pay worse than the run would have paid anyway, so the portal is an opportunity, never a trap. Below 1.0x it
     * simply pays the base; above it the multiplier applies. (A death or disconnect pays the plain base and no bonus.)
     */
    public static long cashOut(long baseSilver, int tenths) {
        return Math.max(Math.max(0, baseSilver), scaled(baseSilver, tenths));
    }

    /** True once the Wrath phase (screams, combustion, thrashing, terror) applies to a player still fighting at the cap. */
    public static boolean wrath(int tenths) {
        return tenths >= CAP_TENTHS;
    }
}
