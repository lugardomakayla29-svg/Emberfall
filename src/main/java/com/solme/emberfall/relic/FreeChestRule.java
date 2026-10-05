package com.solme.emberfall.relic;

/**
 * When a kill or shrine leaves a FREE chest behind. Pure, so the numbers are provable without a server.
 *
 * A free chest is a relic for no gold, so it must stay a treat, not a second economy. Rules:
 *  - a BOSS always drops one (a boss is a milestone);
 *  - an ELITE drops one with a chance that starts at {@link #ELITE_BASE} (0.15) and falls 10% per free chest already given,
 *    so a long run cannot snowball. Measured over 30000 runs: 0.7 / 2.0 / 3.7 elite chests for 5 / 15 / 30 elites, about 7.7
 *    free chests in a 30-elite run including the boss and 3 shrines, and only 1% of runs ever touch the cap;
 *  - a cleared SHRINE challenge always drops one;
 *  - a hard cap per run ({@link #RUN_CAP}) stops the total, however the run goes.
 */
public final class FreeChestRule {
    private FreeChestRule() {}

    public static final double ELITE_BASE = 0.15;
    /** Each free chest already given this run multiplies the elite chance by this. */
    public static final double ELITE_DECAY = 0.90;
    public static final int RUN_CAP = 12;

    public enum Source { ELITE, BOSS, SHRINE }

    /** Chance (0 to 1) that this source leaves a free chest, given how many the run already gave. */
    public static double chance(Source source, int alreadyGiven) {
        if (alreadyGiven >= RUN_CAP) {
            return 0.0;
        }
        return switch (source) {
            case BOSS, SHRINE -> 1.0;
            case ELITE -> ELITE_BASE * Math.pow(ELITE_DECAY, Math.max(0, alreadyGiven));
        };
    }

    public static boolean drops(Source source, int alreadyGiven, double roll) {
        return roll < chance(source, alreadyGiven);
    }
}
