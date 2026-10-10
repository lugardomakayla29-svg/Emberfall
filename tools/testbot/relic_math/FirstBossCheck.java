import com.solme.emberfall.boss.FirstBoss;

/**
 * Pure checks for the tier-1 boss wording (FirstBoss). The Ember Guardian is gone (owner decision 2026-10-09), so these prove the player-facing text names the
 * Broodtide everywhere and never names the Guardian, and that the three texts agree with each other. They do NOT prove which entity spawns (the live test does).
 */
public class FirstBossCheck {
    static int fails = 0, total = 0;

    static void check(String l, boolean ok, String e) {
        total++;
        System.out.println((ok ? "PASS " : "FAIL ") + l + "  " + e);
        if (!ok) fails++;
    }

    public static void main(String[] a) {
        check("F1 the boss is named The Broodtide", FirstBoss.NAME.equals("The Broodtide"), FirstBoss.NAME);
        check("F2 the awakening line contains the name", FirstBoss.awakenLine().contains(FirstBoss.NAME), FirstBoss.awakenLine());
        check("F3 the awakening line says it rises", FirstBoss.awakenLine().endsWith("rises."), "");
        check("F4 the awakening line keeps the red bold style", FirstBoss.awakenLine().startsWith("\u00a7c\u00a7l"), "");
        check("F5 the run-summary fragment names the Broodtide", FirstBoss.defeatedLine().contains("Broodtide"), FirstBoss.defeatedLine());
        check("F6 the run-summary fragment reads as a clause (starts with a comma)", FirstBoss.defeatedLine().startsWith(", "), "");
        String all = FirstBoss.NAME + FirstBoss.awakenLine() + FirstBoss.defeatedLine();
        check("F7 no player-facing text names the removed Ember Guardian", !all.contains("Guardian") && !all.contains("awakens"), "");
        System.out.println();
        System.out.println(fails == 0 ? "ALL PASS (" + total + " checks)" : "SOME FAIL (" + fails + " of " + total + ")");
        if (fails > 0) System.exit(1);
    }
}
