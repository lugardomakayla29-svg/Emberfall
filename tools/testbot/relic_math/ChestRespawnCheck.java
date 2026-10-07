import com.solme.emberfall.relic.ChestOpening;
import com.solme.emberfall.relic.ChestRespawnRule;
import com.solme.emberfall.relic.FreeChestRule;

/** V3: a looted chest has a small capped chance to come back, once per run. Pure numbers, no server. */
public class ChestRespawnCheck {
    static int fails = 0;
    static void check(String l, boolean ok, String e) { System.out.println((ok ? "PASS " : "FAIL ") + l + "  " + e); if (!ok) fails++; }
    static final ChestOpening.Kind PAID = ChestOpening.Kind.PAID, GOLD = ChestOpening.Kind.GOLD, FREE = ChestOpening.Kind.FREE;

    public static void main(String[] a) {
        check("the chance is 10% and the cap is 1 respawn per run", ChestRespawnRule.CHANCE == 0.10 && ChestRespawnRule.PER_RUN_CAP == 1, "");
        check("a PAID chest rolls 10% while the run still has its respawn", ChestRespawnRule.chance(PAID, 0) == 0.10, "");
        check("a GOLD chest rolls 10% too", ChestRespawnRule.chance(GOLD, 0) == 0.10, "");
        check("a FREE chest NEVER comes back (it would skip FreeChestRule and its cap of " + FreeChestRule.RUN_CAP + ")", ChestRespawnRule.chance(FREE, 0) == 0.0 && !ChestRespawnRule.eligible(FREE), "");

        // the cap: once the run used its respawn, the chance is 0 for every kind and every count after
        boolean capped = true; for (int used = ChestRespawnRule.PER_RUN_CAP; used <= 50; used++) for (var k : ChestOpening.Kind.values()) if (ChestRespawnRule.chance(k, used) != 0.0) capped = false;
        check("once the run has used its respawn the chance is 0 for every kind (counts 1..50)", capped, "");
        boolean rollNever = true; for (var k : ChestOpening.Kind.values()) for (double r = 0.0; r < 1.0; r += 0.001) if (ChestRespawnRule.respawns(k, 1, r)) rollNever = false;
        check("and NO roll from 0.000 to 0.999 brings a chest back after the cap", rollNever, "");

        // the roll edges: roll < chance
        check("roll 0.0 respawns, roll 0.0999 respawns, roll 0.10 does not (strictly below the chance), roll 0.99 does not",
                ChestRespawnRule.respawns(PAID, 0, 0.0) && ChestRespawnRule.respawns(PAID, 0, 0.0999) && !ChestRespawnRule.respawns(PAID, 0, 0.10) && !ChestRespawnRule.respawns(PAID, 0, 0.99), "");
        check("a free chest does not respawn even on roll 0.0", !ChestRespawnRule.respawns(FREE, 0, 0.0), "");
        check("a negative or absurd used-count is treated sanely (negative still rolls, huge does not)", ChestRespawnRule.chance(PAID, -5) == 0.10 && ChestRespawnRule.chance(PAID, Integer.MAX_VALUE) == 0.0, "");

        // simulate whole runs: open N eligible chests in a row, each rolls until the first hit, then never again
        java.util.Random rng = new java.util.Random(20261007L);
        int runs = 200000;
        for (int opened : new int[]{3, 8, 16}) {
            int withOne = 0, withMoreThanOne = 0;
            for (int r = 0; r < runs; r++) {
                int used = 0;
                for (int i = 0; i < opened; i++) if (ChestRespawnRule.respawns(i % 7 == 6 ? GOLD : PAID, used, rng.nextDouble())) used++;
                if (used >= 1) withOne++;
                if (used > 1) withMoreThanOne++;
            }
            double expect = 1 - Math.pow(0.9, opened), got = withOne / (double) runs;
            check("simulated " + runs + " runs opening " + opened + " chests: NO run ever gets a second respawn", withMoreThanOne == 0, "runs with >1 = " + withMoreThanOne);
            check("   and the share of runs that see their respawn matches 1-0.9^" + opened + " = " + String.format("%.3f", expect) + " (within 0.01)", Math.abs(got - expect) < 0.01, String.format("measured %.3f", got));
        }
        check("the javadoc's claims: 27% at 3 openings and 57% at 8 openings are right to the nearest percent",
                Math.round((1 - Math.pow(0.9, 3)) * 100) == 27 && Math.round((1 - Math.pow(0.9, 8)) * 100) == 57, String.format("%.4f %.4f", 1 - Math.pow(0.9, 3), 1 - Math.pow(0.9, 8)));
        System.out.println(fails == 0 ? "ALL PASS" : "FAILED " + fails);
    }
}
