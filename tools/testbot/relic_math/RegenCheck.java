import com.solme.emberfall.relic.*;
import java.util.*;
public class RegenCheck {
    static int fails = 0;
    static void check(String l, boolean ok, String e) { System.out.println((ok ? "PASS " : "FAIL ") + l + "  " + e); if (!ok) fails++; }
    static RelicStats with(Object... kv) { Map<String,Integer> m = new HashMap<>(); for (int i = 0; i < kv.length; i += 2) m.put((String) kv[i], (Integer) kv[i+1]); return new RelicStats(m); }
    public static void main(String[] x) {
        RelicStats none = with(), camp = with("campfire_core", 2), heart = with("dragons_heart", 1), both = with("campfire_core", 2, "dragons_heart", 1);
        check("no relic is inactive (skipped entirely)", !RelicRegen.active(none), "");
        check("campfire and heart are active", RelicRegen.active(camp) && RelicRegen.active(heart), "");
        check("unrelated relics stay inactive", !RelicRegen.active(with("thorn_vest", 5, "big_bonk", 3)), "");
        check("no relic heals nothing", RelicRegen.healFor(none, 999) == 0f, "");
        check("campfire x2 heals NOTHING while moving", RelicRegen.healFor(camp, 0) == 0f && RelicRegen.healFor(camp, 29) == 0f, "");
        check("campfire x2 heals 1.0 per second once still for 30 ticks", RelicRegen.healFor(camp, 30) == 1.0f, "");
        check("dragon's heart heals 0.5 per second even while moving", RelicRegen.healFor(heart, 0) == 0.5f, "");
        check("heart + campfire stack when still (0.5 + 1.0)", RelicRegen.healFor(both, 100) == 1.5f && RelicRegen.healFor(both, 0) == 0.5f, "");
        check("isStill: 0 movement", RelicRegen.isStill(0, 0), "");
        check("isStill: walking (0.2/tick) is not still", !RelicRegen.isStill(0.2, 0), "");
        check("isStill: tiny drift (0.01) still counts", RelicRegen.isStill(0.01, 0.0), "");
        int t = 0; for (int i = 0; i < 40; i++) t = RelicRegen.nextStillTicks(t, true);
        check("40 still ticks counted", t == 40, "" + t);
        check("one step resets the counter", RelicRegen.nextStillTicks(t, false) == 0, "");
        int big = 0; for (int i = 0; i < 20000; i++) big = RelicRegen.nextStillTicks(big, true);
        check("counter is capped (no overflow)", big == 10000, "" + big);
        float total = 0; int st = 0; for (int sec = 0; sec < 60; sec++) { for (int i = 0; i < 20; i++) st = RelicRegen.nextStillTicks(st, true); total += RelicRegen.healFor(camp, st); }
        check("60 s standing still with campfire x2 heals about 60", Math.abs(total - 60f) < 1.5f, "" + total);
        System.out.println(fails == 0 ? "RESULT: ALL PASS" : "RESULT: " + fails + " FAILED"); System.exit(fails == 0 ? 0 : 1);
    }
}
