import com.solme.emberfall.relic.*;
import java.util.*;
import java.util.function.DoubleSupplier;
public class ChestCheck {
    static int fails = 0;
    static void check(String l, boolean ok, String e) { System.out.println((ok ? "PASS " : "FAIL ") + l + "  " + e); if (!ok) fails++; }
    static DoubleSupplier fixed(double... v) { int[] i = {0}; return () -> v[Math.min(i[0]++, v.length - 1)]; }
    static ChestOpening.Outcome go(ChestOpening.Kind k, int gold, int opened, double luck, int keys, Map<String,Integer> owned, DoubleSupplier r) {
        return ChestOpening.open(k, gold, opened, luck, keys, owned, Set.of("open_25_chests", "reach_level_15", "clear_3_challenges"), r);
    }
    public static void main(String[] x) {
        Map<String,Integer> none = new HashMap<>();
        // 1 paid, enough gold, no key: charged the price, counts
        var a = go(ChestOpening.Kind.PAID, 100, 0, 0, 0, none, fixed(0.99, 0.0, 0.0));
        check("paid chest #1 costs 30 and counts", a.opened() && a.goldCost() == 30 && a.countsOpening() && !a.keyProc(), a.goldCost() + " " + a.relic());
        // 2 price climbs
        var b = go(ChestOpening.Kind.PAID, 1000, 5, 0, 0, none, fixed(0.99, 0.0, 0.0));
        check("paid chest after 5 openings costs " + RelicMath.chestPrice(5), b.goldCost() == RelicMath.chestPrice(5), "" + b.goldCost());
        // 3 not enough gold: nothing changes
        var c = go(ChestOpening.Kind.PAID, 29, 0, 0, 0, none, fixed(0.99, 0.0, 0.0));
        check("29 gold cannot buy a 30 gold chest, nothing changes", !c.opened() && c.goldCost() == 0 && !c.countsOpening() && c.relic() == null && c.refusal() == ChestOpening.Refusal.NOT_ENOUGH_GOLD, "" + c.refusal());
        var c2 = go(ChestOpening.Kind.PAID, 30, 0, 0, 0, none, fixed(0.99, 0.0, 0.0));
        check("exactly 30 gold is enough", c2.opened() && c2.goldCost() == 30, "");
        // 4 free chest: no cost, no count, ignores keys
        var d = go(ChestOpening.Kind.FREE, 0, 7, 0, 5, none, fixed(0.0, 0.0, 0.0));
        check("free chest costs 0 with 0 gold, does not count, no key proc even with roll 0.0", d.opened() && d.goldCost() == 0 && !d.countsOpening() && !d.keyProc(), "");
        // 5 key proc: free AND does not count
        var e = go(ChestOpening.Kind.PAID, 0, 3, 0, 3, none, fixed(0.10, 0.0, 0.0));
        check("key proc (30% at 3 keys, roll 0.10): free with 0 gold, does not count", e.opened() && e.keyProc() && e.goldCost() == 0 && !e.countsOpening(), "");
        var f = go(ChestOpening.Kind.PAID, 1000, 3, 0, 3, none, fixed(0.40, 0.0, 0.0));
        check("no key proc (roll 0.40 >= 30%): pays and counts", f.opened() && !f.keyProc() && f.goldCost() > 0 && f.countsOpening(), "");
        // 6 key rate over many rolls and the 50% cap
        Random rnd = new Random(12345); int procs = 0, n = 100000;
        for (int i = 0; i < n; i++) if (go(ChestOpening.Kind.PAID, 1_000_000, 0, 0, 2, none, rnd::nextDouble).keyProc()) procs++;
        check("2 keys procs about 20% over 100k (19-21%)", procs > 0.19 * n && procs < 0.21 * n, String.format("%.2f%%", 100.0 * procs / n));
        procs = 0; for (int i = 0; i < n; i++) if (go(ChestOpening.Kind.PAID, 1_000_000, 0, 0, 20, none, rnd::nextDouble).keyProc()) procs++;
        check("20 keys is capped at 50% (49-51%)", procs > 0.49 * n && procs < 0.51 * n, String.format("%.2f%%", 100.0 * procs / n));
        // 7 gold chest never gives common or uncommon
        int low = 0; Map<RelicRarity,Integer> seen = new EnumMap<>(RelicRarity.class);
        for (int i = 0; i < n; i++) { var g = go(ChestOpening.Kind.GOLD, 1_000_000, 0, 0, 0, none, rnd::nextDouble); if (g.relic().rarity().ordinal() < 2) low++; seen.merge(g.relic().rarity(), 1, Integer::sum); }
        check("gold chest never gives common/uncommon over 100k", low == 0, "low=" + low + " " + seen);
        // 8 gold chest still can roll legendary
        check("gold chest reaches legendary", seen.getOrDefault(RelicRarity.LEGENDARY, 0) > 0, "" + seen.get(RelicRarity.LEGENDARY));
        // 9 ordinary chest at luck 0 matches the base weights (60/28/10/2) within tolerance
        Map<RelicRarity,Integer> ord = new EnumMap<>(RelicRarity.class);
        for (int i = 0; i < n; i++) ord.merge(go(ChestOpening.Kind.PAID, 1_000_000, 0, 0, 0, none, rnd::nextDouble).relic().rarity(), 1, Integer::sum);
        check("ordinary chest tier mix near 60/28/10/2 at luck 0", Math.abs(ord.get(RelicRarity.COMMON) / 1000.0 - 60) < 1.5 && Math.abs(ord.get(RelicRarity.LEGENDARY) / 1000.0 - 2) < 0.6, ord.toString());
        // 10 luck raises legendary share
        int leg0 = 0, leg100 = 0; for (int i = 0; i < n; i++) { if (go(ChestOpening.Kind.PAID, 1_000_000, 0, 0, 0, none, rnd::nextDouble).relic().rarity() == RelicRarity.LEGENDARY) leg0++; if (go(ChestOpening.Kind.PAID, 1_000_000, 0, 100, 0, none, rnd::nextDouble).relic().rarity() == RelicRarity.LEGENDARY) leg100++; }
        check("luck 100 gives clearly more legendaries than luck 0", leg100 > leg0 * 3, leg0 + " -> " + leg100);
        // 11 maxed-out pool: nothing is charged
        Map<String,Integer> all = new HashMap<>(); for (Relic r : RelicPool.all()) all.put(r.id(), r.maxStacks());
        var h = go(ChestOpening.Kind.PAID, 1000, 0, 0, 0, all, rnd::nextDouble);
        check("every relic maxed: no sale, no charge, no count", !h.opened() && h.goldCost() == 0 && !h.countsOpening() && h.refusal() == ChestOpening.Refusal.NOTHING_LEFT, "" + h.refusal());
        var h2 = go(ChestOpening.Kind.FREE, 0, 0, 0, 0, all, rnd::nextDouble);
        check("every relic maxed: free chest also gives nothing", !h2.opened(), "");
        // 12 a relic already at cap is never offered
        Map<String,Integer> one = new HashMap<>(none); one.put("hourglass", 1); one.put("wither_crown", 1);
        boolean dupe = false; for (int i = 0; i < 20000; i++) { var o = go(ChestOpening.Kind.GOLD, 1_000_000, 0, 100, 0, one, rnd::nextDouble); if (o.relic().id().equals("hourglass") || o.relic().id().equals("wither_crown")) dupe = true; }
        check("maxed relics are never offered again (20k gold chests at luck 100)", !dupe, "");
        // 13 locked relics are never offered
        Set<String> lockedIds = new HashSet<>(); for (Relic r : RelicPool.all()) if (r.isGated()) lockedIds.add(r.id());
        boolean leak = false; for (int i = 0; i < 30000; i++) { var o = ChestOpening.open(ChestOpening.Kind.PAID, 1_000_000, 0, 50, 0, none, Set.of(), rnd::nextDouble); if (o.relic() != null && o.relic().isGated()) leak = true; }
        check("with nothing unlocked, no gated relic ever comes out (" + lockedIds.size() + " gated)", !leak && !lockedIds.isEmpty(), "" + lockedIds);
        // 14 a sequence of 20 paid openings on a fixed wallet: total spent equals the sum of the price curve
        int gold = 100000, spent = 0, opened = 0; for (int i = 0; i < 20; i++) { var o = go(ChestOpening.Kind.PAID, gold, opened, 0, 0, none, fixed(0.99, 0.3, 0.3)); gold -= o.goldCost(); spent += o.goldCost(); if (o.countsOpening()) opened++; }
        int expect = 0; for (int i = 0; i < 20; i++) expect += RelicMath.chestPrice(i);
        check("20 paid openings spend exactly the price curve sum", spent == expect && opened == 20, spent + " vs " + expect);
        // 15 determinism: same rolls, same result
        var p1 = go(ChestOpening.Kind.PAID, 500, 2, 10, 1, none, fixed(0.7, 0.2, 0.4)); var p2 = go(ChestOpening.Kind.PAID, 500, 2, 10, 1, none, fixed(0.7, 0.2, 0.4));
        check("same rolls give the same outcome", p1.relic() == p2.relic() && p1.goldCost() == p2.goldCost(), "");
        System.out.println(fails == 0 ? "RESULT: ALL PASS" : "RESULT: " + fails + " FAILED"); System.exit(fails == 0 ? 0 : 1);
    }
}
