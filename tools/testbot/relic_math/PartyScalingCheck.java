import com.solme.emberfall.world.PartyScaling;

/**
 * Pure maths for party scaling (issue #13, plan section 4, step 2). No server, no game types. Every quantity must equal
 * today's value for one player, so wiring this in later cannot change a solo run. The coefficients are PLACEHOLDERS from
 * the plan (not tuned); this check pins the structure and the edges, not that the numbers are good game balance.
 */
public class PartyScalingCheck {
    static int fails = 0;
    static int ran = 0;
    static void check(String label, boolean ok, String extra) {
        ran++;
        System.out.println((ok ? "PASS " : "FAIL ") + label + "  " + extra);
        if (!ok) fails++;
    }
    static boolean near(double a, double b) { return Math.abs(a - b) < 1e-9; }
    /** WaveDirector.HOSTILE_CAP today. A test INPUT only: PartyScaling no longer holds a copy, the director passes its own. */
    static final int SOLO_CAP = 40;

    static boolean soloIdentity() {
        for (int v = 0; v <= 300; v++) if (PartyScaling.spawnIntervalTicks(v, 15, 1) != v) return false;
        return true;
    }

    public static void main(String[] a) {
        // ---- solo is today's game, exactly
        check("MAX_PARTY is 10 (owner's issue #6)", PartyScaling.MAX_PARTY == 10, "" + PartyScaling.MAX_PARTY);
        check("n=1: mob health x1.0", near(PartyScaling.mobHealthMultiplier(1), 1.0), "" + PartyScaling.mobHealthMultiplier(1));
        check("n=1: boss health x1.0", near(PartyScaling.bossHealthMultiplier(1), 1.0), "" + PartyScaling.bossHealthMultiplier(1));
        check("n=1: spawn interval unchanged", PartyScaling.spawnIntervalTicks(100, 15, 1) == 100 && PartyScaling.spawnIntervalTicks(15, 15, 1) == 15, "100->" + PartyScaling.spawnIntervalTicks(100, 15, 1) + " 15->" + PartyScaling.spawnIntervalTicks(15, 15, 1));
        check("n=1: hostile cap is today's 40", PartyScaling.hostileCap(SOLO_CAP, 1) == 40, "" + PartyScaling.hostileCap(SOLO_CAP, 1));

        // ---- the plan's table (n = 2 and 4), exact
        check("n=2: mob health x1.5", near(PartyScaling.mobHealthMultiplier(2), 1.5), "" + PartyScaling.mobHealthMultiplier(2));
        check("n=4: mob health x2.5", near(PartyScaling.mobHealthMultiplier(4), 2.5), "" + PartyScaling.mobHealthMultiplier(4));
        check("n=2: boss health x1.75", near(PartyScaling.bossHealthMultiplier(2), 1.75), "" + PartyScaling.bossHealthMultiplier(2));
        check("n=4: boss health x3.25", near(PartyScaling.bossHealthMultiplier(4), 3.25), "" + PartyScaling.bossHealthMultiplier(4));
        check("n=2: interval 100 -> 63 (100 / 1.6 = 62.5, rounded)", PartyScaling.spawnIntervalTicks(100, 15, 2) == 63, "" + PartyScaling.spawnIntervalTicks(100, 15, 2));
        check("n=4: interval 100 -> 36 (100 / 2.8 = 35.7, rounded)", PartyScaling.spawnIntervalTicks(100, 15, 4) == 36, "" + PartyScaling.spawnIntervalTicks(100, 15, 4));
        check("n=2: hostile cap 50", PartyScaling.hostileCap(SOLO_CAP, 2) == 50, "" + PartyScaling.hostileCap(SOLO_CAP, 2));
        check("n=4: hostile cap 70", PartyScaling.hostileCap(SOLO_CAP, 4) == 70, "" + PartyScaling.hostileCap(SOLO_CAP, 4));

        // ---- the cap is frozen at its n=5 value above 5 (plan section 4: no MSPT measurement exists yet)
        check("n=5: hostile cap 80", PartyScaling.hostileCap(SOLO_CAP, 5) == 80, "" + PartyScaling.hostileCap(SOLO_CAP, 5));
        boolean frozen = true; StringBuilder fz = new StringBuilder();
        for (int n = 6; n <= 10; n++) { int c = PartyScaling.hostileCap(SOLO_CAP, n); fz.append(n).append("=").append(c).append(" "); if (c != 80) frozen = false; }
        check("n=6..10: hostile cap stays at 80", frozen, fz.toString());

        // ---- n = 1..10: every quantity is defined, finite, and never goes down as the party grows
        boolean mono = true, finite = true, floorOk = true; StringBuilder tb = new StringBuilder();
        double pm = 0, pb = 0; int pi = Integer.MAX_VALUE, pc = 0;
        for (int n = 1; n <= PartyScaling.MAX_PARTY; n++) {
            double m = PartyScaling.mobHealthMultiplier(n), b = PartyScaling.bossHealthMultiplier(n);
            int iv = PartyScaling.spawnIntervalTicks(100, 15, n), cap = PartyScaling.hostileCap(SOLO_CAP, n);
            if (Double.isNaN(m) || Double.isInfinite(m) || Double.isNaN(b) || Double.isInfinite(b)) finite = false;
            if (m < pm || b < pb || iv > pi || cap < pc) mono = false;
            if (iv < 1) floorOk = false;
            pm = m; pb = b; pi = iv; pc = cap;
            tb.append(String.format("n=%d mob=%.2f boss=%.2f int=%d cap=%d | ", n, m, b, iv, cap));
        }
        check("n=1..10: all values finite", finite, "");
        check("n=1..10: health and cap never fall, interval never rises", mono, tb.toString());
        check("n=1..10: interval stays at least 1 tick", floorOk, "");
        check("a solo interval already below the floor is returned unchanged (n=1) and not raised (n=5)", PartyScaling.spawnIntervalTicks(10, 15, 1) == 10 && PartyScaling.spawnIntervalTicks(10, 15, 5) == 10, PartyScaling.spawnIntervalTicks(10, 15, 1) + " " + PartyScaling.spawnIntervalTicks(10, 15, 5));
        check("a mid-range solo interval is divided but stops at the floor: 40 -> n=10 gives 15", PartyScaling.spawnIntervalTicks(40, 15, 10) == 15, "" + PartyScaling.spawnIntervalTicks(40, 15, 10));

        // ---- the interval keeps the director's own floor: a party must not push it under MIN_SPAWN_INTERVAL_TICKS (15)
        check("n=10 at the floor: interval 15 stays 15 (a party never goes under the director's minimum)", PartyScaling.spawnIntervalTicks(15, 15, 10) == 15, "" + PartyScaling.spawnIntervalTicks(15, 15, 10));

        // ---- the solo cap is an argument (Koda, #13): the class must follow it, not carry its own copy of 40
        check("hostile cap follows the solo cap it is given: 50 solo -> 50 at n=1, 90 at n=5", PartyScaling.hostileCap(50, 1) == 50 && PartyScaling.hostileCap(50, 5) == 90, PartyScaling.hostileCap(50, 1) + " " + PartyScaling.hostileCap(50, 5));
        check("hostile cap: a different solo cap moves every n (30 -> 30, 40, 70)", PartyScaling.hostileCap(30, 1) == 30 && PartyScaling.hostileCap(30, 2) == 40 && PartyScaling.hostileCap(30, 10) == 70, PartyScaling.hostileCap(30, 1) + " " + PartyScaling.hostileCap(30, 2) + " " + PartyScaling.hostileCap(30, 10));

        // ---- a solo interval at or below the floor is returned unchanged for ANY n, including 0 and below (replaces the old size==1 branch)
        check("solo interval 0 is returned unchanged at n=1, 2, 5, 10 (and -5 likewise)", PartyScaling.spawnIntervalTicks(0, 15, 1) == 0 && PartyScaling.spawnIntervalTicks(0, 15, 2) == 0 && PartyScaling.spawnIntervalTicks(0, 15, 5) == 0 && PartyScaling.spawnIntervalTicks(0, 15, 10) == 0 && PartyScaling.spawnIntervalTicks(-5, 15, 3) == -5, PartyScaling.spawnIntervalTicks(0, 15, 1) + " " + PartyScaling.spawnIntervalTicks(0, 15, 2) + " " + PartyScaling.spawnIntervalTicks(-5, 15, 3));
        check("n=1 returns the input for every interval 0..300 (nothing is scaled for a solo run)", soloIdentity(), "");

        // ---- bad input is clamped, not an exception or a negative multiplier
        check("n=0 reads as 1", near(PartyScaling.mobHealthMultiplier(0), 1.0) && PartyScaling.hostileCap(SOLO_CAP, 0) == 40, "" + PartyScaling.mobHealthMultiplier(0));
        check("n=-3 reads as 1", near(PartyScaling.bossHealthMultiplier(-3), 1.0) && PartyScaling.spawnIntervalTicks(100, 15, -3) == 100, "" + PartyScaling.bossHealthMultiplier(-3));
        check("n=11 reads as 10 (above MAX_PARTY)", near(PartyScaling.mobHealthMultiplier(11), PartyScaling.mobHealthMultiplier(10)) && PartyScaling.hostileCap(SOLO_CAP, 11) == PartyScaling.hostileCap(SOLO_CAP, 10), "" + PartyScaling.mobHealthMultiplier(11));
        check("n=100000 reads as 10", near(PartyScaling.bossHealthMultiplier(100000), PartyScaling.bossHealthMultiplier(10)), "");
        check("n=Integer.MAX_VALUE does not overflow", near(PartyScaling.mobHealthMultiplier(Integer.MAX_VALUE), PartyScaling.mobHealthMultiplier(10)), "");

        // ---- the run's party size is fixed at start (option b), so the maths is a pure function of n
        check("same input, same output", near(PartyScaling.mobHealthMultiplier(3), PartyScaling.mobHealthMultiplier(3)) && PartyScaling.hostileCap(SOLO_CAP, 3) == PartyScaling.hostileCap(SOLO_CAP, 3), "");

        // a run that executes fewer checks than this did not finish (a missing class would also fail to compile)
        final int EXPECTED_CHECKS = 31;
        System.out.println(fails == 0 && ran >= EXPECTED_CHECKS ? "RESULT: ALL PASS (" + ran + " checks)" : "RESULT: " + (fails == 0 ? 1 : fails) + " FAILED" + (ran < EXPECTED_CHECKS ? " (only " + ran + " of " + EXPECTED_CHECKS + " checks ran)" : ""));
        System.exit(fails == 0 && ran >= EXPECTED_CHECKS ? 0 : 1);
    }
}
