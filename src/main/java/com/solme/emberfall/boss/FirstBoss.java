package com.solme.emberfall.boss;

/**
 * The tier-1 boss is the Broodtide. It replaced the Ember Guardian entirely (owner decision 2026-10-09: the Guardian is removed, not kept behind a flag).
 * This class only holds the player-facing wording, in one place, so the awakening line, the boss command's reply and the run summary cannot disagree.
 */
public final class FirstBoss {
    private FirstBoss() {}

    /** The boss's name as the player reads it. */
    public static final String NAME = "The Broodtide";

    /** The awakening line broadcast when the boss spawns, in the mod's red bold style. */
    public static String awakenLine() {
        return "\u00a7c\u00a7l" + NAME + " rises.";
    }

    /** Run-summary fragment when the tier-1 boss was defeated. */
    public static String defeatedLine() {
        return ", Broodtide defeated";
    }
}
