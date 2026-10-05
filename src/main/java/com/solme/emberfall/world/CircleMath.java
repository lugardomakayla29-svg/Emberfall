package com.solme.emberfall.world;

/**
 * The geometry of the circular expedition map, with no game types so it can be tested without a server.
 * {@link CircleBoundary} uses this for every distance and landing-point calculation.
 */
public final class CircleMath {
    private CircleMath() {}

    /** Horizontal distance from the circle centre. */
    public static double distance(double cx, double cz, double x, double z) {
        return Math.hypot(x - cx, z - cz);
    }

    /** How far outside the play radius a point is: negative or 0 when inside. */
    public static double overshoot(double cx, double cz, double x, double z, double radius) {
        return distance(cx, cz, x, z) - radius;
    }

    /** The point on the circle of radius (radius - inset) nearest to (x,z), or (x,z) itself if already inside that. */
    public static double[] nearestInside(double cx, double cz, double x, double z, double radius, double inset) {
        double dx = x - cx;
        double dz = z - cz;
        double d = Math.hypot(dx, dz);
        if (d < 1.0E-6) {
            return new double[]{cx, cz};
        }
        double r = Math.min(d, radius - inset);
        return new double[]{cx + dx / d * r, cz + dz / d * r};
    }
}
