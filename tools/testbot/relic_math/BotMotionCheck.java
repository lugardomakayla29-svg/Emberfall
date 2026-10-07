import com.solme.emberfall.bot.BotMotion;
import com.solme.emberfall.bot.BotMotion.Strafe;
import java.util.*;

/** Pure proof of BotMotion: the turn limit that removes the jitter, the jump arc, the strafe rhythm, the S-tap, the reaction lag. */
public class BotMotionCheck {
    static int fails = 0;
    static void check(String l, boolean ok, String e) { System.out.println((ok ? "PASS " : "FAIL ") + l + "  " + e); if (!ok) fails++; }
    static boolean near(double a, double b, double eps) { return Math.abs(a - b) < eps; }

    public static void main(String[] args) {
        // ---- angles ----
        check("350 to 10 degrees is a +20 turn, not -340", near(BotMotion.angleDelta(350, 10), 20, 1e-9), "" + BotMotion.angleDelta(350, 10));
        check("10 to 350 degrees is a -20 turn", near(BotMotion.angleDelta(10, 350), -20, 1e-9), "" + BotMotion.angleDelta(10, 350));
        check("exactly opposite (0 to 180) is +180, a defined answer", near(BotMotion.angleDelta(0, 180), 180, 1e-9), "" + BotMotion.angleDelta(0, 180));
        check("angleDelta stays in (-180, 180] over 3600 random pairs", rangeOk(), "");
        check("turnToward never moves more than maxTurn", near(BotMotion.turnToward(0, 90, 10), 10, 1e-9), "" + BotMotion.turnToward(0, 90, 10));
        check("turnToward lands exactly on the target when within reach (no overshoot)", near(BotMotion.turnToward(0, 7, 10), 7, 1e-9), "");
        check("turnToward takes the short way round the 360 seam", near(BotMotion.turnToward(355, 5, 4), 359, 1e-9), "" + BotMotion.turnToward(355, 5, 4));

        // THE JITTER: the old code set yaw straight to the step bearing. A route that zig-zags (re-plan flips) gave big per-tick changes.
        // Feed the same flip-flopping target to the old rule and the new one and compare the largest per-tick change.
        double[] targets = new double[200];
        for (int i = 0; i < 200; i++) targets[i] = (i / 5) % 2 == 0 ? 20 : 160; // flips 140 degrees every 5 ticks
        double oldMax = 0, newMax = 0, prevOld = targets[0], cur = targets[0];
        for (int i = 1; i < 200; i++) {
            oldMax = Math.max(oldMax, Math.abs(BotMotion.angleDelta(prevOld, targets[i]))); prevOld = targets[i];
            double rate = BotMotion.turnRate(BotMotion.angleDelta(cur, targets[i]), 4.0, 24.0);
            double next = BotMotion.turnToward(cur, targets[i], rate);
            newMax = Math.max(newMax, Math.abs(BotMotion.angleDelta(cur, next))); cur = next;
        }
        check("JITTER baseline: the old snap-to-bearing rule turns up to 140 degrees in ONE tick", oldMax >= 139, String.format("%.0f", oldMax));
        check("JITTER fix: the limited turn never exceeds 24 degrees in one tick", newMax <= 24.0 + 1e-9, String.format("%.1f", newMax));
        check("the turn rate is gentler near the target than far from it", BotMotion.turnRate(5, 4, 24) < BotMotion.turnRate(120, 4, 24), BotMotion.turnRate(5, 4, 24) + " vs " + BotMotion.turnRate(120, 4, 24));
        check("turn rate at 45 degrees left is halfway between min and max: 4 + (24-4) * 45/90 = 14", Math.abs(BotMotion.turnRate(45, 4, 24) - 14.0) < 1e-9 && Math.abs(BotMotion.turnRate(90, 4, 24) - 24.0) < 1e-9 && Math.abs(BotMotion.turnRate(0, 4, 24) - 4.0) < 1e-9, "" + BotMotion.turnRate(45, 4, 24));
        // stepStrafe: hold = 8 + (int)(u1 * 22). u1=0 -> 8 ticks, u1=0.999 -> 29 ticks. u2 < share/2 -> side +1, share/2 <= u2 < share -> -1, u2 >= share -> rest.
        BotMotion.Strafe sLo = BotMotion.stepStrafe(new BotMotion.Strafe(0, 1), 0.6, 0.0, 0.1);
        BotMotion.Strafe sHi = BotMotion.stepStrafe(new BotMotion.Strafe(0, 1), 0.6, 0.999, 0.1);
        check("a new strafe lasts 8 ticks at the low end and 29 at the high end (derived from 8 + u1 * 22)", sLo.ticksLeft() == 8 && sHi.ticksLeft() == 29, sLo.ticksLeft() + " and " + sHi.ticksLeft());
        // From a standstill (dir 0) there is no previous strafe to reverse, so u1 does not matter; from dir +1 with u1 < 0.5 the side flips to -1, with u1 >= 0.5 it stays +1.
        BotMotion.Strafe rev = BotMotion.stepStrafe(new BotMotion.Strafe(1, 1), 0.6, 0.3, 0.1);
        BotMotion.Strafe keep = BotMotion.stepStrafe(new BotMotion.Strafe(1, 1), 0.6, 0.7, 0.1);
        BotMotion.Strafe reverseAt20 = BotMotion.stepStrafe(new BotMotion.Strafe(1, 1), 0.6, 0.2, 0.1);
        BotMotion.Strafe reverseAt40 = BotMotion.stepStrafe(new BotMotion.Strafe(1, 1), 0.6, 0.4, 0.1);
        check("a strafe reverses the last one when u1 < 0.5 and keeps its side otherwise (u1 0.2 and 0.4 reverse, 0.7 keeps)", rev.dir() == -1 && keep.dir() == 1 && reverseAt20.dir() == -1 && reverseAt40.dir() == -1, rev.dir() + "," + keep.dir() + "," + reverseAt20.dir() + "," + reverseAt40.dir());
        check("a strafe eases in over 3 ticks: 1/3, 2/3, then full speed", Math.abs(BotMotion.strafeStep(new BotMotion.Strafe(1, 20), 0, 0.3) - 0.1) < 1e-9 && Math.abs(BotMotion.strafeStep(new BotMotion.Strafe(1, 20), 1, 0.3) - 0.2) < 1e-9 && Math.abs(BotMotion.strafeStep(new BotMotion.Strafe(1, 20), 2, 0.3) - 0.3) < 1e-9, BotMotion.strafeStep(new BotMotion.Strafe(1, 20), 0, 0.3) + "");
        check("turn rate is bounded by min and max", BotMotion.turnRate(0, 4, 24) >= 4 - 1e-9 && BotMotion.turnRate(500, 4, 24) <= 24 + 1e-9, "");

        // ---- speed easing ----
        double sp = 0; int ticks = 0;
        while (sp < 0.2 - 1e-9 && ticks < 100) { sp = BotMotion.easeSpeed(sp, 0.2, 0.05); ticks++; }
        check("speed eases from 0 to 0.2 in 4 ticks at accel 0.05, never overshooting", ticks == 4 && near(sp, 0.2, 1e-9), ticks + " ticks, " + sp);
        check("easing down works the same way", near(BotMotion.easeSpeed(0.2, 0.0, 0.05), 0.15, 1e-9), "");

        // ---- jump arc: must match vanilla (peak about 1.25, back down after ~12 ticks) ----
        double peak = 0; int peakTick = 0;
        for (int t = 0; t < 40; t++) if (BotMotion.jumpHeight(t) > peak) { peak = BotMotion.jumpHeight(t); peakTick = t; }
        check("the jump peaks at 1.25 +- 0.02 blocks (vanilla 1.2522)", near(peak, 1.2522, 0.02), String.format("peak %.4f at tick %d", peak, peakTick));
        check("the jump is in the air about 12 ticks (11..13)", BotMotion.jumpTicks() >= 11 && BotMotion.jumpTicks() <= 13, "" + BotMotion.jumpTicks());
        check("the jump starts at 0 and rises on the first ticks", BotMotion.jumpHeight(0) == 0 && BotMotion.jumpHeight(1) > 0.3 && BotMotion.jumpHeight(2) > BotMotion.jumpHeight(1), BotMotion.jumpHeight(1) + "");
        check("the jump is back on the ground afterwards", BotMotion.jumpHeight(BotMotion.jumpTicks() + 3) == 0, "");
        boolean smooth = true; double last = 0;
        for (int t = 1; t < BotMotion.jumpTicks(); t++) { double h = BotMotion.jumpHeight(t); if (Math.abs(h - last) > 0.45) smooth = false; last = h; }
        check("no tick of the jump moves more than 0.45 blocks (no teleport)", smooth, "");

        // ---- strafe rhythm ----
        Random rnd = new Random(7);
        Strafe s = new Strafe(0, 1);
        int flips = 0, left = 0, right = 0, rest = 0, total = 6000, prevDir = 0;
        Set<Integer> holdLengths = new TreeSet<>(); int hold = 0;
        for (int t = 0; t < total; t++) {
            s = BotMotion.stepStrafe(s, 0.6, rnd.nextDouble(), rnd.nextDouble());
            if (s.dir() == 1) right++; else if (s.dir() == -1) left++; else rest++;
            if (s.dir() != prevDir) { if (hold > 0) holdLengths.add(hold); hold = 0; if (prevDir != 0 && s.dir() == -prevDir) flips++; }
            hold++; prevDir = s.dir();
        }
        check("strafing happens about 60% of the time at share 0.6 (50..70%)", (left + right) / (double) total > 0.50 && (left + right) / (double) total < 0.70, String.format("%.2f", (left + right) / (double) total));
        check("it goes both ways, roughly evenly", left > 0.35 * (left + right) && right > 0.35 * (left + right), left + " / " + right);
        check("it reverses direct (left to right) many times, like a juke-strafe", flips > 20, "" + flips);
        check("hold lengths vary (not a fixed beat): at least 8 different lengths", holdLengths.size() >= 8, holdLengths.size() + " lengths");
        Strafe none = new Strafe(0, 1); int strafed = 0;
        for (int t = 0; t < 2000; t++) { none = BotMotion.stepStrafe(none, 0.0, rnd.nextDouble(), rnd.nextDouble()); if (none.dir() != 0) strafed++; }
        check("CONTROL: with share 0 it never strafes", strafed == 0, "" + strafed);
        check("strafe offset is zero when resting and eased in when strafing", BotMotion.strafeStep(new Strafe(0, 5), 0, 0.1) == 0 && BotMotion.strafeStep(new Strafe(1, 5), 0, 0.1) < BotMotion.strafeStep(new Strafe(1, 5), 5, 0.1) && near(BotMotion.strafeStep(new Strafe(-1, 5), 9, 0.1), -0.1, 1e-9), "");

        // ---- S-tap and reaction ----
        check("S-tap is strongest right after a hit and gone after the window", BotMotion.sTap(0, 6) == 1.0 && BotMotion.sTap(3, 6) == 0.5 && BotMotion.sTap(6, 6) == 0.0 && BotMotion.sTap(-1, 6) == 0.0, "");
        check("a bot has not reacted before its reaction time and has after", !BotMotion.hasReacted(5, 8) && BotMotion.hasReacted(8, 8) && BotMotion.hasReacted(20, 8), "");

        System.out.println(fails == 0 ? "ALL PASS" : "FAILED " + fails);
        System.exit(fails == 0 ? 0 : 1);
    }

    static boolean rangeOk() {
        Random r = new Random(3);
        for (int i = 0; i < 3600; i++) { double d = BotMotion.angleDelta(r.nextDouble() * 1000 - 500, r.nextDouble() * 1000 - 500); if (d <= -180 || d > 180) return false; }
        return true;
    }
}
