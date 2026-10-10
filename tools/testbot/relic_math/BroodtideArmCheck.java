import com.solme.emberfall.boss.BroodtideArmPlan;
import com.solme.emberfall.boss.BroodtideGrab;
import com.solme.emberfall.boss.BroodtideGrab.Phase;
import com.solme.emberfall.boss.TideClock;

/** Pure checks for the Broodtide's arm plan: counts per phase, the entity budget, the ring, the Tide-driven extension, the blend, and that an arm can always reach what the Grab can grab. */
public class BroodtideArmCheck {
    static int fails = 0;
    static int total = 0;

    static void check(String l, boolean ok, String e) {
        total++;
        System.out.println((ok ? "PASS " : "FAIL ") + l + "  " + e);
        if (!ok) {
            fails++;
        }
    }

    static boolean near(double a, double b) {
        return Math.abs(a - b) < 1.0e-9;
    }

    public static void main(String[] args) {
        // ---- counts and the entity budget (hand numbers: 3, 4, 5 arms; 8 links; at most 40 displays) ----
        check("A1 3 arms in phase one, 4 in phase two, 5 in phase three", BroodtideArmPlan.armCount(Phase.ONE) == 3 && BroodtideArmPlan.armCount(Phase.TWO) == 4 && BroodtideArmPlan.armCount(Phase.THREE) == 5, "");
        check("A2 the arms never exceed MAX_ARMS = 5, so the displays are allocated once", BroodtideArmPlan.MAX_ARMS == 5 && BroodtideArmPlan.armCount(Phase.THREE) <= BroodtideArmPlan.MAX_ARMS, "");
        check("A3 the entity budget is at most 40 displays (5 arms x 8 links), the number the brief allows", BroodtideArmPlan.maxEntities() == 40 && BroodtideArmPlan.LINKS == 8, "entities " + BroodtideArmPlan.maxEntities());
        boolean grows = true;
        for (Phase p : Phase.values()) {
            if (p.ordinal() > 0 && BroodtideArmPlan.armCount(p) <= BroodtideArmPlan.armCount(Phase.values()[p.ordinal() - 1])) {
                grows = false;
            }
        }
        check("A4 each phase has strictly more arms than the one before", grows, "");

        // ---- the ring ----
        int ringBad = 0;
        for (int n = 3; n <= 5; n++) {
            for (double spin = 0; spin < 6.3; spin += 0.37) {
                double sx = 0, sz = 0;
                for (int i = 0; i < n; i++) {
                    double dx = BroodtideArmPlan.rootDx(i, n, spin), dz = BroodtideArmPlan.rootDz(i, n, spin);
                    if (Math.abs(Math.hypot(dx, dz) - BroodtideArmPlan.ROOT_RADIUS) > 1.0e-9) {
                        ringBad++;
                    }
                    sx += dx;
                    sz += dz;
                }
                if (Math.abs(sx) > 1.0e-9 || Math.abs(sz) > 1.0e-9) {
                    ringBad++;   // an even ring sums to the centre, so the arms are spread, not bunched on one side
                }
            }
        }
        check("A5 every root sits exactly ROOT_RADIUS from the centre and the roots of 3, 4 and 5 arms balance round it at every spin", ringBad == 0, "bad " + ringBad);
        check("A6 two different arms never share a root (3, 4, 5 arms)", distinctRoots(), "");

        // ---- Tide-driven extension ----
        check("A7 EBB opens the arms from 0 to 1 over EXTEND_TICKS, then holds at 1", near(BroodtideArmPlan.extension(TideClock.State.EBB, 0), 0.0) && near(BroodtideArmPlan.extension(TideClock.State.EBB, BroodtideArmPlan.EXTEND_TICKS), 1.0) && near(BroodtideArmPlan.extension(TideClock.State.EBB, 10000), 1.0), "");
        check("A8 FLOOD closes the arms from 1 to 0 over the same time, then holds at 0", near(BroodtideArmPlan.extension(TideClock.State.FLOOD, 0), 1.0) && near(BroodtideArmPlan.extension(TideClock.State.FLOOD, BroodtideArmPlan.EXTEND_TICKS), 0.0) && near(BroodtideArmPlan.extension(TideClock.State.FLOOD, 10000), 0.0), "");
        boolean mono = true;
        double prev = -1;
        for (int t = 0; t <= 40; t++) {
            double e = BroodtideArmPlan.extension(TideClock.State.EBB, t);
            if (e < prev - 1e-12 || e < 0 || e > 1) {
                mono = false;
            }
            prev = e;
        }
        check("A9 the opening is smooth and monotonic and stays within 0..1", mono, "");
        check("A10 a negative tick count (a clock quirk) is treated as 0, never outside 0..1", near(BroodtideArmPlan.extension(TideClock.State.EBB, -50), 0.0) && near(BroodtideArmPlan.extension(TideClock.State.FLOOD, -50), 1.0), "");

        // ---- targets and the blend ----
        double[] curl = BroodtideArmPlan.curlTarget(0, 3, 0.0);
        check("A11 a curled arm ends CURL_RADIUS out and CURL_HEIGHT up, so it wraps the body", near(Math.hypot(curl[0], curl[2]), BroodtideArmPlan.CURL_RADIUS) && near(curl[1], BroodtideArmPlan.CURL_HEIGHT), "");
        double[] near = BroodtideArmPlan.huntTarget(0, 3, 0.0, true, 4.0, 0.0, 3.0);
        check("A12 a player inside reach is the target exactly", near(near[0], 4.0) && near(near[1], 0.0) && near(near[2], 3.0), "");
        double[] far = BroodtideArmPlan.huntTarget(0, 3, 0.0, true, 100.0, 2.0, 0.0);
        check("A13 a player beyond reach is clamped to HUNT_REACH along the same bearing, height kept", near(Math.hypot(far[0], far[2]), BroodtideArmPlan.HUNT_REACH) && near(far[2], 0.0) && near(far[1], 2.0), "len " + Math.hypot(far[0], far[2]));
        double[] idle = BroodtideArmPlan.huntTarget(1, 4, 0.0, false, 0, 0, 0);
        check("A14 with no player an arm reaches straight out along its own angle at ground level", near(Math.atan2(idle[2], idle[0]), BroodtideArmPlan.wrap(BroodtideArmPlan.angle(1, 4, 0.0))) && idle[1] < 1.0, "");
        double[] b0 = BroodtideArmPlan.blend(curl, near, 0.0), b1 = BroodtideArmPlan.blend(curl, near, 1.0), bh = BroodtideArmPlan.blend(curl, near, 0.5);
        check("A15 blend 0 is the curl, blend 1 is the hunt, and 0.5 is halfway", near(b0[0], curl[0]) && near(b1[2], near[2]) && near(bh[1], (curl[1] + near[1]) / 2), "");
        double[] bo = BroodtideArmPlan.blend(curl, near, 7.0), bn = BroodtideArmPlan.blend(curl, near, -7.0);
        check("A16 a blend outside 0..1 is clamped, so the arm can never overshoot its target", near(bo[0], near[0]) && near(bn[0], curl[0]), "");

        // ---- which arm grabs ----
        int wrongArm = 0;
        for (int n = 3; n <= 5; n++) {
            for (int i = 0; i < n; i++) {
                if (BroodtideArmPlan.grabbingArm(n, 0.3, BroodtideArmPlan.angle(i, n, 0.3) + 0.01) != i) {
                    wrongArm++;
                }
            }
        }
        check("A17 a player in line with arm i is grabbed by arm i, for 3, 4 and 5 arms", wrongArm == 0, "wrong " + wrongArm);
        check("A18 with no arms the answer is 0 and never an out-of-range index", BroodtideArmPlan.grabbingArm(0, 0.0, 1.0) == 0, "");
        check("A19 the bearing wrap handles 2 PI and negative angles", near(BroodtideArmPlan.wrap(2 * Math.PI), 0.0) && near(BroodtideArmPlan.wrap(-3 * Math.PI), -Math.PI) || near(BroodtideArmPlan.wrap(-3 * Math.PI), Math.PI), "");

        // ---- the arm must reach what the Grab can grab (independent of both constants' source) ----
        check("A20 an arm can reach a player at the Grab's full REACH, so no grab is ever made by a limb that falls short", BroodtideArmPlan.armTouches(BroodtideGrab.REACH) && BroodtideArmPlan.HUNT_REACH >= 14.0, "arm reach " + BroodtideArmPlan.HUNT_REACH + " vs grab " + BroodtideGrab.REACH);
        check("A21 spacing is at least 1.0 and at most 2.5 (a joint 2.5 apart reads as a gap, under 1.0 piles up)", BroodtideArmPlan.SPACING >= 1.0 && BroodtideArmPlan.SPACING <= 2.5, "spacing " + BroodtideArmPlan.SPACING);
        check("A22 the pull stops further out than the roots, so a pulled player is never inside the arm ring", BroodtideGrab.STOP_RADIUS > BroodtideArmPlan.ROOT_RADIUS, "stop " + BroodtideGrab.STOP_RADIUS + " ring " + BroodtideArmPlan.ROOT_RADIUS);
        check("A23 the extension time fits inside the shortest Ebb, so the arms are fully out for most of it", BroodtideArmPlan.EXTEND_TICKS * 3 <= TideClock.EBB_TICKS, "extend " + BroodtideArmPlan.EXTEND_TICKS + " ebb " + TideClock.EBB_TICKS);
        check("A24 the Grab's wind-up is no shorter than the arm's reach time, so the arm lands when the Grab does", BroodtideGrab.WINDUP_TICKS >= BroodtideArmPlan.EXTEND_TICKS, "windup " + BroodtideGrab.WINDUP_TICKS + " extend " + BroodtideArmPlan.EXTEND_TICKS);

        // ---- look: thick to thin, root to tip ----
        int n8 = BroodtideArmPlan.LINKS;
        boolean thinning = true;
        for (int i = 1; i < n8; i++) {
            if (BroodtideArmPlan.scaleFor(i, n8) >= BroodtideArmPlan.scaleFor(i - 1, n8)) {
                thinning = false;
            }
        }
        check("A25 every link is thinner than the one before it (thick root to thin tip, as in the Kuudra reference)", thinning, "");
        check("A26 the root link is 1.9 and the tip link is 0.55 (hand numbers, not read from the constants)", Math.abs(BroodtideArmPlan.scaleFor(0, n8) - 1.9) < 1.0e-5 && Math.abs(BroodtideArmPlan.scaleFor(n8 - 1, n8) - 0.55) < 1.0e-5, "root " + BroodtideArmPlan.scaleFor(0, n8) + " tip " + BroodtideArmPlan.scaleFor(n8 - 1, n8));
        check("A27 link 0 is moss, the last is the froglight, the one before it is the spike", BroodtideArmPlan.itemFor(0, n8).equals("moss_block") && BroodtideArmPlan.itemFor(n8 - 1, n8).equals("verdant_froglight") && BroodtideArmPlan.itemFor(n8 - 2, n8).equals("pointed_dripstone"), "");
        int slimeLinks = 0, mossLinks = 0;
        for (int i = 0; i < n8; i++) {
            String it = BroodtideArmPlan.itemFor(i, n8);
            if (it.equals("slime_block")) { slimeLinks++; }
            if (it.equals("moss_block")) { mossLinks++; }
        }
        check("A28 the arm has some moss root and some slime middle (not one material), and exactly one spike and one glow", mossLinks >= 2 && slimeLinks >= 2 && mossLinks + slimeLinks == n8 - 2, "moss " + mossLinks + " slime " + slimeLinks);
        check("A29 a 1 link arm is only the glow and never throws (an edge case, not a real arm)", BroodtideArmPlan.itemFor(0, 1).equals("verdant_froglight") && BroodtideArmPlan.scaleFor(0, 1) > 0, "");

        // ---- the clock helper the arms rely on ----
        int intoBad = 0;
        for (long t = 0; t < 3L * TideClock.CYCLE_TICKS; t++) {
            int len = TideClock.stateAt(t) == TideClock.State.EBB ? TideClock.EBB_TICKS : TideClock.FLOOD_TICKS;
            if (TideClock.ticksInto(t) + TideClock.ticksLeft(t) != len || TideClock.ticksInto(t) < 0 || TideClock.ticksInto(t) >= len) {
                intoBad++;
            }
        }
        check("A30 ticksInto + ticksLeft is the state's length on every tick of three full cycles, and ticksInto is 0 on the first tick of each state", intoBad == 0 && TideClock.ticksInto(0) == 0 && TideClock.ticksInto(TideClock.EBB_TICKS) == 0 && TideClock.ticksInto(TideClock.CYCLE_TICKS) == 0, "bad " + intoBad);
        check("A31 ticksInto never goes negative for a negative tick", TideClock.ticksInto(-5) == 0, "");

        System.out.println();
        System.out.println(fails == 0 ? "ALL PASS (" + total + " checks)" : "FAILED " + fails + " of " + total);
        System.exit(fails == 0 ? 0 : 1);
    }

    static boolean distinctRoots() {
        for (int n = 3; n <= 5; n++) {
            for (int i = 0; i < n; i++) {
                for (int j = i + 1; j < n; j++) {
                    if (Math.hypot(BroodtideArmPlan.rootDx(i, n, 0.2) - BroodtideArmPlan.rootDx(j, n, 0.2), BroodtideArmPlan.rootDz(i, n, 0.2) - BroodtideArmPlan.rootDz(j, n, 0.2)) < 0.5) {
                        return false;
                    }
                }
            }
        }
        return true;
    }
}
