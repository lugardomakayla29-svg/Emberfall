import com.solme.emberfall.tome.StaleModifiers;

/**
 * Pure checks for StaleModifiers (which player modifier ids are run-scoped and must be stripped when no run is active). They prove the RULE: every id a run
 * adds to a player is matched, meta-progression and other mods' ids are never matched. They do NOT prove the join hook calls it: that is the live test.
 */
public class StaleModifiersCheck {
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
        String u = "d3eeca9b-f438-32aa-95be-84060e2a1371";
        check("S1 a stat tome modifier (the id seen in the crash) is run-scoped", StaleModifiers.isRunScoped("emberfall", "tome_swift_boots_1_" + u), "");
        check("S2 a later stack of a tome is run-scoped", StaleModifiers.isRunScoped("emberfall", "tome_iron_skin_7_" + u), "");
        check("S3 the Momentum synergy bonus is run-scoped (a stale one is a free buff next run)", StaleModifiers.isRunScoped("emberfall", "synergy_momentum3_" + u), "");
        check("S4 a Curse Shrine penalty is run-scoped", StaleModifiers.isRunScoped("emberfall", "shrine_curse_" + u), "");
        check("S5 shop upgrades are NEVER stripped (meta-progression persists)", !StaleModifiers.isRunScoped("emberfall", "shop_upgrade_max_health_" + u), "");
        check("S6 another mod's modifier with a tome_ path is never touched", !StaleModifiers.isRunScoped("othermod", "tome_swift_boots_1_" + u), "");
        check("S7 a vanilla modifier is never touched", !StaleModifiers.isRunScoped("minecraft", "sprinting"), "");
        check("S8 an unrelated emberfall id is never touched", !StaleModifiers.isRunScoped("emberfall", "swarm_speed"), "");
        check("S9 the prefix must START the path, not appear inside it", !StaleModifiers.isRunScoped("emberfall", "my_tome_x"), "");
        check("S10 null and empty are safe", !StaleModifiers.isRunScoped("emberfall", null) && !StaleModifiers.isRunScoped("emberfall", "") && !StaleModifiers.isRunScoped(null, "tome_x"), "");
        check("S11 the bare prefix word without the underscore is not matched", !StaleModifiers.isRunScoped("emberfall", "tomes") && !StaleModifiers.isRunScoped("emberfall", "synergy"), "");

        System.out.println(fails == 0 ? "ALL PASS " + total : "FAILED " + fails + " of " + total);
        if (fails != 0) {
            System.exit(1);
        }
    }
}
