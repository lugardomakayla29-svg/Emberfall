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
        check("every step is exactly 0.1x up to the cap (slow)", oneStep, "");
        check("the cap is 5.0x and it is reached at step 49", FinalSwarm.multiplierAtStep(49) == 5.0 && FinalSwarm.multiplierAtStep(48) == 4.9, "");
        check("nothing passes the cap, however long the run goes", FinalSwarm.multiplierAtStep(1_000_000) == 5.0, "");
        check("step from seconds: 0 s step 0, 19 s step 0, 20 s step 1, 980 s step 49", FinalSwarm.stepsAfter(0) == 0 && FinalSwarm.stepsAfter(19) == 0 && FinalSwarm.stepsAfter(20) == 1 && FinalSwarm.stepsAfter(980) == 49, "");
        check("atCap flips exactly at 980 s", !FinalSwarm.atCap(979) && FinalSwarm.atCap(980), "");
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
