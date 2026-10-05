import com.solme.emberfall.relic.*;
import java.util.*;
public class OffenceCheck {
    static int fails = 0;
    static void check(String l, boolean ok, String e) { System.out.println((ok ? "PASS " : "FAIL ") + l + "  " + e); if (!ok) fails++; }
    static RelicStats with(Object... kv) { Map<String,Integer> m = new HashMap<>(); for (int i = 0; i < kv.length; i += 2) m.put((String) kv[i], (Integer) kv[i+1]); return new RelicStats(m); }
    static boolean near(double a, double b, double t) { return Math.abs(a - b) <= t; }
    public static void main(String[] x) {
        RelicStats none = with();
        check("no relics: damage unchanged and NO roll consumed", RelicOffence.scaledDamage(none, 10f, 50, () -> { throw new RuntimeException("roll"); }) == 10f, "");
        check("zero damage stays zero", RelicOffence.scaledDamage(with("anvil_of_dawn", 3), 0f, 0, () -> 0.0) == 0f, "");
        check("anvil x3 = +75%", near(RelicOffence.scaledDamage(with("anvil_of_dawn", 3), 10f, 0, () -> 0.99), 17.5, 1e-4), "");
        check("crown = +50%", near(RelicOffence.scaledDamage(with("wither_crown", 1), 10f, 0, () -> 0.99), 15.0, 1e-4), "");
        check("anvil and crown multiply (1.25 x 1.5)", near(RelicOffence.scaledDamage(with("anvil_of_dawn", 1, "wither_crown", 1), 10f, 0, () -> 0.99), 18.75, 1e-4), "");
        check("soul lantern: 100 kills = +20%", near(RelicOffence.scaledDamage(with("soul_lantern", 1), 10f, 100, () -> 0.99), 12.0, 1e-4), "");
        check("soul lantern caps at +100%", near(RelicOffence.scaledDamage(with("soul_lantern", 1), 10f, 100000, () -> 0.99), 20.0, 1e-4), "");
        check("soul lantern does nothing without the relic", near(RelicOffence.scaledDamage(none, 10f, 100000, () -> 0.99), 10.0, 1e-4), "");
        check("big bonk proc = x20", near(RelicOffence.scaledDamage(with("big_bonk", 1), 10f, 0, () -> 0.0), 200.0, 1e-3), "");
        check("big bonk miss = x1", near(RelicOffence.scaledDamage(with("big_bonk", 1), 10f, 0, () -> 0.5), 10.0, 1e-4), "");
        check("bonk stacks with anvil (x20 x1.25)", near(RelicOffence.scaledDamage(with("big_bonk", 1, "anvil_of_dawn", 1), 10f, 0, () -> 0.0), 250.0, 1e-3), "");
        Random r = new Random(3); int N = 500000, b = 0; RelicStats bk = with("big_bonk", 3);
        for (int i = 0; i < N; i++) if (RelicOffence.scaledDamage(bk, 1f, 0, r::nextDouble) > 10f) b++;
        check("big bonk x3 procs 6% of hits", near(100.0 * b / N, 6.0, 0.15), String.format("%.3f%%", 100.0 * b / N));
        check("lifesteal 3 stacks = 9% of damage dealt", near(RelicOffence.lifesteal(with("blood_chalice", 3), 50f), 4.5, 1e-4), "");
        check("lifesteal is zero without the relic or with no damage", RelicOffence.lifesteal(none, 50f) == 0f && RelicOffence.lifesteal(with("blood_chalice", 3), 0f) == 0f, "");
        check("censer: no relic never rolls", !RelicOffence.censerProcs(none, () -> { throw new RuntimeException("roll"); }), "");
        b = 0; for (int i = 0; i < N; i++) if (RelicOffence.censerProcs(with("spiked_censer", 2), r::nextDouble)) b++;
        check("censer x2 procs 20%", near(100.0 * b / N, 20.0, 0.25), String.format("%.3f%%", 100.0 * b / N));
        check("censer blast = 60% of the hit", near(RelicOffence.censerDamage(50f), 30.0, 1e-4) && RelicOffence.censerDamage(0f) == 0f, "");
        check("frost: no relic is NONE without a roll", RelicOffence.frostOutcome(none, false, () -> { throw new RuntimeException("roll"); }) == RelicOffence.Frost.NONE, "");
        check("frost proc on a fresh foe = CHILL", RelicOffence.frostOutcome(with("frostbound_ring", 1), false, () -> 0.0) == RelicOffence.Frost.CHILL, "");
        check("frost proc on a chilled foe = FREEZE", RelicOffence.frostOutcome(with("frostbound_ring", 1), true, () -> 0.0) == RelicOffence.Frost.FREEZE, "");
        check("frost miss = NONE even when chilled", RelicOffence.frostOutcome(with("frostbound_ring", 1), true, () -> 0.9) == RelicOffence.Frost.NONE, "");
        System.out.println(fails == 0 ? "RESULT: ALL PASS" : "RESULT: " + fails + " FAILED"); System.exit(fails == 0 ? 0 : 1);
    }
}
