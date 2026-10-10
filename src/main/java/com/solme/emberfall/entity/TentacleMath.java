package com.solme.emberfall.entity;

import net.minecraft.world.phys.Vec3;

/**
 * Pure geometry for a tentacle: a chain of {@code n} joints that stays rooted at one point and reaches toward another.
 * No entities and no level, so it can be measured on its own.
 *
 * <p>The solver is FABRIK (forward and backward reaching inverse kinematics): one pass drags the chain toward the target
 * from the tip, the next pulls it back onto the root, and every joint ends up exactly {@code spacing} from its neighbour.
 * That is the same "exactly one spacing from the leader" rule {@link WormBody} uses, applied from both ends so the base
 * never slides away from the head. A target farther than the chain can reach leaves the chain straight and pointing at
 * it, which is what a fully stretched tentacle should look like.</p>
 */
public final class TentacleMath {
    private TentacleMath() {}

    /**
     * Moves {@code joints} (index 0 is the root) so that joint 0 stays at {@code root}, every neighbour pair is exactly
     * {@code spacing} apart, and the tip is as close to {@code target} as the length allows.
     *
     * @param iterations how many forward/backward sweeps to run; 2 is enough for a smooth curve from a nearby pose
     */
    public static void solve(Vec3[] joints, Vec3 root, Vec3 target, double spacing, int iterations) {
        int n = joints.length;
        if (n < 2) {
            if (n == 1) {
                joints[0] = root;
            }
            return;
        }
        double reach = spacing * (n - 1);
        Vec3 toTarget = target.subtract(root);
        double dist = toTarget.length();
        if (dist >= reach) {
            // Out of reach: lay the chain straight toward the target. FABRIK would converge here too, but slowly.
            Vec3 dir = dist > 1.0E-9 ? toTarget.scale(1.0 / dist) : new Vec3(0.0, 1.0, 0.0);
            for (int i = 0; i < n; i++) {
                joints[i] = root.add(dir.scale(i * spacing));
            }
            return;
        }
        for (int it = 0; it < iterations; it++) {
            // Backward: pin the tip on the target and pull each joint to sit one spacing from the one after it.
            joints[n - 1] = target;
            for (int i = n - 2; i >= 0; i--) {
                joints[i] = step(joints[i + 1], joints[i], spacing);
            }
            // Forward: pin the root back where it belongs and push each joint one spacing out from the one before it.
            joints[0] = root;
            for (int i = 1; i < n; i++) {
                joints[i] = step(joints[i - 1], joints[i], spacing);
            }
        }
    }

    // ---- Per-link bone lengths (owner, 2026-10-10: tentacles are big slimes at the root down to small ones at the tip, glued with NO gaps). ----
    // The scalar versions above keep one length for every link; these take one length PER LINK (bones[i] joins joint i to joint i+1), so a tapering chain
    // can have long links where its cubes are big and short links where they are small. Same algorithm, same guarantees, link by link.

    /** Total length of a chain with these bones. */
    public static double reachOf(double[] bones) {
        double r = 0.0;
        for (double b : bones) {
            r += b;
        }
        return r;
    }

    /** {@link #solve(Vec3[], Vec3, Vec3, double, int)} with one bone length per link; {@code bones.length} must be {@code joints.length - 1}. */
    public static void solve(Vec3[] joints, Vec3 root, Vec3 target, double[] bones, int iterations) {
        int n = joints.length;
        if (n < 2) {
            if (n == 1) {
                joints[0] = root;
            }
            return;
        }
        double reach = reachOf(bones);
        Vec3 toTarget = target.subtract(root);
        double dist = toTarget.length();
        if (dist >= reach) {
            Vec3 dir = dist > 1.0E-9 ? toTarget.scale(1.0 / dist) : new Vec3(0.0, 1.0, 0.0);
            double along = 0.0;
            for (int i = 0; i < n; i++) {
                joints[i] = root.add(dir.scale(along));
                if (i < bones.length) {
                    along += bones[i];
                }
            }
            return;
        }
        for (int it = 0; it < iterations; it++) {
            joints[n - 1] = target;
            for (int i = n - 2; i >= 0; i--) {
                joints[i] = step(joints[i + 1], joints[i], bones[i]);
            }
            joints[0] = root;
            for (int i = 1; i < n; i++) {
                joints[i] = step(joints[i - 1], joints[i], bones[i - 1]);
            }
        }
    }

    /** {@link #lay(Vec3[], Vec3, Vec3, double)} with one bone length per link. */
    public static void lay(Vec3[] joints, Vec3 root, Vec3 dir, double[] bones) {
        Vec3 u = dir.lengthSqr() > 1.0E-12 ? dir.normalize() : new Vec3(0.0, 1.0, 0.0);
        double along = 0.0;
        for (int i = 0; i < joints.length; i++) {
            joints[i] = root.add(u.scale(along));
            if (i < bones.length) {
                along += bones[i];
            }
        }
    }

    /** {@link #worstLinkError(Vec3[], double)} with one bone length per link. */
    public static double worstLinkError(Vec3[] joints, double[] bones) {
        double worst = 0.0;
        for (int i = 1; i < joints.length; i++) {
            worst = Math.max(worst, Math.abs(joints[i].distanceTo(joints[i - 1]) - bones[i - 1]));
        }
        return worst;
    }

    /** {@link #keepAboveFloor(Vec3[], Vec3, double, double, float, float)} with one bone length per link and the real size of each joint. */
    public static void keepAboveFloor(Vec3[] joints, Vec3 root, double floorY, double[] bones, float[] sizes) {
        int n = joints.length;
        for (int pass = 0; pass < 3; pass++) {
            for (int i = 1; i < n; i++) {
                double rest = floorY + sizes[i] * 0.5;
                if (joints[i].y < rest) {
                    joints[i] = new Vec3(joints[i].x, rest, joints[i].z);
                }
            }
            joints[0] = root;
            for (int i = 1; i < n; i++) {
                joints[i] = step(joints[i - 1], joints[i], bones[i - 1]);
            }
        }
        for (int i = 1; i < n; i++) {
            double rest = floorY + sizes[i] * 0.5;
            if (joints[i].y < rest) {
                joints[i] = new Vec3(joints[i].x, rest, joints[i].z);
            }
        }
    }

    /** The point exactly {@code spacing} from {@code anchor}, on the ray toward {@code from}. */
    private static Vec3 step(Vec3 anchor, Vec3 from, double spacing) {
        Vec3 d = from.subtract(anchor);
        double len = d.length();
        if (len < 1.0E-9) {
            return anchor.add(0.0, spacing, 0.0);   // coincident joints: pick a direction instead of dividing by zero
        }
        return anchor.add(d.scale(spacing / len));
    }

    /** Straight chain from {@code root} along {@code dir} (used to initialise a tentacle before its first solve). */
    public static void lay(Vec3[] joints, Vec3 root, Vec3 dir, double spacing) {
        Vec3 u = dir.lengthSqr() > 1.0E-12 ? dir.normalize() : new Vec3(0.0, 1.0, 0.0);
        for (int i = 0; i < joints.length; i++) {
            joints[i] = root.add(u.scale(i * spacing));
        }
    }

    /** Longest neighbour gap minus {@code spacing}, in absolute value: 0 means every link is exactly one spacing. */
    public static double worstLinkError(Vec3[] joints, double spacing) {
        double worst = 0.0;
        for (int i = 1; i < joints.length; i++) {
            worst = Math.max(worst, Math.abs(joints[i].distanceTo(joints[i - 1]) - spacing));
        }
        return worst;
    }

    /**
     * Lifts any joint that sank below {@code floorY + half its own size} back onto the floor, then restores the link lengths
     * from the root outward so the arm stays one piece. Leaves the root alone. A chain pulled up by the floor can end a touch
     * short of its target, which reads as the arm pressing against the ground, exactly what a tentacle should do.
     */
    public static void keepAboveFloor(Vec3[] joints, Vec3 root, double floorY, double spacing, float baseSize, float tipSize) {
        int n = joints.length;
        // Lift, then restore the link lengths, twice: the length fix can push a joint back under, and a second pass settles it
        // (a chain lying on the floor has horizontal slack, so the lengths are kept by sliding the joint sideways, not down).
        for (int pass = 0; pass < 3; pass++) {
            for (int i = 1; i < n; i++) {
                double rest = floorY + TentacleMath.taper(i, n, baseSize, tipSize) * 0.5;
                if (joints[i].y < rest) {
                    joints[i] = new Vec3(joints[i].x, rest, joints[i].z);
                }
            }
            joints[0] = root;
            for (int i = 1; i < n; i++) {
                joints[i] = step(joints[i - 1], joints[i], spacing);
            }
        }
        // Last word goes to the floor: a joint still a hair under it is set on it (link error stays well under a tenth).
        for (int i = 1; i < n; i++) {
            double rest = floorY + TentacleMath.taper(i, n, baseSize, tipSize) * 0.5;
            if (joints[i].y < rest) {
                joints[i] = new Vec3(joints[i].x, rest, joints[i].z);
            }
        }
    }

    /** Display scale for joint {@code i} of {@code n}: a wide base tapering to a thin tip. */
    public static float taper(int i, int n, float baseSize, float tipSize) {
        if (n <= 1) {
            return baseSize;
        }
        float t = i / (float) (n - 1);
        return baseSize + (tipSize - baseSize) * t;
    }
}
