package com.solme.emberfall.bot;

import java.util.List;

/**
 * The walking maths of an EmberTester, free of engine types so it can be proven on its own.
 *
 * <p>A client-less player cannot be driven by input or velocity (measured: both move it 0.00 blocks), only placed. So the
 * body is placed one short step per tick along a list of standable points that vanilla pathfinding already validated.
 * This class decides where the next placement is; it never looks at the world.
 */
public final class BotWalk {
    private BotWalk() {}

    /** A point on the route: x and z are block centres, y is the floor the feet stand on. */
    public record Point(double x, double y, double z) {}

    /** One step: where to stand now, which route index is next, and whether the route is finished. */
    public record Step(double x, double y, double z, int nextIndex, boolean arrived) {}

    private static final double EPS = 1.0e-9;

    /**
     * Advances one tick: horizontal distance {@code speed} toward {@code route.get(index)}. Leftover distance carries on to
     * the following points in the same tick, so a short segment never slows the bot. Height changes in proportion to the
     * horizontal distance walked on each segment, so a step or slope is smooth and never jumps a whole rise in one tick.
     */
    public static Step advance(double x, double y, double z, List<Point> route, int index, double speed) {
        double left = speed;
        int i = index;
        while (left > EPS && i < route.size()) {
            Point target = route.get(i);
            double dx = target.x() - x;
            double dz = target.z() - z;
            double dist = Math.hypot(dx, dz);
            if (dist <= left) {
                x = target.x();
                z = target.z();
                y = target.y();
                left -= dist;
                i++;
            } else {
                double f = left / dist;
                y += (target.y() - y) * f;
                x += dx * f;
                z += dz * f;
                left = 0.0;
            }
        }
        return new Step(x, y, z, i, i >= route.size());
    }

    /**
     * True when the bot is further than {@code tolerance} from the segment it is walking (the line from the previous point,
     * or from {@code (fromX, fromZ)} on the first leg, to the next point). It measures distance from the LINE, so a sideways
     * push is caught even when the bot is still close to the next point.
     */
    public static boolean offRoute(double x, double z, double fromX, double fromZ, List<Point> route, int index, double tolerance) {
        if (index >= route.size()) {
            return false;
        }
        Point next = route.get(index);
        double ax = index == 0 ? fromX : route.get(index - 1).x();
        double az = index == 0 ? fromZ : route.get(index - 1).z();
        double sx = next.x() - ax;
        double sz = next.z() - az;
        double len2 = sx * sx + sz * sz;
        double t = len2 < EPS ? 0.0 : Math.max(0.0, Math.min(1.0, ((x - ax) * sx + (z - az) * sz) / len2));
        return Math.hypot(x - (ax + sx * t), z - (az + sz * t)) > tolerance;
    }
}
