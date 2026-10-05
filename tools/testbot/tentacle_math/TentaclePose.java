import java.util.List;
public final class TentaclePose {
    private TentaclePose() {}

    /** Ticks an arm stays planted on its strike point after the attack resolves. */
    public static final int HOLD_TICKS = 8;
    /** How high an arm rears over the head during a wind-up, in blocks above the head's base. */
    public static final double REAR_HEIGHT = 5.0;

    /** Progress 0..1 through a wind-up, clamped. */
    public static double progress(int ticks, int windup) {
        if (windup <= 0) {
            return 1.0;
        }
        return Math.max(0.0, Math.min(1.0, ticks / (double) windup));
    }

    /**
     * Blend between the idle target and a strike target. During the first part of the wind-up the arm lifts to the rear
     * position, and over the last {@code strikeFraction} of it the arm comes down onto the strike point, arriving exactly at
     * progress 1 (the resolve tick). After resolve it holds, which the caller expresses by passing {@code resolved}.
     *
     * @param rear   where the arm is held while rearing
     * @param strike where the arm lands
     */
    public static Vec3 blend(Vec3 idle, Vec3 rear, Vec3 strike, double progress, boolean resolved, double strikeFraction) {
        if (resolved) {
            return strike;
        }
        double riseEnd = 1.0 - strikeFraction;
        if (progress < riseEnd) {
            double k = smooth(progress / riseEnd);
            return idle.lerp(rear, k);
        }
        double k = smooth((progress - riseEnd) / strikeFraction);
        return rear.lerp(strike, k);
    }

    /** Smoothstep: eases in and out so an arm does not start or stop abruptly. */
    public static double smooth(double x) {
        double c = Math.max(0.0, Math.min(1.0, x));
        return c * c * (3.0 - 2.0 * c);
    }

    /**
     * Strike target for tentacle {@code t} of {@code n} in a RING attack: the arms spread evenly round the boss and land on
     * the ring's outer edge.
     */
    public static Vec3 ringStrike(Vec3 centre, int t, int n, double radius, double yawRad) {
        double a = yawRad + (t + 0.5) * (2.0 * Math.PI / n);
        return centre.add(Math.cos(a) * radius, 0.0, Math.sin(a) * radius);
    }

    /**
     * Strike target for a FAN attack: arm 0 lands at the far side of the locked cone, the others fan out either side of it
     * inside the cone's half angle, so the whole cone is visibly swept.
     */
    public static Vec3 fanStrike(Vec3 origin, Vec3 dir, double range, double halfAngleDeg, int t, int n) {
        double spread = n <= 1 ? 0.0 : (t / (double) (n - 1)) * 2.0 - 1.0;   // -1 .. +1 across the arms
        double ang = Math.toRadians(halfAngleDeg * 0.8) * spread;
        double c = Math.cos(ang), s = Math.sin(ang);
        Vec3 d = new Vec3(dir.x * c - dir.z * s, 0.0, dir.x * s + dir.z * c);
        return origin.add(d.scale(range * 0.85));
    }

    /** Strike target for a SPARKS attack: arm {@code t} lands on circle {@code t} (arms beyond the circle count rest on the first). */
    public static Vec3 sparkStrike(List<Vec3> centres, int t) {
        if (centres.isEmpty()) {
            return Vec3.ZERO;
        }
        return centres.get(Math.min(t, centres.size() - 1));
    }

    /** Strike target for a BEAM attack: arm 0 points down the locked line, the rest guard the sides of the boss. */
    public static Vec3 beamStrike(Vec3 origin, Vec3 dir, int t, int n) {
        if (t == 0) {
            return origin.add(dir.scale(8.0));
        }
        double side = (t % 2 == 1 ? 1.0 : -1.0) * (1.0 + 0.5 * (t / 2));
        return origin.add(dir.x * 3.0 - dir.z * side * 3.0, 0.0, dir.z * 3.0 + dir.x * side * 3.0);
    }
}
