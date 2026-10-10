package com.solme.emberfall.entity;

/**
 * The "no gaps" rule for a chain of cubes (owner, 2026-10-10: the Devourer, the Broodtide arms and the Brood-Kin must be glued, no gaps).
 *
 * <p>Two cubes of scale {@code a} and {@code b} touch face to face when their centres are {@code (a + b) / 2} apart. A single fixed spacing for a
 * chain whose cubes change size is therefore wrong at every link: too far (a gap) where the cubes are big, too close (a tangle) where they are small.
 * The fix is one spacing PER LINK, then pulled in by {@link #OVERLAP} so neighbours sink into each other: on a bend a cube chain opens a wedge on the
 * outside of the turn, and sinking the faces into each other is what hides it.
 */
public final class ChainGlue {
    /** Fraction of the touching distance the neighbours overlap. 0.12 hides a wedge up to roughly a 25 degree bend per link. */
    public static final double OVERLAP = 0.12;

    private ChainGlue() {
    }

    /** Distance between the centres of two neighbouring cubes of scale {@code a} and {@code b}: touching, less the overlap. */
    public static double linkSpacing(double a, double b) {
        return (Math.abs(a) + Math.abs(b)) * 0.5 * (1.0 - OVERLAP);
    }

    /** The spacing of every link of a chain: element i is the distance from part i to part i+1, so the array is one shorter than {@code sizes}. */
    public static double[] spacings(float[] sizes) {
        int n = Math.max(0, sizes.length - 1);
        double[] out = new double[n];
        for (int i = 0; i < n; i++) {
            out[i] = linkSpacing(sizes[i], sizes[i + 1]);
        }
        return out;
    }

    /**
     * The widest empty slice between two neighbours that are {@code spacing} apart: zero when they touch or overlap. This is the quantity the owner
     * sees as a "gap", and the one a check can bound.
     */
    public static double gap(double a, double b, double spacing) {
        return Math.max(0.0, spacing - (Math.abs(a) + Math.abs(b)) * 0.5);
    }

    /** Position along a ring of part i when each link has its own spacing: the arc length so far is the sum of the earlier links. */
    public static double arcBefore(double[] spacings, int i) {
        double s = 0.0;
        for (int k = 0; k < i && k < spacings.length; k++) {
            s += spacings[k];
        }
        return s;
    }
}
