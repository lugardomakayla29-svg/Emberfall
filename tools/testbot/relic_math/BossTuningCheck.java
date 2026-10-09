import com.solme.emberfall.boss.BossTuning;

/**
 * Pure checks for the base boss boost (BossTuning, owner 2026-10-09). They prove the arithmetic: the boost is a real increase, health lands on a
 * whole number, the Boss Curse stacks on top and is never lost, and a curse below 1 cannot reduce the boost. They do NOT prove the Devourer
 * reads these (the jar check does) or that the fight is balanced (a playtest does).
 */
public class BossTuningCheck {
    static int fails = 0;
    static int total = 0;

    static void check(String l, boolean ok, String e) {
        total++;
        System.out.println((ok ? "PASS " : "FAIL ") + l + "  " + e);
        if (!ok) {
            fails++;
        }
    }

    public static void main(String[] a) {
        double hp = BossTuning.devourerHealth();
        check("B1 the boost is an increase, but a small one (above 1.0, at most 1.25)", BossTuning.BASE_BOOST > 1.0 && BossTuning.BASE_BOOST <= 1.25, "boost " + BossTuning.BASE_BOOST);
        check("B2 the Devourer's health went up from 260", hp > BossTuning.DEVOURER_BASE_HEALTH, "hp " + hp);
        check("B3 the health is exactly 299 (260 x 1.15 rounded)", hp == 299.0, "hp " + hp);
        check("B4 the health is a whole number, so the boss bar reads cleanly", hp == Math.rint(hp), "hp " + hp);
        check("B5 the starting damage scale is the boost alone", Math.abs(BossTuning.devourerDamageScale() - 1.15F) < 1e-6, "scale " + BossTuning.devourerDamageScale());
        check("B6 no curse (1.0) leaves the boost as it is", Math.abs(BossTuning.withCurse(1.0) - 1.15) < 1e-9, "with 1.0: " + BossTuning.withCurse(1.0));
        check("B7 a 1.5 Boss Curse stacks on top: 1.15 x 1.5 = 1.725, not 1.5 (the boost is not lost)", Math.abs(BossTuning.withCurse(1.5) - 1.725) < 1e-9, "with 1.5: " + BossTuning.withCurse(1.5));
        check("B8 a curse below 1 (bad input) can never reduce the boost", BossTuning.withCurse(0.5) >= BossTuning.BASE_BOOST && BossTuning.withCurse(-3) >= BossTuning.BASE_BOOST, "0.5 -> " + BossTuning.withCurse(0.5));
        check("B9 the curse still only ever raises damage: monotonic over 1.0 to 3.0", mono(), "");
        check("B10 the boosted Devourer hits harder on every attack (7, 10, 9, 6 base damage all rise)", rises(7) && rises(10) && rises(9) && rises(6), "dash 7 -> " + 7 * BossTuning.devourerDamageScale());

        System.out.println();
        System.out.println(fails == 0 ? "ALL PASS (" + total + " checks)" : "SOME FAIL (" + fails + " of " + total + ")");
        if (fails > 0) System.exit(1);
    }

    static boolean mono() {
        double prev = 0;
        for (double c = 1.0; c <= 3.0; c += 0.05) {
            double v = BossTuning.withCurse(c);
            if (v < prev) return false;
            prev = v;
        }
        return true;
    }

    static boolean rises(float base) {
        return base * BossTuning.devourerDamageScale() > base;
    }
}
