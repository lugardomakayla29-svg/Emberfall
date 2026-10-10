import com.solme.emberfall.progression.RewardFormula;

/** Pins the run-end "Bosses defeated" text: the first boss is the Broodtide, never the removed Ember Guardian (or the older Hydra). */
public class RunEndNamesCheck {
    static int fails = 0, total = 0;

    static void check(String l, boolean ok, String e) {
        total++;
        System.out.println((ok ? "PASS " : "FAIL ") + l + "  " + e);
        if (!ok) {
            fails++;
        }
    }

    public static void main(String[] a) {
        check("N1 first boss alone reads exactly 'The Broodtide'", RewardFormula.bossNames(true, false).equals("The Broodtide"), RewardFormula.bossNames(true, false));
        check("N2 the Devourer alone reads exactly 'The Devourer'", RewardFormula.bossNames(false, true).equals("The Devourer"), RewardFormula.bossNames(false, true));
        check("N3 both read 'The Broodtide, The Devourer' in fight order", RewardFormula.bossNames(true, true).equals("The Broodtide, The Devourer"), RewardFormula.bossNames(true, true));
        check("N4 neither reads empty", RewardFormula.bossNames(false, false).isEmpty(), "");
        boolean clean = true;
        for (int i = 0; i < 4; i++) {
            String s = RewardFormula.bossNames((i & 1) != 0, (i & 2) != 0).toLowerCase();
            if (s.contains("guardian") || s.contains("hydra") || s.contains("ember")) {
                clean = false;
            }
        }
        check("N5 no combination ever names the Ember Guardian, the Hydra or anything 'Ember'", clean, "");
        check("N6 the pay is unchanged: the bonus follows the same two flags", RewardFormula.forBosses(true, false) > 0 && RewardFormula.forBosses(false, false) == 0, "");
        System.out.println();
        System.out.println(fails == 0 ? "ALL PASS (" + total + " checks)" : "FAILED " + fails + " of " + total);
        System.exit(fails == 0 ? 0 : 1);
    }
}
