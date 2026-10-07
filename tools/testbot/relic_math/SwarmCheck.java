import com.solme.emberfall.wave.FinalSwarm;
public class SwarmCheck {
    static int fails = 0;
    static void check(String l, boolean ok, String e) { System.out.println((ok ? "PASS " : "FAIL ") + l + "  " + e); if (!ok) fails++; }
    public static void main(String[] a) {
        check("the swarm begins at 0.1x", FinalSwarm.tenthsAtStep(0) == 1 && FinalSwarm.multiplierAtStep(0) == 0.1, "");
        check("a negative step count cannot go below 0.1x", FinalSwarm.tenthsAtStep(-5) == 1, "");
        check("the first steps are 0.1 0.2 0.3 0.4 0.5", java.util.stream.IntStream.range(0, 5).mapToObj(FinalSwarm::multiplierAtStep).toList().equals(java.util.List.of(0.1, 0.2, 0.3, 0.4, 0.5)), "");
        boolean mono = true; for (int s = 1; s < 80; s++) if (FinalSwarm.tenthsAtStep(s) < FinalSwarm.tenthsAtStep(s - 1)) mono = false;
        check("the multiplier never goes down", mono, "");
        boolean oneStep = true; for (int s = 1; s < FinalSwarm.stepsToCap() + 1; s++) if (FinalSwarm.tenthsAtStep(s) - FinalSwarm.tenthsAtStep(s - 1) != 1) oneStep = false;
        check("every step is exactly 0.1x up to the cap (the SIZE of a step is unchanged; only WHEN it happens accelerates)", oneStep, "");
        check("the cap is 5.0x and it is reached at step 49", FinalSwarm.multiplierAtStep(49) == 5.0 && FinalSwarm.multiplierAtStep(48) == 4.9, "");
        check("nothing passes the cap, however long the run goes", FinalSwarm.multiplierAtStep(1_000_000) == 5.0, "");
        check("step from seconds: 0 s step 0, 42 s step 0, 43 s step 1 (the first step comes late: an ease-in), 300 s step 49",
                FinalSwarm.stepsAfter(0) == 0 && FinalSwarm.stepsAfter(42) == 0 && FinalSwarm.stepsAfter(43) == 1 && FinalSwarm.stepsAfter(300) == 49,
                FinalSwarm.stepsAfter(42) + " " + FinalSwarm.stepsAfter(43) + " " + FinalSwarm.stepsAfter(300));
        check("atCap flips exactly at " + FinalSwarm.RAMP_SECONDS + " s (299 s is 4.9x, 300 s is 5.0x)", !FinalSwarm.atCap(299) && FinalSwarm.atCap(300) && FinalSwarm.multiplierAtStep(FinalSwarm.stepsAfter(299)) == 4.9, "");
        // ---- V1: the ramp. Koda's DONE-WHEN: print the multiplier at 60/120/180/240/300 s and show 5.0x by 300 s.
        int[] marks = {60, 120, 180, 240, 300};
        StringBuilder tbl = new StringBuilder();
        for (int sec : marks) tbl.append(sec).append("s=").append(String.format("%.1f", FinalSwarm.multiplierAtStep(FinalSwarm.stepsAfter(sec)))).append("x  ");
        System.out.println("TABLE multiplier by time: " + tbl.toString().trim());
        check("V1 the multiplier is 5.0x by 300 s (the old linear ramp needed 980 s)", FinalSwarm.multiplierAtStep(FinalSwarm.stepsAfter(300)) == 5.0, tbl.toString().trim());
        check("V1 and it is NOT there early: 299 s is below 5.0x, and 240 s is still below 4.0x", FinalSwarm.multiplierAtStep(FinalSwarm.stepsAfter(299)) < 5.0 && FinalSwarm.multiplierAtStep(FinalSwarm.stepsAfter(240)) < 4.0, "");
        check("V1 the exact table: 60 s 0.2x, 120 s 0.8x, 180 s 1.8x, 240 s 3.2x, 300 s 5.0x",
                FinalSwarm.tenthsAtStep(FinalSwarm.stepsAfter(60)) == 2 && FinalSwarm.tenthsAtStep(FinalSwarm.stepsAfter(120)) == 8 && FinalSwarm.tenthsAtStep(FinalSwarm.stepsAfter(180)) == 18
                        && FinalSwarm.tenthsAtStep(FinalSwarm.stepsAfter(240)) == 32 && FinalSwarm.tenthsAtStep(FinalSwarm.stepsAfter(300)) == 50, tbl.toString().trim());
        int g1 = FinalSwarm.stepsAfter(60) - FinalSwarm.stepsAfter(0), g5 = FinalSwarm.stepsAfter(300) - FinalSwarm.stepsAfter(240);
        check("V1 it accelerates: the last minute gains at least 10x more steps than the first minute", g5 >= 10 * g1 && g1 >= 1, g1 + " steps in minute 1, " + g5 + " in minute 5");
        boolean accel = true; int prevGain = -1; for (int m = 0; m < 5; m++) { int gain = FinalSwarm.stepsAfter(60L * (m + 1)) - FinalSwarm.stepsAfter(60L * m); if (gain <= prevGain) accel = false; prevGain = gain; }
        check("V1 every minute gains strictly more steps than the one before (slow start, faster later, never linear)", accel, "");
        boolean monoSec = true, atMostOne = true, allSeen = true, inRange = true; boolean[] seen = new boolean[FinalSwarm.stepsToCap() + 1];
        for (int t = 1; t <= 1200; t++) {
            int st = FinalSwarm.stepsAfter(t), d = st - FinalSwarm.stepsAfter(t - 1);
            if (d < 0) monoSec = false; if (d > 1) atMostOne = false;
            if (st < 0 || st > FinalSwarm.stepsToCap()) inRange = false; else seen[st] = true;   // a bad value is a named FAIL, never a crash
        }
        seen[0] = true; for (boolean b : seen) if (!b) allSeen = false;
        check("V1 stepsAfter never leaves 0..49 for any time up to 20 minutes", inRange, "");
        check("V1 time never makes the multiplier go down", monoSec, "");
        check("V1 no second ever jumps two steps, so no tier is skipped and every announcement still fires", atMostOne, "");
        check("V1 every one of the 50 multipliers from 0.1x to 5.0x is reached at some second", allSeen, "");
        check("V1 nothing passes the cap however long the swarm runs, and a negative time is step 0", FinalSwarm.stepsAfter(10_000_000L) == 49 && FinalSwarm.stepsAfter(Long.MAX_VALUE) == 49 && FinalSwarm.stepsAfter(-50) == 0, "");
        boolean inv = true; for (int n = 0; n <= FinalSwarm.stepsToCap(); n++) { int sec = FinalSwarm.secondsForStep(n); if (FinalSwarm.stepsAfter(sec) < n || (sec > 0 && FinalSwarm.stepsAfter(sec - 1) >= n)) inv = false; }
        check("V1 secondsForStep is the exact inverse: the first second that reaches each step, for all 50 steps", inv, "");
        check("V1 the cap second is RAMP_SECONDS and it is five minutes", FinalSwarm.secondsForStep(FinalSwarm.stepsToCap()) == FinalSwarm.RAMP_SECONDS && FinalSwarm.RAMP_SECONDS == 300, "");
        int[] tierSecs = new int[4]; for (int t = 0; t < FinalSwarm.RAMP_SECONDS; t++) tierSecs[FinalSwarm.tierOf(FinalSwarm.tenthsAtStep(FinalSwarm.stepsAfter(t))).ordinal()]++;
        check("V1 the time before the cap splits Easy 136 s, Medium 56 s, Hard 43 s, Nightmare 65 s: a long calm start, a fast hostile finish", tierSecs[0] == 136 && tierSecs[1] == 56 && tierSecs[2] == 43 && tierSecs[3] == 65, java.util.Arrays.toString(tierSecs));
        // tiers at every boundary
        check("tier boundaries: 1.0 Easy, 1.1 Medium, 2.0 Medium, 2.1 Hard, 3.0 Hard, 3.1 Nightmare, 5.0 Nightmare",
                FinalSwarm.tierOf(10) == FinalSwarm.Tier.EASY && FinalSwarm.tierOf(11) == FinalSwarm.Tier.MEDIUM && FinalSwarm.tierOf(20) == FinalSwarm.Tier.MEDIUM
                        && FinalSwarm.tierOf(21) == FinalSwarm.Tier.HARD && FinalSwarm.tierOf(30) == FinalSwarm.Tier.HARD && FinalSwarm.tierOf(31) == FinalSwarm.Tier.NIGHTMARE && FinalSwarm.tierOf(50) == FinalSwarm.Tier.NIGHTMARE, "");
        int[] count = new int[4]; for (int t = 1; t <= 50; t++) count[FinalSwarm.tierOf(t).ordinal()]++;
        check("the 50 steps split 10 / 10 / 10 / 20 across Easy, Medium, Hard, Nightmare", count[0] == 10 && count[1] == 10 && count[2] == 10 && count[3] == 20, java.util.Arrays.toString(count));
        // crowd and speed
        boolean grow = true, bounded = true; for (int p = 1; p <= 10; p++) { int last = 0; for (int t = 1; t <= 50; t++) { int n = FinalSwarm.targetMobs(t, p); if (n < last) grow = false; if (n > FinalSwarm.MOB_CEILING || n < 1) bounded = false; last = n; } }
        check("the crowd never shrinks as the multiplier rises, for 1 to 10 players", grow, "");
        check("the crowd never exceeds the entity ceiling of " + FinalSwarm.MOB_CEILING + " and is never empty", bounded, "");
        check("solo crowd: 6 at 0.1x, 20 at 5.0x", FinalSwarm.targetMobs(1, 1) == 6 && FinalSwarm.targetMobs(50, 1) == 20, FinalSwarm.targetMobs(1, 1) + " / " + FinalSwarm.targetMobs(50, 1));
        check("a bigger party gets a bigger crowd (same step)", FinalSwarm.targetMobs(25, 4) > FinalSwarm.targetMobs(25, 1), FinalSwarm.targetMobs(25, 1) + " -> " + FinalSwarm.targetMobs(25, 4));
        check("10 players at the cap hit the ceiling, not beyond", FinalSwarm.targetMobs(50, 10) == FinalSwarm.MOB_CEILING, "" + FinalSwarm.targetMobs(50, 10));
        check("0 or negative players is treated as 1", FinalSwarm.targetMobs(10, 0) == FinalSwarm.targetMobs(10, 1) && FinalSwarm.targetMobs(10, -3) == FinalSwarm.targetMobs(10, 1), "");
        check("speed bonus is 0 at the start, 0.45 at the cap, and rises monotonically", FinalSwarm.speedBonus(1) == 0 && Math.abs(FinalSwarm.speedBonus(50) - 0.45) < 1e-9 && FinalSwarm.speedBonus(30) < FinalSwarm.speedBonus(31), "");
        // cash out
        check("scaled: 1000 silver at 0.1x is 100, at 1.0x 1000, at 2.5x 2500, at 5.0x 5000", FinalSwarm.scaled(1000, 1) == 100 && FinalSwarm.scaled(1000, 10) == 1000 && FinalSwarm.scaled(1000, 25) == 2500 && FinalSwarm.scaled(1000, 50) == 5000, "");
        check("scaled rounds to the nearest silver (33 at 0.5x is 17, 7 at 0.1x is 1)", FinalSwarm.scaled(33, 5) == 17 && FinalSwarm.scaled(7, 1) == 1, FinalSwarm.scaled(33, 5) + " " + FinalSwarm.scaled(7, 1));
        check("leaving through the portal never pays less than the plain base (0.1x pays 1000, not 100)", FinalSwarm.cashOut(1000, 1) == 1000 && FinalSwarm.cashOut(1000, 9) == 1000 && FinalSwarm.cashOut(1000, 10) == 1000, FinalSwarm.cashOut(1000, 1) + "");
        check("above 1.0x the multiplier applies (1000 at 1.1x is 1100, at 2.5x 2500, at 5.0x 5000)", FinalSwarm.cashOut(1000, 11) == 1100 && FinalSwarm.cashOut(1000, 25) == 2500 && FinalSwarm.cashOut(1000, 50) == 5000, "");
        check("cash out of nothing or a negative base is 0", FinalSwarm.cashOut(0, 50) == 0 && FinalSwarm.cashOut(-40, 50) == 0, "");
        check("a later exit always pays at least as much as an earlier one", java.util.stream.IntStream.range(1, 50).allMatch(t -> FinalSwarm.cashOut(777, t + 1) >= FinalSwarm.cashOut(777, t)), "");
        check("cash out is never below the base for any base 0..300 and any step", java.util.stream.IntStream.rangeClosed(0, 300).allMatch(b -> java.util.stream.IntStream.rangeClosed(1, 50).allMatch(t -> FinalSwarm.cashOut(b, t) >= b)), "");
        check("wrath applies only at the cap", !FinalSwarm.wrath(49) && FinalSwarm.wrath(50), "");
        System.out.println(fails == 0 ? "ALL PASS" : "FAILED " + fails);
    }
}
