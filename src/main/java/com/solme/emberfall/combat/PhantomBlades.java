package com.solme.emberfall.combat;

import com.solme.emberfall.item.WeaponGrowth;

/**
 * The maths of the Twin Daggers' ultimate, PHANTOM BLADES, free of Minecraft types so it can be unit-checked. When the meter fills, two ghost
 * daggers float at the wielder's sides and FOLLOW him. Each tick a blade either HUNTS (darts at a foe within reach of the wielder, cuts it on
 * its own cooldown, then darts to the next) or RETURNS to its side when nothing is in reach. Everything grows with the weapon level.
 */
public final class PhantomBlades {
    public static final int DURATION_BASE_TICKS = 60;              // 3 s at level 1
    public static final double DURATION_PER_LEVEL = 40.0 / 9.0;    // 5 s at level 10
    public static final double RANGE_BASE = 6.0;                   // blocks from the WIELDER a blade will hunt
    public static final double RANGE_PER_LEVEL = 8.0 / 9.0;        // 14 at level 10
    public static final int CUT_GAP_BASE = 4;                      // ticks between one blade's cuts
    public static final double CUT_GAP_PER_LEVEL = -2.0 / 9.0;     // 2 at level 10
    public static final int CUT_GAP_MIN = 2;
    public static final double CUT_BASE = 0.60;                    // of the triggering hit, per cut
    public static final double CUT_PER_LEVEL = 0.04;               // 0.96 at level 10
    /** How far a blade travels in one tick, in blocks: 50 blocks a second, faster than any mob. */
    public static final double BLADE_SPEED = 2.5;
    /** A blade cuts when it is this close to its foe (its body-centre). */
    public static final double CUT_REACH = 1.6;
    /** The blades float this far to each side of the wielder and this high, when they have nothing to hunt. */
    public static final double SIDE_OFFSET = 1.3;
    public static final double SIDE_HEIGHT = 1.4;
    public static final int BLADES = 2;
    /** A blade cuts its foe this many times, then darts to another: the zooming-about of the ultimate. (A foe that dies frees it sooner.) */
    public static final int CUTS_PER_FOE = 2;

    private PhantomBlades() {}

    public static int durationTicks(int level) {
        return (int) Math.round(WeaponGrowth.scale(DURATION_BASE_TICKS, DURATION_PER_LEVEL, level));
    }

    public static double range(int level) {
        return WeaponGrowth.scale(RANGE_BASE, RANGE_PER_LEVEL, level);
    }

    /** Ticks between one blade's cuts: 4 at level 1 down to 2 at level 10. */
    public static int cutGap(int level) {
        return Math.max(CUT_GAP_MIN, (int) Math.round(WeaponGrowth.scale(CUT_GAP_BASE, CUT_GAP_PER_LEVEL, level)));
    }

    /** The damage of one cut as a multiple of the hit that opened the ultimate. */
    public static double cutMultiple(int level) {
        return WeaponGrowth.scale(CUT_BASE, CUT_PER_LEVEL, level);
    }

    /** The resting spot of blade {@code index} (0 left, 1 right) relative to the wielder facing {@code yawRad}: returns {dx, dy, dz}. Pure. */
    public static double[] restingOffset(int index, double yawRad) {
        double side = index == 0 ? -1.0 : 1.0;
        // "right" of a wielder facing yaw: (-cos yaw, -sin yaw) in Minecraft's x/z with yaw 0 = +z. Blades sit at +-SIDE_OFFSET on that axis.
        double rx = -Math.cos(yawRad), rz = -Math.sin(yawRad);
        return new double[] {side * SIDE_OFFSET * rx, SIDE_HEIGHT, side * SIDE_OFFSET * rz};
    }

    /**
     * Moves a point {@code from} toward {@code to} by at most {@code speed}. Returns the new {x, y, z}; when the target is nearer than the speed
     * the blade lands exactly on it (never overshoots), so a blade never flickers back and forth across a foe.
     */
    public static double[] stepToward(double[] from, double[] to, double speed) {
        double dx = to[0] - from[0], dy = to[1] - from[1], dz = to[2] - from[2];
        double d = Math.sqrt(dx * dx + dy * dy + dz * dz);
        if (d <= speed || d < 1.0E-9) {
            return new double[] {to[0], to[1], to[2]};
        }
        double k = speed / d;
        return new double[] {from[0] + dx * k, from[1] + dy * k, from[2] + dz * k};
    }

    /** True when a blade at {@code dist} from its foe may cut (close enough and its cooldown is over). */
    public static boolean mayCut(double dist, long now, long lastCut, int gap) {
        return dist <= CUT_REACH && now - lastCut >= gap;
    }

    /** Whether a foe at {@code distFromWielder} is still worth hunting: within the level's range of the wielder. */
    public static boolean inRange(double distFromWielder, int level) {
        return distFromWielder <= range(level);
    }
}
