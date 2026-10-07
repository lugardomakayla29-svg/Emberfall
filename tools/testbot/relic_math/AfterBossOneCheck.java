import com.solme.emberfall.wave.AfterBossOne;
import com.solme.emberfall.world.PartyScaling;

/** V2: what changes once the first boss is dead. Pure numbers, no server. */
public class AfterBossOneCheck {
    static int fails = 0;
    static void check(String l, boolean ok, String e) { System.out.println((ok ? "PASS " : "FAIL ") + l + "  " + e); if (!ok) fails++; }

    // The director's own numbers, copied from WaveDirector (BASE 100, MIN 15, THREAT_CAP 20): the solo interval at a given threat.
    static final int BASE = 100, MIN = 15; static final double CAP = 20.0;
    static int solo(double threat) { return (int) Math.round(BASE - (threat / CAP) * (BASE - MIN)); }

    public static void main(String[] a) {
        // tier 1 (before any boss) is untouched
        boolean same = true; for (int iv = 1; iv <= 200; iv++) if (AfterBossOne.spawnIntervalTicks(iv, MIN, 1) != iv) same = false;
        check("before boss 1 (tier 1) the interval is returned unchanged, for every interval 1..200", same, "");
        check("tier 0 or a negative tier is also 'before boss 1': unchanged", AfterBossOne.spawnIntervalTicks(58, MIN, 0) == 58 && AfterBossOne.spawnIntervalTicks(58, MIN, -3) == 58, "");
        check("the first boss's death is exactly tier 2: active(1) false, active(2) true, active(3) true", !AfterBossOne.active(1) && AfterBossOne.active(2) && AfterBossOne.active(3), "");

        // Koda's DONE-WHEN: the spawn interval after boss 1 is lower than before it
        int before = AfterBossOne.spawnIntervalTicks(solo(10), MIN, 1), after = AfterBossOne.spawnIntervalTicks(solo(10), MIN, 2);
        check("V2 at the moment the Hydra falls (threat 10) the spawn interval drops from " + before + " to " + after + " ticks", after < before && before == 58 && after == 41, before + " -> " + after);
        check("V2 that is about 1.4x as many spawns a minute (58 ticks -> 41 ticks)", Math.abs((1200.0 / after) / (1200.0 / before) - 1.4146) < 0.01, String.format("%.3fx", (1200.0 / after) / (1200.0 / before)));

        // the table across the run
        StringBuilder tbl = new StringBuilder();
        for (int th : new int[]{0, 5, 10, 15, 20}) tbl.append("threat ").append(th).append(": ").append(solo(th)).append("->").append(AfterBossOne.spawnIntervalTicks(solo(th), MIN, 2)).append("  ");
        System.out.println("TABLE solo interval before->after (ticks): " + tbl.toString().trim());

        // safety
        boolean notSlower = true, floor = true, notRaised = true;
        for (int iv = 0; iv <= 300; iv++) {
            int out = AfterBossOne.spawnIntervalTicks(iv, MIN, 2);
            if (out > iv) { notSlower = false; notRaised = false; }
            if (iv >= MIN && out < MIN) floor = false;
            if (iv < MIN && out != iv) floor = false;
        }
        check("after boss 1 the interval is never LONGER than before, for every interval 0..300", notSlower, "");
        check("it never goes under the floor of " + MIN + " ticks when it started at or above it", floor, "");
        check("an interval already under the floor is returned as it was (a faster horde never raises it)", AfterBossOne.spawnIntervalTicks(10, MIN, 2) == 10 && AfterBossOne.spawnIntervalTicks(15, MIN, 2) == 15 && AfterBossOne.spawnIntervalTicks(0, MIN, 2) == 0, "");
        check("at max threat the interval is already at the floor, so boss 1 changes nothing there (honest limit)", AfterBossOne.spawnIntervalTicks(solo(20), MIN, 2) == solo(20) && solo(20) == MIN, "");

        // every threat 0..20 in halves: tier 2 is never slower, and strictly faster until the floor bites
        boolean mono = true, strict = true; for (int h = 0; h <= 40; h++) { double th = h / 2.0; int b = AfterBossOne.spawnIntervalTicks(solo(th), MIN, 1), c = AfterBossOne.spawnIntervalTicks(solo(th), MIN, 2); if (c > b) mono = false; if (solo(th) >= 22 && c >= b) strict = false; }
        check("at every threat 0..20 tier 2 is never slower than tier 1", mono, "");
        check("and strictly faster at every threat where the interval is 22 ticks or more (above where the floor bites)", strict, "");

        // stacking with PartyScaling: the factor goes in FRONT, the party maths stacks on top, the floor still holds for every party
        boolean stack = true, partyNotSlower = true; int lowest = 999;
        for (int h = 0; h <= 40; h++) for (int n = 1; n <= 10; n++) {
            double th = h / 2.0; int s = solo(th);
            int t1 = PartyScaling.spawnIntervalTicks(AfterBossOne.spawnIntervalTicks(s, MIN, 1), MIN, n);
            int t2 = PartyScaling.spawnIntervalTicks(AfterBossOne.spawnIntervalTicks(s, MIN, 2), MIN, n);
            lowest = Math.min(lowest, t2);
            if (t2 < MIN && s >= MIN) stack = false;
            if (t2 > t1) partyNotSlower = false;
        }
        check("with PartyScaling stacked on top, no party of 1..10 at any threat ever gets under the " + MIN + "-tick floor", stack, "lowest " + lowest);
        check("and tier 2 is never slower than tier 1 for any party size", partyNotSlower, "");
        check("tier 1 with the new class in front equals the old director maths exactly (a run that has not killed a boss is unchanged)",
                java.util.stream.IntStream.rangeClosed(0, 40).allMatch(h -> java.util.stream.IntStream.rangeClosed(1, 10).allMatch(n ->
                        PartyScaling.spawnIntervalTicks(AfterBossOne.spawnIntervalTicks(solo(h / 2.0), MIN, 1), MIN, n) == PartyScaling.spawnIntervalTicks(solo(h / 2.0), MIN, n))), "");
        check("10 players: the interval is already at the floor at threat 10 in BOTH tiers, so a full party sees no change in spawn rate (honest limit)",
                PartyScaling.spawnIntervalTicks(AfterBossOne.spawnIntervalTicks(solo(10), MIN, 1), MIN, 10) == MIN && PartyScaling.spawnIntervalTicks(AfterBossOne.spawnIntervalTicks(solo(10), MIN, 2), MIN, 10) == MIN, "");
        check("2 players at threat 10 DO speed up: " + PartyScaling.spawnIntervalTicks(58, MIN, 2) + " -> " + PartyScaling.spawnIntervalTicks(41, MIN, 2) + " ticks",
                PartyScaling.spawnIntervalTicks(41, MIN, 2) < PartyScaling.spawnIntervalTicks(58, MIN, 2), "");

        // the words and the log tag
        check("the warning is a non-empty line that says WARNING and is one line (no newline)", AfterBossOne.WARNING.contains("WARNING") && !AfterBossOne.WARNING.contains("\n") && AfterBossOne.WARNING.length() > 20, AfterBossOne.WARNING.replaceAll("§.", ""));
        check("the warning tells the player both things that changed: more of them, and the strong ones more often", AfterBossOne.WARNING.contains("More of them") && AfterBossOne.WARNING.contains("strong ones"), "");
        check("the log tag is AFTERBOSS1 (the live test greps for it)", AfterBossOne.LOG_TAG.equals("AFTERBOSS1"), "");
        check("the factor is 0.7", AfterBossOne.INTERVAL_FACTOR == 0.7, "");
        System.out.println(fails == 0 ? "ALL PASS" : "FAILED " + fails);
    }
}
