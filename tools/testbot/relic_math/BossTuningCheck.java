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
        check("B1 the boost is the owner's 35% (exactly 1.35)", BossTuning.BASE_BOOST > 1.0 && BossTuning.BASE_BOOST == 1.35, "boost " + BossTuning.BASE_BOOST);
        check("B2 the Devourer's health went up from 260", hp > BossTuning.DEVOURER_BASE_HEALTH, "hp " + hp);
        check("B3 the health is exactly 666 (owner decision 2026-10-09, a chosen number)", hp == 666.0, "hp " + hp);
        check("B4 the health is a whole number, so the boss bar reads cleanly", hp == Math.rint(hp), "hp " + hp);
        check("B5 the starting damage scale is the boost alone", Math.abs(BossTuning.devourerDamageScale() - 1.35F) < 1e-6, "scale " + BossTuning.devourerDamageScale());
        check("B6 no curse (1.0) leaves the boost as it is", Math.abs(BossTuning.withCurse(1.0) - 1.35) < 1e-9, "with 1.0: " + BossTuning.withCurse(1.0));
        check("B7 a 1.5 Boss Curse stacks on top: 1.35 x 1.5 = 2.025, not 1.5 (the boost is not lost)", Math.abs(BossTuning.withCurse(1.5) - 2.025) < 1e-9, "with 1.5: " + BossTuning.withCurse(1.5));
        check("B8 a curse below 1 (bad input) can never reduce the boost", BossTuning.withCurse(0.5) >= BossTuning.BASE_BOOST && BossTuning.withCurse(-3) >= BossTuning.BASE_BOOST, "0.5 -> " + BossTuning.withCurse(0.5));
        check("B9 the curse still only ever raises damage: monotonic over 1.0 to 3.0", mono(), "");
        check("B10 the boosted Devourer hits harder on every attack (7, 10, 9, 6 base damage all rise)", rises(7) && rises(10) && rises(9) && rises(6), "dash 7 -> " + 7 * BossTuning.devourerDamageScale());

        double sp = BossTuning.devourerSpeed();
        check("B11 movement speed is raised by the same 35%: 0.32 -> 0.432 (the owner said all stats)", Math.abs(sp - 0.432) < 1e-9, "speed " + sp);
        check("B12 damage and speed use ONE factor, the boost (health is set by hand to 666, so it is NOT part of this)",
                Math.abs(BossTuning.devourerDamageScale() - BossTuning.BASE_BOOST) < 1e-6
                        && Math.abs(sp / BossTuning.DEVOURER_BASE_SPEED - BossTuning.BASE_BOOST) < 1e-9, "dmg " + BossTuning.devourerDamageScale() + " sp " + sp / BossTuning.DEVOURER_BASE_SPEED);

        check("B13 Broodtide health is 810 (600 x 1.35), a whole number", BossTuning.broodtideHealth() == 810.0, "hp " + BossTuning.broodtideHealth());
        check("B14 Broodtide contact damage is 13.5 and speed 0.297: the same 35% on every stat",
                Math.abs(BossTuning.broodtideDamage() - 13.5) < 1e-9 && Math.abs(BossTuning.broodtideSpeed() - 0.297) < 1e-9, "dmg " + BossTuning.broodtideDamage() + " sp " + BossTuning.broodtideSpeed());
        check("B15 Broodtide keeps the plain boost on health (600 x 1.35 = 810), the Devourer does not: 666 is chosen, not derived",
                Math.abs(BossTuning.broodtideHealth() / BossTuning.BROODTIDE_BASE_HEALTH - BossTuning.BASE_BOOST) < 0.005
                        && BossTuning.devourerHealth() != Math.round(BossTuning.DEVOURER_BASE_HEALTH * BossTuning.BASE_BOOST), "");
        check("B16 666 is a real raise: 2.56x the original 260, and above what the plain boost would give (351)",
                BossTuning.devourerHealth() > Math.round(BossTuning.DEVOURER_BASE_HEALTH * BossTuning.BASE_BOOST)
                        && Math.abs(BossTuning.devourerHealth() / BossTuning.DEVOURER_BASE_HEALTH - 2.5615) < 0.001, "ratio " + BossTuning.devourerHealth() / BossTuning.DEVOURER_BASE_HEALTH);

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
