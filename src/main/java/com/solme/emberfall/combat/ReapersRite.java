package com.solme.emberfall.combat;

import com.solme.emberfall.item.WeaponGrowth;

import java.util.List;

/**
 * The maths of the Spectral Sickles' ultimate, REAPER'S RITE, free of Minecraft types so it can be unit-checked. Three phases:
 * GATHER (foes are dragged fast to one spot), SLICE (both sickles sit there as a spinning disc and cut everything in it), BREAK (the sickles
 * shatter in one last big hit, then the ordinary ring returns).
 */
public final class ReapersRite {
    public static final int GATHER_TICKS = 30;                     // 1.5 s of dragging
    public static final double GATHER_PULL = 1.0;                  // velocity added toward the pile per pull
    public static final int GATHER_EVERY = 2;                      // ticks between pulls: 12 blocks in about 10 ticks
    public static final double GATHER_RANGE_BASE = 8.0;            // how far from the wielder a foe is still dragged
    public static final double GATHER_RANGE_PER_LEVEL = 6.0 / 9.0; // 14 at level 10
    public static final double PILE_STOP = 1.2;                    // a foe this close to the pile is no longer pulled (no overshoot)
    public static final double PILE_MAX_FROM_WIELDER = 9.0;       // the pile forms at most this far from the wielder, so it is never off-screen
    public static final double CLUSTER_RADIUS = 3.5;               // foes this close to a candidate count towards its score
    public static final int SLICE_BASE_TICKS = 100;                // 5 s at level 1
    public static final double SLICE_PER_LEVEL = 100.0 / 9.0;      // 10 s at level 10
    public static final double DISC_BASE = 3.0;                    // radius of the spinning disc
    public static final double DISC_PER_LEVEL = 2.0 / 9.0;         // 5 at level 10
    public static final int SLICE_EVERY = 3;                       // ticks between cuts on one foe
    public static final double CUT_BASE = 0.80;                    // of one sickle hit, per cut
    public static final double CUT_PER_LEVEL = 0.05;               // 1.25 at level 10
    public static final double BREAK_BASE = 4.0;                   // the shatter, times a sickle hit
    public static final double BREAK_PER_LEVEL = 0.6;              // 9.4 at level 10
    public static final double SLICE_KEEP_PULL = 0.25;             // a gentle pull during the slice keeps the pile together
    public static final int MAX_FOES = 40;                         // hard ceiling on foes handled per tick, so cost stays bounded

    private ReapersRite() {}

    public static double gatherRange(int level) {
        return WeaponGrowth.scale(GATHER_RANGE_BASE, GATHER_RANGE_PER_LEVEL, level);
    }

    public static int sliceTicks(int level) {
        return (int) Math.round(WeaponGrowth.scale(SLICE_BASE_TICKS, SLICE_PER_LEVEL, level));
    }

    public static double discRadius(int level) {
        return WeaponGrowth.scale(DISC_BASE, DISC_PER_LEVEL, level);
    }

    public static double cutMultiple(int level) {
        return WeaponGrowth.scale(CUT_BASE, CUT_PER_LEVEL, level);
    }

    public static double breakMultiple(int level) {
        return WeaponGrowth.scale(BREAK_BASE, BREAK_PER_LEVEL, level);
    }

    /** Total ticks the whole Rite takes: gather, then slice. The break is instant, on the last tick. */
    public static int totalTicks(int level) {
        return GATHER_TICKS + sliceTicks(level);
    }

    /**
     * Picks the pile point. Candidates are the foes' own positions; each scores by how many foes lie within {@link #CLUSTER_RADIUS} of it
     * (itself counts), ties broken by the nearer to the wielder. Then the point is pulled in toward the wielder so it is at most
     * {@link #PILE_MAX_FROM_WIELDER} away. Returns {x, z} or null for no foes.
     *
     * @param xs foe x positions   @param zs foe z positions   @param wx wielder x   @param wz wielder z
     */
    public static double[] pilePoint(List<double[]> foes, double wx, double wz) {
        if (foes.isEmpty()) {
            return null;
        }
        int bestScore = -1;
        double bestDist = Double.MAX_VALUE;
        double[] best = null;
        for (double[] c : foes) {
            int score = 0;
            for (double[] o : foes) {
                double dx = o[0] - c[0], dz = o[1] - c[1];
                if (dx * dx + dz * dz <= CLUSTER_RADIUS * CLUSTER_RADIUS) {
                    score++;
                }
            }
            double d = Math.hypot(c[0] - wx, c[1] - wz);
            if (score > bestScore || (score == bestScore && d < bestDist)) {
                bestScore = score;
                bestDist = d;
                best = c;
            }
        }
        double d = Math.hypot(best[0] - wx, best[1] - wz);
        if (d > PILE_MAX_FROM_WIELDER) {
            double k = PILE_MAX_FROM_WIELDER / d;
            return new double[] {wx + (best[0] - wx) * k, wz + (best[1] - wz) * k};
        }
        return new double[] {best[0], best[1]};
    }

    /** True while the foe should still be dragged this tick: it is farther than {@link #PILE_STOP} from the pile. */
    public static boolean shouldPull(double distToPile) {
        return distToPile > PILE_STOP;
    }

    /** True on the ticks the gather pulls (every {@link #GATHER_EVERY}th tick of the gather). */
    public static boolean pullTick(int tickInRite) {
        return tickInRite >= 0 && tickInRite < GATHER_TICKS && tickInRite % GATHER_EVERY == 0;
    }

    /** True on the ticks the slice cuts: every {@link #SLICE_EVERY}th tick after the gather. */
    public static boolean cutTick(int tickInRite) {
        int s = tickInRite - GATHER_TICKS;
        return s >= 0 && s % SLICE_EVERY == 0;
    }
}
