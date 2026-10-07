package com.solme.emberfall.relic;

/**
 * Whether a chest that was just looted comes back. Pure, so the numbers are provable without a server.
 *
 * A looted chest has a small chance to stand again, ONCE per run. The rules:
 *  - each eligible opening rolls {@link #CHANCE} (10%) until one hits, and from then on the run never rolls again, so a run
 *    gets at most {@link #PER_RUN_CAP} respawn (the cap is a hard guarantee, not a probability);
 *  - only PAID and GOLD chests can come back. A FREE chest is a relic for no gold and is already paced by
 *    {@link FreeChestRule} ("a treat, not a second economy"), so a free chest that re-armed itself would be a second source of
 *    free relics that skips that rule and its run cap of {@link FreeChestRule#RUN_CAP};
 *  - the chest comes back as the same kind in the same place, still costs the current price, and still counts toward the price.
 *
 * The 10% is a placeholder pacing number in the same spirit as the other tuning constants in this mod: it makes the one
 * respawn land in about 27% of runs that open 3 eligible chests and 57% of runs that open 8, not a balanced figure.
 */
public final class ChestRespawnRule {
    private ChestRespawnRule() {}

    /** Chance (0 to 1) that one eligible opening brings its chest back, while the run still has its respawn. */
    public static final double CHANCE = 0.10;
    /** The most respawns a run can have. */
    public static final int PER_RUN_CAP = 1;

    /** Only chests that cost gold can come back; a free chest never does. */
    public static boolean eligible(ChestOpening.Kind kind) {
        return kind == ChestOpening.Kind.PAID || kind == ChestOpening.Kind.GOLD;
    }

    /** Chance that opening a chest of this kind brings it back, given how many respawns the run already used. */
    public static double chance(ChestOpening.Kind kind, int respawnsUsed) {
        if (!eligible(kind) || respawnsUsed >= PER_RUN_CAP) {
            return 0.0;
        }
        return CHANCE;
    }

    /** True if this opening brings the chest back. {@code roll} is a uniform number in [0, 1). */
    public static boolean respawns(ChestOpening.Kind kind, int respawnsUsed, double roll) {
        return roll < chance(kind, respawnsUsed);
    }
}
