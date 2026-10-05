import com.solme.emberfall.relic.*;
import java.util.*;
public class TempoCheck {
    static int fails = 0;
    static void check(String l, boolean ok, String e) { System.out.println((ok ? "PASS " : "FAIL ") + l + "  " + e); if (!ok) fails++; }
    static RelicStats with(Object... kv) { Map<String,Integer> m = new HashMap<>(); for (int i = 0; i < kv.length; i += 2) m.put((String) kv[i], (Integer) kv[i+1]); return new RelicStats(m); }
    public static void main(String[] x) {
        RelicStats none = with();
        check("no cowl: delay unchanged", RelicTempo.delay(none, 20) == 20 && RelicTempo.delay(none, 7) == 7, "");
        check("cowl x1 = -10% (20 -> 18)", RelicTempo.delay(with("wizards_cowl", 1), 20) == 18, "" + RelicTempo.delay(with("wizards_cowl", 1), 20));
        check("cowl x3 = -30% (20 -> 14)", RelicTempo.delay(with("wizards_cowl", 3), 20) == 14, "");
        check("cowl x5 reaches the -50% cap (20 -> 10)", RelicTempo.delay(with("wizards_cowl", 5), 20) == 10, "");
        check("cowl x20 cannot go past the cap (20 -> 10)", RelicTempo.delay(with("wizards_cowl", 20), 20) == 10, "" + RelicTempo.delay(with("wizards_cowl", 20), 20));
        check("tiny delays keep a floor of 2 (4 -> 2)", RelicTempo.delay(with("wizards_cowl", 20), 4) == 2, "");
        check("a 1-tick weapon stays 1", RelicTempo.delay(with("wizards_cowl", 20), 1) == 1, "");
        check("delay never exceeds the base", RelicTempo.delay(with("wizards_cowl", 1), 3) <= 3, "");
        boolean mono = true; int prev = 100; for (int n = 0; n <= 10; n++) { int d = RelicTempo.delay(with("wizards_cowl", n), 40); if (d > prev) mono = false; prev = d; }
        check("more cowls never slow a weapon (monotonic)", mono, "");
        check("no quiver: no extra strikes", RelicTempo.extraStrikes(none) == 0 && RelicTempo.strikes(none, 3) == 3, "");
        check("quiver x2 adds 2 to a volley of 3", RelicTempo.strikes(with("quiver_of_plenty", 2), 3) == 5, "");
        check("quiver is capped at +6", RelicTempo.extraStrikes(with("quiver_of_plenty", 20)) == 6, "");
        check("a non-volley weapon (0) stays 0", RelicTempo.strikes(with("quiver_of_plenty", 4), 0) == 0, "");
        check("no cowl: attack speed bonus is 0", RelicTempo.attackSpeedBonus(none) == 0.0, "");
        check("cowl x5 (delay x0.5) = +100% attack speed", Math.abs(RelicTempo.attackSpeedBonus(with("wizards_cowl", 5)) - 1.0) < 1e-9, "" + RelicTempo.attackSpeedBonus(with("wizards_cowl", 5)));
        check("cowl x1 (delay x0.9) = +11.1% attack speed", Math.abs(RelicTempo.attackSpeedBonus(with("wizards_cowl", 1)) - (1.0 / 0.9 - 1.0)) < 1e-9, "");
        for (int n = 0; n <= 12; n++) { RelicStats s = with("wizards_cowl", n); double m = s.cooldownMultiplier(); double speedup = 1.0 + RelicTempo.attackSpeedBonus(s);
            if (Math.abs(speedup * m - 1.0) > 1e-9) { check("speed x delay = 1 for " + n + " cowls", false, "" + speedup * m); } }
        check("attack speed x delay multiplier is exactly 1 for 0..12 cowls (inverse)", true, "");
        check("cowl 50% cap means attack speed bonus never exceeds +100%", RelicTempo.attackSpeedBonus(with("wizards_cowl", 50)) <= 1.0 + 1e-9, "");
        System.out.println(fails == 0 ? "RESULT: ALL PASS" : "RESULT: " + fails + " FAILED"); System.exit(fails == 0 ? 0 : 1);
    }
}
