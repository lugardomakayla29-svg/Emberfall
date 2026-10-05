import com.solme.emberfall.relic.*;
import java.util.*;
public class DefenceCheck {
    static int fails = 0;
    static void check(String l, boolean ok, String e) { System.out.println((ok ? "PASS " : "FAIL ") + l + "  " + e); if (!ok) fails++; }
    static RelicStats with(Object... kv) { Map<String,Integer> m = new HashMap<>(); for (int i = 0; i < kv.length; i += 2) m.put((String) kv[i], (Integer) kv[i+1]); return new RelicStats(m); }
    public static void main(String[] x) {
        RelicStats none = with();
        check("no relics: every hit lands", RelicDefence.judge(none, 0, () -> 0.0) == RelicDefence.Verdict.LANDS, "");
        check("invulnerability window ignores the hit even with no dodge", RelicDefence.judge(none, 5, () -> 0.99) == RelicDefence.Verdict.IGNORE_INVULNERABLE, "");
        check("invulnerability beats dodge (no roll consumed)", RelicDefence.judge(with("pearl_shard", 5), 3, () -> { throw new RuntimeException("roll used"); }) == RelicDefence.Verdict.IGNORE_INVULNERABLE, "");
        RelicStats p = with("pearl_shard", 2); // 16%
        check("dodge when roll < 16%", RelicDefence.judge(p, 0, () -> 0.10) == RelicDefence.Verdict.DODGED, "");
        check("lands when roll >= 16%", RelicDefence.judge(p, 0, () -> 0.16) == RelicDefence.Verdict.LANDS, "");
        Random r = new Random(5); int d = 0, N = 400000; for (int i = 0; i < N; i++) if (RelicDefence.judge(p, 0, r::nextDouble) == RelicDefence.Verdict.DODGED) d++;
        check("sampled dodge rate is 16%", Math.abs(100.0 * d / N - 16.0) < 0.2, String.format("%.3f%%", 100.0 * d / N));
        d = 0; RelicStats cap = with("pearl_shard", 9); for (int i = 0; i < N; i++) if (RelicDefence.judge(cap, 0, r::nextDouble) == RelicDefence.Verdict.DODGED) d++;
        check("dodge is capped at 40% even with 9 stacks", Math.abs(100.0 * d / N - 40.0) < 0.3, String.format("%.3f%%", 100.0 * d / N));
        check("no dodge relic never consumes a roll", RelicDefence.judge(none, 0, () -> { throw new RuntimeException("roll used"); }) == RelicDefence.Verdict.LANDS, "");
        check("thorns: 3 stacks = 6 back", RelicDefence.reflected(with("thorn_vest", 3), 10f) == 6.0f, "");
        check("mirror: half the damage taken", RelicDefence.reflected(with("mirror_shard", 1), 10f) == 5.0f, "");
        check("thorns + mirror add up (6 + 5)", RelicDefence.reflected(with("thorn_vest", 3, "mirror_shard", 1), 10f) == 11.0f, "");
        check("no relics reflect nothing", RelicDefence.reflected(none, 10f) == 0.0f, "");
        check("zero or negative damage reflects nothing", RelicDefence.reflected(with("thorn_vest", 5), 0f) == 0f && RelicDefence.reflected(with("thorn_vest", 5), -3f) == 0f, "");
        check("mirror ready only when held and off cooldown", RelicDefence.mirrorReady(with("mirror_shard", 1), 0) && !RelicDefence.mirrorReady(with("mirror_shard", 1), 1) && !RelicDefence.mirrorReady(none, 0), "");
        check("totem saves once", RelicDefence.totemSaves(with("totem_of_returning", 1), false) && !RelicDefence.totemSaves(with("totem_of_returning", 1), true), "");
        check("no totem never saves", !RelicDefence.totemSaves(none, false), "");
        check("totem health is half max", RelicDefence.totemHealth(40f) == 20f, "");
        check("totem health never below 1 heart", RelicDefence.totemHealth(1f) == 2f, "");
        System.out.println(fails == 0 ? "RESULT: ALL PASS" : "RESULT: " + fails + " FAILED"); System.exit(fails == 0 ? 0 : 1);
    }
}
