import com.solme.emberfall.relic.*;
import com.solme.emberfall.world.StaticMap;
import java.util.*;
public class PlanCheck {
    static int fails = 0;
    static void check(String l, boolean ok, String e) { System.out.println((ok ? "PASS " : "FAIL ") + l + "  " + e); if (!ok) fails++; }
    static double minGap(List<ChestPlan.Spot> s) { double m = 1e9; for (int i = 0; i < s.size(); i++) for (int j = i + 1; j < s.size(); j++) m = Math.min(m, Math.hypot(s.get(i).x() - s.get(j).x(), s.get(i).z() - s.get(j).z())); return m; }
    public static void main(String[] a) {
        List<int[]> c = StaticMap.get().chestCandidates(3, 15, 80, 12);
        var p = ChestPlan.plan(c, ChestPlan.PAID_CHESTS, ChestPlan.GOLD_CHESTS, new Random(1));
        check("exactly 16 chests (14 paid + 2 gold)", p.size() == 16, "" + p.size());
        long gold = p.stream().filter(s -> s.kind() == ChestOpening.Kind.GOLD).count(), paid = p.stream().filter(s -> s.kind() == ChestOpening.Kind.PAID).count();
        check("2 gold and 14 paid, no free chest placed in the world", gold == 2 && paid == 14, gold + "/" + paid);
        check("all spots are real candidates", p.stream().allMatch(s -> c.stream().anyMatch(q -> q[0] == s.x() && q[2] == s.z())), "");
        check("no two chests on the same block", p.stream().map(s -> s.x() + "," + s.z()).distinct().count() == p.size(), "");
        System.out.printf("     min gap over seed 1: %.1f%n", minGap(p));
        check("the preferred 22 block gap holds with 16 chests on this map", minGap(p) >= ChestPlan.PREFERRED_GAP - 1e-9, String.format("%.1f", minGap(p)));
        // 200 seeds: always 16, never below the hard minimum gap, runs differ
        int worstCount = 99; double worstGap = 1e9; Set<String> layouts = new HashSet<>();
        for (int seed = 0; seed < 200; seed++) { var q = ChestPlan.plan(c, 14, 2, new Random(seed)); worstCount = Math.min(worstCount, q.size()); worstGap = Math.min(worstGap, minGap(q)); layouts.add(q.toString()); }
        check("200 seeds: always 16 chests", worstCount == 16, "" + worstCount);
        check("200 seeds: never closer than the hard minimum 8", worstGap >= ChestPlan.MIN_GAP - 1e-9, String.format("worst %.1f", worstGap));
        check("200 seeds give 200 different layouts (each run plays differently)", layouts.size() == 200, "" + layouts.size());
        check("same seed gives the same layout", ChestPlan.plan(c, 14, 2, new Random(7)).equals(ChestPlan.plan(c, 14, 2, new Random(7))), "");
        // gold chests are spread like the rest: no gold pair closer than the preferred gap on seed 1
        var golds = p.stream().filter(s -> s.kind() == ChestOpening.Kind.GOLD).toList();
        check("the two gold chests are far apart (>= 22)", Math.hypot(golds.get(0).x() - golds.get(1).x(), golds.get(0).z() - golds.get(1).z()) >= 22, "");
        // edge cases
        check("zero chests requested gives none", ChestPlan.plan(c, 0, 0, new Random(1)).isEmpty(), "");
        check("no candidates gives none, no crash", ChestPlan.plan(List.of(), 14, 2, new Random(1)).isEmpty(), "");
        check("negative counts are treated as zero", ChestPlan.plan(c, -5, -1, new Random(1)).isEmpty(), "");
        var tiny = ChestPlan.plan(c.subList(0, 3), 14, 2, new Random(1));
        check("fewer candidates than chests: places what fits, never duplicates", tiny.size() <= 3 && tiny.stream().map(s -> s.x() + "," + s.z()).distinct().count() == tiny.size(), "" + tiny.size());
        var huge = ChestPlan.plan(c, 400, 0, new Random(1));
        check("asking for more than the map can hold stops at the hard gap, no infinite loop", huge.size() > 16 && minGap(huge) >= ChestPlan.MIN_GAP - 1e-9, huge.size() + " chests, gap " + String.format("%.1f", minGap(huge)));
        System.out.println(fails == 0 ? "RESULT: ALL PASS" : "RESULT: " + fails + " FAILED"); System.exit(fails == 0 ? 0 : 1);
    }
}
