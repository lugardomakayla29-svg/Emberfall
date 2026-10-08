import com.solme.emberfall.rift.RiftEntry;
import com.solme.emberfall.rift.RiftShape;

/**
 * RiftEntryCheck: the click target of an open Rift. Pure, no game jar. Run from tools/testbot/relic_math.
 * The headline property: the click target is only ever over the tear. For many seeds, in both orientations, a target-sized rectangle must fit
 * ENTIRELY on body cells somewhere in the silhouette, so a click on the target is a click on the Rift and never on empty air beside it.
 */
public class RiftEntryCheck {
    static int fails = 0, total = 0;
    static void check(String name, boolean ok, String extra) { total++; System.out.println((ok ? "PASS " : "FAIL ") + name + (extra.isEmpty() ? "" : "  " + extra)); if (!ok) fails++; }

    /** True if a w by h block of cells (w, h whole cells) lies entirely on body cells somewhere in the shape. */
    static boolean fits(RiftShape.Shape s, int w, int h) {
        for (int x0 = 0; x0 + w <= s.width; x0++) {
            for (int y0 = 0; y0 + h <= s.height; y0++) {
                boolean all = true;
                for (int x = x0; x < x0 + w && all; x++) {
                    for (int y = y0; y < y0 + h; y++) {
                        if (!s.isBody(x, y)) { all = false; break; }
                    }
                }
                if (all) return true;
            }
        }
        return false;
    }

    public static void main(String[] a) {
        int wCells = (int) Math.floor(RiftEntry.TARGET_WIDTH);
        int hCells = (int) Math.floor(RiftEntry.TARGET_HEIGHT);

        // S: size. The target must be strictly inside the narrowest and shortest column the generator can make.
        check("S1 the target is narrower than the narrowest column", RiftEntry.TARGET_WIDTH < RiftShape.COLUMN_MIN_W, "w=" + RiftEntry.TARGET_WIDTH);
        check("S2 the target is shorter than the shortest column", RiftEntry.TARGET_HEIGHT < RiftShape.COLUMN_MIN_H, "h=" + RiftEntry.TARGET_HEIGHT);
        check("S3 the target is not tiny (at least 2 wide and 8 tall, or it is hard to hit)", RiftEntry.TARGET_WIDTH >= 2.0F && RiftEntry.TARGET_HEIGHT >= 8.0F, "");
        check("S4 the target is far smaller than the whole box (a box-sized target would accept air beside the tear)",
                RiftEntry.TARGET_WIDTH < RiftShape.BOX_W / 3.0 && RiftEntry.TARGET_HEIGHT < RiftShape.BOX_H, "");

        // F: it really fits on the tear, for every seed, in both orientations.
        int bad = 0, firstBad = -1, n = 0;
        for (int seed = 0; seed < 400; seed++) {
            for (boolean horiz : new boolean[] {false, true}) {
                RiftShape.Shape s = RiftShape.generate(seed, horiz);
                n++;
                if (!horiz && !fits(s, wCells, hCells)) { bad++; if (firstBad < 0) firstBad = seed; }
            }
        }
        check("F1 for 400 seeds a target-sized rectangle fits entirely on body cells (a vertical Rift)", bad == 0, "fails=" + bad + " first seed=" + firstBad + " of " + n + " shapes");

        // F2: the check itself can fail. A rectangle taller than the whole box can never fit, so fits() must say no.
        RiftShape.Shape probe = RiftShape.generate(1, false);
        check("F2 control: a rectangle taller than the box never fits (so F1 can fail)", !fits(probe, 1, RiftShape.BOX_H + 1), "");
        check("F3 control: a 1 by 1 rectangle fits (so F1 is not vacuous)", fits(probe, 1, 1), "");

        // B: base position.
        check("B1 the target stands on the bottom edge of the box", RiftEntry.targetBaseY(100.0) == 100.0 - RiftShape.BOX_H / 2.0, "got " + RiftEntry.targetBaseY(100.0));
        check("B2 the target's top is still inside the box", RiftEntry.targetBaseY(100.0) + RiftEntry.TARGET_HEIGHT < 100.0 + RiftShape.BOX_H / 2.0, "");

        // M: matching a leftover target to a live Rift.
        check("M1 a target at the anchor belongs to it", RiftEntry.belongsTo(0.0), "");
        check("M2 a target at the edge of the match radius belongs to it", RiftEntry.belongsTo(RiftEntry.MATCH_RADIUS), "");
        check("M3 a target just past the radius does not", !RiftEntry.belongsTo(RiftEntry.MATCH_RADIUS + 0.01), "");
        check("M4 a negative distance is nonsense and does not belong", !RiftEntry.belongsTo(-1.0), "");
        check("M5 the radius is no larger than the Rift spacing (a target must never match a NEIGHBOURING Rift)", RiftEntry.MATCH_RADIUS < 48.0 / 2, "r=" + RiftEntry.MATCH_RADIUS);


        // R: reach is measured to the nearest point of the column, not its middle.
        double base = 100.0;
        double mid = base + RiftEntry.TARGET_HEIGHT / 2.0;
        check("R1 standing at the foot, beside the column, the distance is the horizontal gap only", Math.abs(RiftEntry.reachDistance(3.0, 0.0, base, base) - 3.0) < 1e-9, "got " + RiftEntry.reachDistance(3.0, 0.0, base, base));
        check("R2 level with the middle it is still the horizontal gap (not zero, not the slant)", Math.abs(RiftEntry.reachDistance(3.0, 4.0, mid, base) - 5.0) < 1e-9, "got " + RiftEntry.reachDistance(3.0, 4.0, mid, base));
        check("R3 a player at the foot, 3 blocks out, is within the gate's reach of 4 (distance to the middle would be over 4)",
                RiftEntry.reachDistance(3.0, 0.0, base, base) <= com.solme.emberfall.rift.RiftRules.REACH && Math.hypot(3.0, mid - base) > com.solme.emberfall.rift.RiftRules.REACH, "");
        check("R4 below the base the gap below counts", Math.abs(RiftEntry.reachDistance(0.0, 0.0, base - 2.0, base) - 2.0) < 1e-9, "");
        check("R5 above the top the gap above counts", Math.abs(RiftEntry.reachDistance(0.0, 0.0, base + RiftEntry.TARGET_HEIGHT + 1.5, base) - 1.5) < 1e-9, "");
        check("R6 distance is never negative and is symmetric in dx and dz sign", RiftEntry.reachDistance(-3.0, -4.0, mid, base) == RiftEntry.reachDistance(3.0, 4.0, mid, base) && RiftEntry.reachDistance(-3.0, 0.0, base, base) >= 0.0, "");

        // H: which clicks are honoured, and the refusal text agrees with it for all four combinations.
        check("H1 an open Rift honours the click", RiftEntry.honoured(true, true), "");
        check("H2 a Rift still opening does not", !RiftEntry.honoured(true, false), "");
        check("H3 no Rift behind the target does not (a leftover target)", !RiftEntry.honoured(false, true) && !RiftEntry.honoured(false, false), "");
        boolean agree = true;
        for (boolean ex : new boolean[] {false, true}) for (boolean op : new boolean[] {false, true}) {
            if ((RiftEntry.refusal(ex, op) == null) != RiftEntry.honoured(ex, op)) agree = false;
        }
        check("H4 refusal() is null exactly when honoured() is true, for all four combinations", agree, "");
        check("H5 a still-opening Rift says so", "the Rift is still opening".equals(RiftEntry.refusal(true, false)), "got " + RiftEntry.refusal(true, false));
        check("H6 a missing Rift says it faded", "the Rift has faded".equals(RiftEntry.refusal(false, true)), "got " + RiftEntry.refusal(false, true));
        check("H7 the tag is stable and namespaced", "emberfall_rift_target".equals(RiftEntry.TAG), "");

        System.out.println(fails == 0 ? "ALL PASS (" + total + " checks)" : fails + " of " + total + " FAILED");
        System.exit(fails == 0 ? 0 : 1);
    }
}
