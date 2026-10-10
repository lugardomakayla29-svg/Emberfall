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
    /**
     * Fraction of the touching distance the neighbours overlap. MEASURED with shapely on the real arm sizes (2.4 down to 0.8): at 12% the two cubes stay ONE
     * connected solid at every bend from 0 to 45 degrees (never a see-through sliver), but the open notch on the OUTSIDE of a turn grows with the angle, from
     * 0.19 block squared on a straight joint (the taper step itself) to 0.86 at 14 degrees and 1.6 at 45 degrees for the thickest joint. So "no gaps" holds as
     * "always joined"; a tight curl still shows a bite on its outer edge. An earlier comment here claimed a wedge was hidden up to 25 degrees: that was not measured.
     */
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

    /** Total length of a glued chain of these sizes: the sum of its links. */
    public static double total(float[] sizes) {
        double t = 0.0;
        for (double d : spacings(sizes)) {
            t += d;
        }
        return t;
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
