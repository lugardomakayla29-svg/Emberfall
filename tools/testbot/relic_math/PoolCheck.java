import com.solme.emberfall.relic.*;
import java.util.*;

public class PoolCheck {
    static int fails = 0;
    static void check(String label, boolean ok, String extra) { System.out.println((ok ? "PASS " : "FAIL ") + label + "  " + extra); if (!ok) fails++; }
    public static void main(String[] a) {
        List<Relic> all = RelicPool.all();
        check("24 relics", all.size() == 24, "" + all.size());
        for (RelicRarity r : RelicRarity.values()) check("6 " + r, RelicPool.ofRarity(r).size() == 6, "" + RelicPool.ofRarity(r).size());
        Set<String> ids = new HashSet<>(); boolean dup = false;
        for (Relic r : all) if (!ids.add(r.id())) dup = true;
        check("ids unique", !dup, "");
        boolean sane = true;
        for (Relic r : all) if (r.name().isBlank() || r.description().isBlank() || r.maxStacks() < 1 || r.id().contains(" ") ) sane = false;
        check("every relic has name, description, max>=1, id without spaces", sane, "");
        boolean gatedOk = true;
        for (Relic r : all) if (r.isGated() != (r.unlockText() != null)) gatedOk = false;
        check("gated relics all carry unlock text", gatedOk, "");
        StringBuilder g = new StringBuilder();
        for (Relic r : all) if (r.isGated()) g.append(r.id()).append("(").append(r.unlockId()).append(") ");
        System.out.println("gated: " + g);
        // pick(): with nothing unlocked, a gated relic never drops
        Random rnd = new Random(7); Set<String> none = Set.of();
        boolean leak = false;
        for (int i = 0; i < 20000; i++) { Relic r = RelicPool.pick(RelicRarity.values()[i % 4], Map.of(), none, rnd::nextDouble); if (r == null || r.isGated()) leak = true; }
        check("gated relics never drop while locked", !leak, "");
        // ...and does drop once unlocked
        Set<String> all3 = Set.of("open_25_chests", "reach_level_15", "clear_3_challenges"); boolean seen = false;
        for (int i = 0; i < 20000; i++) { Relic r = RelicPool.pick(RelicRarity.COMMON, Map.of(), all3, rnd::nextDouble); if (r.id().equals("ember_key")) seen = true; }
        check("a gated relic drops once unlocked", seen, "");
        // maxed relic is skipped
        Map<String, Integer> owned = new HashMap<>(); for (Relic r : RelicPool.ofRarity(RelicRarity.LEGENDARY)) owned.put(r.id(), r.maxStacks());
        Relic fb = RelicPool.pick(RelicRarity.LEGENDARY, owned, all3, rnd::nextDouble);
        check("full legendary tier falls back DOWN, never up", fb != null && fb.rarity() == RelicRarity.RARE, fb == null ? "null" : fb.rarity() + " " + fb.id());
        // everything maxed: null
        Map<String, Integer> full = new HashMap<>(); for (Relic r : all) full.put(r.id(), r.maxStacks());
        check("nothing eligible returns null", RelicPool.pick(RelicRarity.LEGENDARY, full, all3, rnd::nextDouble) == null, "");
        // roll edges never index out of range
        boolean edge = true;
        try { RelicPool.pick(RelicRarity.COMMON, Map.of(), all3, () -> 0.0); RelicPool.pick(RelicRarity.COMMON, Map.of(), all3, () -> 0.9999999999); } catch (RuntimeException e) { edge = false; }
        check("roll 0.0 and ~1.0 are in range", edge, "");
        // every relic is reachable from an unconstrained pick
        Set<String> seenIds = new HashSet<>();
        for (int i = 0; i < 200000; i++) seenIds.add(RelicPool.pick(RelicRarity.values()[i % 4], Map.of(), all3, rnd::nextDouble).id());
        check("all 24 reachable when everything is unlocked", seenIds.size() == 24, "" + seenIds.size());
        System.out.println(fails == 0 ? "RESULT: ALL PASS" : "RESULT: " + fails + " FAILED");
        System.exit(fails == 0 ? 0 : 1);
    }
}
