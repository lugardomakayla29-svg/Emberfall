import com.solme.emberfall.boss.FirstBoss;
import com.solme.emberfall.boss.FirstBoss.Kind;

/**
 * Pure checks for which boss the tier-1 slot spawns (FirstBoss). They prove the default is the Broodtide, the flag restores the Guardian, and the name
 * shown to the player always matches the boss that spawned. They do NOT prove WaveDirector calls this (the jar check does).
 */
public class FirstBossCheck {
    static int fails = 0, total = 0;

    static void check(String l, boolean ok, String e) {
        total++;
        System.out.println((ok ? "PASS " : "FAIL ") + l + "  " + e);
        if (!ok) fails++;
    }

    public static void main(String[] a) {
        check("F1 with the flag OFF the first boss is the Broodtide", FirstBoss.select(false) == Kind.BROODTIDE, "" + FirstBoss.select(false));
        check("F2 with the flag ON the first boss is the Ember Guardian", FirstBoss.select(true) == Kind.EMBER_GUARDIAN, "" + FirstBoss.select(true));
        check("F3 the flag's name is the documented property", FirstBoss.LEGACY_PROPERTY.equals("emberfall.legacyGuardian"), FirstBoss.LEGACY_PROPERTY);

        System.clearProperty(FirstBoss.LEGACY_PROPERTY);
        check("F4 the real server default (property absent) is the Broodtide", FirstBoss.current() == Kind.BROODTIDE, "" + FirstBoss.current());
        System.setProperty(FirstBoss.LEGACY_PROPERTY, "true");
        check("F5 -Demberfall.legacyGuardian=true restores the Guardian", FirstBoss.current() == Kind.EMBER_GUARDIAN, "" + FirstBoss.current());
        System.setProperty(FirstBoss.LEGACY_PROPERTY, "false");
        check("F6 an explicit false keeps the Broodtide", FirstBoss.current() == Kind.BROODTIDE, "" + FirstBoss.current());
        System.setProperty(FirstBoss.LEGACY_PROPERTY, "yes");
        check("F7 a junk value is NOT true, so it cannot silently switch the boss", FirstBoss.current() == Kind.BROODTIDE, "" + FirstBoss.current());
        System.clearProperty(FirstBoss.LEGACY_PROPERTY);

        for (Kind k : Kind.values()) {
            String name = FirstBoss.displayName(k);
            check("F8 " + k + ": the awakening line, run summary and name all agree",
                    FirstBoss.awakenLine(k).contains(name) && FirstBoss.defeatedLine(k).contains(k == Kind.BROODTIDE ? "Broodtide" : "Ember Guardian"), FirstBoss.awakenLine(k));
        }
        check("F9 the two bosses have different names", !FirstBoss.displayName(Kind.BROODTIDE).equals(FirstBoss.displayName(Kind.EMBER_GUARDIAN)), "");
        check("F10 the Broodtide line never says Guardian and the Guardian line never says Broodtide",
                !FirstBoss.awakenLine(Kind.BROODTIDE).contains("Guardian") && !FirstBoss.defeatedLine(Kind.BROODTIDE).contains("Guardian")
                        && !FirstBoss.awakenLine(Kind.EMBER_GUARDIAN).contains("Broodtide") && !FirstBoss.defeatedLine(Kind.EMBER_GUARDIAN).contains("Broodtide"), "");

        System.out.println();
        System.out.println(fails == 0 ? "ALL PASS (" + total + " checks)" : "SOME FAIL (" + fails + " of " + total + ")");
        if (fails > 0) System.exit(1);
    }
}
