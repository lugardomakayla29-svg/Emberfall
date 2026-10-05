package com.solme.emberfall.combat;

/**
 * The maths of a travelling shock wave, kept free of Minecraft types so it can be unit-checked. The War Slam used to hit every foe in its
 * radius on one tick. It is now a RIPPLE: a front that leaves the wielder and moves outward, hitting each foe on the step where the front
 * passes it, then two weaker AFTERSHOCKS that sweep the same ground again.
 */
public final class QuakeWave {
    /** How far the front advances each tick, in blocks. 0.6 crosses 12 blocks in exactly one second. */
    public static final double FRONT_SPEED = 0.6;
    /** Aftershocks: a share of the main slam each, in order. Together with the slam this is 1.7x the old single blast. */
    public static final double[] AFTERSHOCK_SHARE = {0.45, 0.25};
    /** Ticks between the end of one wave and the start of the next. */
    public static final int AFTERSHOCK_GAP_TICKS = 12;

    private QuakeWave() {}

    /** The ticks a wave takes to reach {@code radius}, at least 1. */
    public static int travelTicks(double radius) {
        return Math.max(1, (int) Math.ceil(radius / FRONT_SPEED));
    }

    /** The front's distance from the origin after {@code step} ticks (step 1 is the first), clamped to the radius. */
    public static double frontAt(double radius, int step) {
        return Math.min(radius, step * FRONT_SPEED);
    }

    /**
     * True when a foe at {@code distance} from the origin is passed by the front during {@code step}: its distance lies in (previous front,
     * current front]. On step 1 the previous front is -1, so a foe standing on the wielder is caught too. Every foe within the radius is
     * caught on exactly one step; a foe outside the radius on none.
     */
    public static boolean passedOnStep(double radius, int step, double distance) {
        if (distance > radius) {
            return false;
        }
        double from = step <= 1 ? -1.0 : frontAt(radius, step - 1);
        double to = frontAt(radius, step);
        return distance > from && distance <= to;
    }

    /** The damage multiple of the wave number {@code wave} (0 = the main slam, 1 and 2 = aftershocks) given the slam's own multiple. */
    public static double shareOf(int wave, double slamMultiple) {
        return wave <= 0 ? slamMultiple : slamMultiple * AFTERSHOCK_SHARE[Math.min(wave, AFTERSHOCK_SHARE.length) - 1];
    }

    /** How many waves a slam has in total: the slam itself plus its aftershocks. */
    public static int waves() {
        return 1 + AFTERSHOCK_SHARE.length;
    }
}
