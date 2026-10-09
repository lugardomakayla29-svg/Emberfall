package com.solme.emberfall.boss;

/**
 * Which boss the tier-1 slot spawns. The Broodtide replaces the Ember Guardian (owner decision 2026-10-06 "I say start Broodtide"); the plan
 * (docs/PLAN_broodtide.md section 4) keeps the Guardian reachable behind a flag until Broodtide is playtested, so old tests and a side-by-side comparison
 * still work. One pure selector so both the START (WaveDirector) and the display name read the same answer and cannot disagree.
 */
public final class FirstBoss {
    private FirstBoss() {}

    public enum Kind { BROODTIDE, EMBER_GUARDIAN }

    /** The system property that restores the Guardian. Default (absent or false) is the Broodtide. */
    public static final String LEGACY_PROPERTY = "emberfall.legacyGuardian";

    /** Which boss a run gets, given the flag's value. */
    public static Kind select(boolean legacyGuardian) {
        return legacyGuardian ? Kind.EMBER_GUARDIAN : Kind.BROODTIDE;
    }

    /** The flag as the running server sees it. */
    public static Kind current() {
        return select(Boolean.getBoolean(LEGACY_PROPERTY));
    }

    /** The boss's name as the player reads it (run summary, awakening line). */
    public static String displayName(Kind k) {
        return k == Kind.BROODTIDE ? "The Broodtide" : "The Ember Guardian";
    }

    /** The awakening line broadcast when the boss spawns, in the mod's red bold style. */
    public static String awakenLine(Kind k) {
        return "\u00a7c\u00a7l" + displayName(k) + (k == Kind.BROODTIDE ? " rises." : " awakens.");
    }

    /** Run-summary fragment when the tier-1 boss was defeated. */
    public static String defeatedLine(Kind k) {
        return ", " + (k == Kind.BROODTIDE ? "Broodtide" : "Ember Guardian") + " defeated";
    }
}
