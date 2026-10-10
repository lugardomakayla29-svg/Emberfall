package com.solme.emberfall.progression;

/**
 * The single definition of how a run pays Silver. The server credits from it and the run-end screen reads the same
 * class to show each line's share, so the breakdown on screen can never disagree with the payout.
 *
 * Pays for what the player DID, not just the clock: a short run that fought well still earns something.
 * Every part rounds down, and the total is the sum of the parts.
 */
public final class RewardFormula {
    public static final long PER_10_SECONDS = 1;
    public static final int KILLS_PER_SILVER = 5;
    public static final long PER_LEVEL = 3;
    public static final int GOLD_PER_SILVER = 20;
    public static final long HYDRA_BONUS = 50;
    public static final long DEVOURER_BONUS = 150;

    private RewardFormula() {}

    public static long forTime(long seconds) {
        return Math.max(0, seconds / 10) * PER_10_SECONDS;
    }

    public static long forKills(int kills) {
        return Math.max(0, kills) / KILLS_PER_SILVER;
    }

    public static long forLevels(int level) {
        return Math.max(0, level) * PER_LEVEL;
    }

    public static long forGold(int gold) {
        return Math.max(0, gold) / GOLD_PER_SILVER;
    }

    public static long forBosses(boolean hydra, boolean devourer) {
        return (hydra ? HYDRA_BONUS : 0) + (devourer ? DEVOURER_BONUS : 0);
    }

    /** The first boss's name on the run-end screen. The flag is still called "hydra" on the wire (renaming it would change the packet), but the boss is the Broodtide. */
    public static final String FIRST_BOSS_NAME = "The Broodtide";
    public static final String SECOND_BOSS_NAME = "The Devourer";

    /**
     * The "Bosses defeated" line: the names of the bosses that fell, in fight order, joined with a comma; empty when none did. Pure, so a check can pin the text
     * (the screen once said "Ember Guardian" after the Broodtide had replaced it).
     */
    public static String bossNames(boolean firstDown, boolean devourerDown) {
        if (firstDown && devourerDown) {
            return FIRST_BOSS_NAME + ", " + SECOND_BOSS_NAME;
        }
        return firstDown ? FIRST_BOSS_NAME : devourerDown ? SECOND_BOSS_NAME : "";
    }

    public static long total(long seconds, int level, int kills, int gold, boolean hydra, boolean devourer) {
        return forTime(seconds) + forKills(kills) + forLevels(level) + forGold(gold) + forBosses(hydra, devourer);
    }
}
