import com.solme.emberfall.relic.FreeChestRule;
import com.solme.emberfall.relic.FreeChestRule.Source;
import java.util.Random;
public class FreeChestCheck {
    static int fails = 0;
    static void check(String l, boolean ok, String e) { System.out.println((ok ? "PASS " : "FAIL ") + l + "  " + e); if (!ok) fails++; }
    public static void main(String[] a) {
        check("a boss always drops (chance 1.0)", FreeChestRule.chance(Source.BOSS, 0) == 1.0, "");
        check("a cleared shrine always drops (chance 1.0)", FreeChestRule.chance(Source.SHRINE, 5) == 1.0, "");
        check("the first elite has the base chance 0.15", Math.abs(FreeChestRule.chance(Source.ELITE, 0) - 0.15) < 1e-12, "");
        check("the chance falls with every free chest given", FreeChestRule.chance(Source.ELITE, 3) < FreeChestRule.chance(Source.ELITE, 2), String.format("%.3f < %.3f", FreeChestRule.chance(Source.ELITE, 3), FreeChestRule.chance(Source.ELITE, 2)));
        check("at the cap nothing drops, not even a boss or a shrine", FreeChestRule.chance(Source.BOSS, 12) == 0 && FreeChestRule.chance(Source.SHRINE, 12) == 0 && FreeChestRule.chance(Source.ELITE, 12) == 0, "");
        check("one below the cap still drops a boss", FreeChestRule.chance(Source.BOSS, 11) == 1.0, "");
        check("a negative count behaves as zero", FreeChestRule.chance(Source.ELITE, -4) == FreeChestRule.chance(Source.ELITE, 0), "");
        check("roll 0.0 always drops, roll 1.0 never does (elite)", FreeChestRule.drops(Source.ELITE, 0, 0.0) && !FreeChestRule.drops(Source.ELITE, 0, 1.0), "");
        // Simulate long runs: 30 elites, 1 boss, 3 shrines each, 20000 runs. The total must respect the cap and the mean must stay a treat.
        Random r = new Random(5); int max = 0; long total = 0, capped = 0; int runs = 20000; int eliteDrops = 0;
        for (int i = 0; i < runs; i++) {
            int given = 0;
            for (int e = 0; e < 30; e++) if (FreeChestRule.drops(Source.ELITE, given, r.nextDouble())) { given++; eliteDrops++; }
            if (FreeChestRule.drops(Source.BOSS, given, r.nextDouble())) given++;
            for (int s = 0; s < 3; s++) if (FreeChestRule.drops(Source.SHRINE, given, r.nextDouble())) given++;
            max = Math.max(max, given); total += given; if (given >= 12) capped++;
        }
        double mean = total / (double) runs;
        System.out.printf("     over %d runs of 30 elites + 1 boss + 3 shrines: mean %.2f free chests, max %d, elite share %.2f, runs at the cap %d%n", runs, mean, max, eliteDrops / (double) runs, capped);
        check("never more than the cap of 12 free chests in a run", max <= 12, "max " + max);
        check("the cap is a safety net: under 3% of 30-elite runs touch it", capped / (double) runs < 0.03, String.format("%.1f%%", 100.0 * capped / runs));
        check("a 30-elite run gives a modest mean (6.5 to 9 free chests incl. boss and 3 shrines, 16 paid on the map)", mean >= 6.5 && mean <= 9, String.format("%.2f", mean));
        double e5 = 0, e15 = 0; for (int i = 0; i < runs; i++) { int g = 0; for (int e = 0; e < 5; e++) if (FreeChestRule.drops(Source.ELITE, g, r.nextDouble())) g++; e5 += g; g = 0; for (int e = 0; e < 15; e++) if (FreeChestRule.drops(Source.ELITE, g, r.nextDouble())) g++; e15 += g; }
        check("5 elites leave 0.5 to 1.0 free chests on average", e5 / runs >= 0.5 && e5 / runs <= 1.0, String.format("%.2f", e5 / runs));
        check("15 elites leave 1.6 to 2.5 free chests on average", e15 / runs >= 1.6 && e15 / runs <= 2.5, String.format("%.2f", e15 / runs));
        System.out.println(fails == 0 ? "RESULT: ALL PASS" : "RESULT: " + fails + " FAILED"); System.exit(fails == 0 ? 0 : 1);
    }
}
