package com.solme.emberfall.rift;

import java.util.List;

/**
 * Pure choices a Rift needs before it opens, with no Minecraft types so a check can run them without the game jar.
 *
 * <ul>
 *   <li>{@link #hasOpenAir}: a Rift is drawn with particles, so a block in the way hides some of it but breaks nothing. The rule is still
 *       that most of the opening must be air, otherwise the tear would be buried in a hillside and nobody would see it open.</li>
 *   <li>{@link #facingToward}: which of the four facings makes the Rift look at a given player.</li>
 *   <li>{@link #nearest}: distance to the nearest existing Rift, the input RiftRules.spacingOk needs.</li>
 * </ul>
 */
public final class RiftSpot {
    private RiftSpot() {}

    /** At least this share of the sampled cells must be non-solid (air, water, plants) for a spot to count as open air. PROPOSAL. */
    public static final double MIN_OPEN_SHARE = 0.70;

    /** Whether {@code openCells} of {@code sampledCells} are non-solid enough. An empty sample is never open. */
    public static boolean hasOpenAir(int openCells, int sampledCells) {
        if (sampledCells <= 0 || openCells < 0 || openCells > sampledCells) {
            return false;
        }
        return openCells >= Math.ceil(sampledCells * MIN_OPEN_SHARE);
    }

    /**
     * The facing (RiftPlacement's 0..3: 0 = +Z, 1 = -X, 2 = -Z, 3 = +X) whose normal points from the Rift toward a player at
     * (dx, dz) relative to the Rift. At the exact same column the Rift faces +Z, a defined answer instead of a coin toss.
     */
    public static int facingToward(double dx, double dz) {
        if (Math.abs(dx) > Math.abs(dz)) {
            return dx > 0 ? 3 : 1;
        }
        if (dz == 0.0) {
            return 0;
        }
        return dz > 0 ? 0 : 2;
    }

    /** One existing Rift's position, for the spacing measure. */
    public record At(double x, double y, double z) {}

    /** Distance to the nearest of {@code rifts}, or {@code Double.POSITIVE_INFINITY} when there are none. */
    public static double nearest(List<At> rifts, double x, double y, double z) {
        double best = Double.POSITIVE_INFINITY;
        for (At r : rifts) {
            double dx = r.x() - x;
            double dy = r.y() - y;
            double dz = r.z() - z;
            best = Math.min(best, Math.sqrt(dx * dx + dy * dy + dz * dz));
        }
        return best;
    }
}
