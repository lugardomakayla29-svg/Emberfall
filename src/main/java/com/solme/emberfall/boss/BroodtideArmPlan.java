package com.solme.emberfall.boss;

/**
 * The Broodtide's arms as pure arithmetic: how many there are, where each one roots, how far it extends and where it points. No Minecraft types, so a
 * check can prove every rule without a server. The entity side ({@code BroodtideArms}) only reads these answers and moves display chains to them.
 *
 * <p>Plan (docs/PLAN_broodtide.md section 2 and the Vesper brief section 5): the arms are a ring around the body, 3 in phase one, 4 in phase two and 5 in
 * phase three. In EBB they are out and hunting; in FLOOD they curl back to the body. The arm that is grabbing reaches the grabbed player during the
 * wind-up and holds there. All numbers are PROPOSALS until a playtest; the look is UNSEEN.</p>
 */
public final class BroodtideArmPlan {
    private BroodtideArmPlan() {}

    /** Links per arm. 8 matches the Guardian's chains (4 x 8 = 32 displays), the entity budget the brief names. */
    public static final int LINKS = 9;
    /** Distance between neighbouring joints, in blocks: 7 gaps of 1.7 reach 11.9 blocks, so root ring (2.4) plus chain (11.9) = 14.3, past the 14 block Grab reach. A check pins this, so an arm never visibly falls short of a grab. */
    public static final double SPACING = 1.7;   // legacy mean; the glued arm uses BONES (per-link) below
    /** The arms root on a ring of this radius around the body centre, and this high above its feet. */
    public static final double ROOT_RADIUS = 3.4;
    public static final double ROOT_HEIGHT = 1.6;
    /** A resting (curled) arm ends this far from the body centre horizontally and this high. */
    public static final double CURL_RADIUS = 3.2;
    public static final double CURL_HEIGHT = 3.4;
    /** Ticks an arm takes to go from curled to fully out, and back. The Tide's Flood swell is the cue. */
    public static final int EXTEND_TICKS = 18;

    /** How many arms exist in a phase: 3, 4, 5. */
    public static int armCount(BroodtideGrab.Phase p) {
        return p == BroodtideGrab.Phase.ONE ? 3 : p == BroodtideGrab.Phase.TWO ? 4 : 5;
    }

    /** The most arms there ever are: the number of displays to allocate once and reuse, so a phase change spawns nothing. */
    public static final int MAX_ARMS = 5;

    /** The most display entities the arms ever use. This is the number the entity budget is judged on. */
    public static int maxEntities() {
        return MAX_ARMS * LINKS;
    }

    /**
     * The angle of arm {@code i} of {@code n} around the body, in radians. The ring is spread evenly and rotated by {@code spin} so the arms slowly turn;
     * with the same inputs the answer is the same every time (no randomness).
     */
    public static double angle(int i, int n, double spin) {
        return spin + (2.0 * Math.PI * i) / Math.max(1, n);
    }

    /** Root x offset of arm {@code i}, from the body centre. */
    public static double rootDx(int i, int n, double spin) {
        return Math.cos(angle(i, n, spin)) * ROOT_RADIUS;
    }

    public static double rootDz(int i, int n, double spin) {
        return Math.sin(angle(i, n, spin)) * ROOT_RADIUS;
    }

    /**
     * How far out the arms are, 0 (curled) to 1 (fully out), for a Tide state and how long it has been that way. EBB opens over {@link #EXTEND_TICKS}, FLOOD
     * closes over the same time. Pure: the caller passes the state and the ticks since it began.
     */
    public static double extension(TideClock.State state, long ticksIntoState) {
        double t = Math.min(1.0, Math.max(0.0, ticksIntoState / (double) EXTEND_TICKS));
        return state == TideClock.State.EBB ? t : 1.0 - t;
    }

    /** The target for a curled arm, relative to the body centre: the arm wraps up and round the body. */
    public static double[] curlTarget(int i, int n, double spin) {
        double a = angle(i, n, spin) + 0.9;   // turned a little from the root so the arm coils instead of standing straight up
        return new double[] {Math.cos(a) * CURL_RADIUS, CURL_HEIGHT, Math.sin(a) * CURL_RADIUS};
    }

    /**
     * The target for an arm that is out, relative to the body centre. With a player (dx, dz from the body, at height dy) it reaches toward them, clamped to
     * {@link #HUNT_REACH}; with no player it reaches straight out along its own angle at ground level.
     */
    public static double[] huntTarget(int i, int n, double spin, boolean hasPlayer, double dx, double dy, double dz) {
        if (!hasPlayer) {
            double a = angle(i, n, spin);
            return new double[] {Math.cos(a) * HUNT_REACH * 0.8, 0.4, Math.sin(a) * HUNT_REACH * 0.8};
        }
        double len = Math.sqrt(dx * dx + dz * dz);
        if (len > HUNT_REACH) {
            double k = HUNT_REACH / len;
            return new double[] {dx * k, dy, dz * k};
        }
        return new double[] {dx, dy, dz};
    }

    /** Blend between a curled and a hunting target by the extension, per axis. Extension 0 gives the curl exactly, 1 gives the hunt exactly. */
    public static double[] blend(double[] curl, double[] hunt, double ext) {
        double e = Math.min(1.0, Math.max(0.0, ext));
        return new double[] {
            curl[0] + (hunt[0] - curl[0]) * e,
            curl[1] + (hunt[1] - curl[1]) * e,
            curl[2] + (hunt[2] - curl[2]) * e
        };
    }

    /**
     * Which arm does the Grab. Of {@code n} arms the one whose own angle is closest to the bearing of the grabbed player, so the limb that reaches is the
     * one already pointing that way. Ties go to the lower index. Returns 0 when there are no arms.
     */
    public static int grabbingArm(int n, double spin, double bearing) {
        int best = 0;
        double bestDiff = Double.MAX_VALUE;
        for (int i = 0; i < n; i++) {
            double d = Math.abs(wrap(angle(i, n, spin) - bearing));
            if (d < bestDiff - 1.0e-12) {
                bestDiff = d;
                best = i;
            }
        }
        return best;
    }

    /**
     * Which arm does the Devour's reach: the arm closest to the bearing of the mob, but NEVER {@code busyArm} (the arm already doing the Grab), so the two beats use
     * different limbs. When no other arm exists (a single arm that is the busy one, or no arms) it returns -1, meaning "no free arm, skip this eat".
     * Ties go to the lower index.
     */
    public static int reachingArm(int n, double spin, double bearing, int busyArm) {
        int best = -1;
        double bestDiff = Double.MAX_VALUE;
        for (int i = 0; i < n; i++) {
            if (i == busyArm) {
                continue;
            }
            double d = Math.abs(wrap(angle(i, n, spin) - bearing));
            if (d < bestDiff - 1.0e-12) {
                bestDiff = d;
                best = i;
            }
        }
        return best;
    }

    /** The angle wrapped into -PI..PI. */
    public static double wrap(double a) {
        double r = a % (2.0 * Math.PI);
        if (r > Math.PI) {
            r -= 2.0 * Math.PI;
        } else if (r < -Math.PI) {
            r += 2.0 * Math.PI;
        }
        return r;
    }

    // ---- look (names are vanilla item ids; BroodtideArms turns them into items) ----
    /** Every link is the same material, a cube in the sickly slime colour (owner, 2026-10-10: a bunch of slimes from big to small, glued together). */
    public static final String LINK_ITEM = "sickly_slime_cube";
    /** Display scale at the root and at the tip: thick to thin, like the Kuudra reference. */
    public static final float BASE_SIZE = 2.4F;
    public static final float TIP_SIZE = 0.8F;
    /** Size of every link, root first: linear from BASE_SIZE to TIP_SIZE. */
    public static final float[] SIZES = sizes();
    /** Distance from joint i to joint i+1, glued: the two cubes touch, less a small overlap (see ChainGlue). */
    public static final double[] BONES = com.solme.emberfall.entity.ChainGlue.spacings(SIZES);

    private static float[] sizes() {
        float[] out = new float[LINKS];
        for (int i = 0; i < LINKS; i++) {
            out[i] = LINKS <= 1 ? BASE_SIZE : BASE_SIZE + (TIP_SIZE - BASE_SIZE) * (i / (float) (LINKS - 1));
        }
        return out;
    }

    /** A hunting arm points at the nearest player but never farther than this from the body centre (its own length from the root ring). */
    public static final double HUNT_REACH = ROOT_RADIUS + com.solme.emberfall.entity.ChainGlue.total(SIZES);

    /** The item id of link {@code i}: always the slime cube. */
    public static String itemFor(int i, int n) {
        return LINK_ITEM;
    }

    /** Display scale of link {@code i} of {@code n}: linear from BASE_SIZE at the root to TIP_SIZE at the tip. */
    public static float scaleFor(int i, int n) {
        if (n <= 1) {
            return BASE_SIZE;
        }
        return BASE_SIZE + (TIP_SIZE - BASE_SIZE) * (i / (float) (n - 1));
    }

    /** True if a grabbing arm can physically reach a player at horizontal distance {@code dist} from the body centre. The Grab's own REACH must always satisfy this. */
    public static boolean armTouches(double dist) {
        return dist <= HUNT_REACH;
    }
}
