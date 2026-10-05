package com.solme.emberfall.shrine;

import java.util.HashMap;
import java.util.Map;

/**
 * What the three map shrines have changed about one run. Pure numbers and no game types, so the balance can be checked
 * without a server. One instance per run slot, created when the run starts and dropped when it ends.
 *
 * Boss Curse (tier 1 to 4): boss stats x1.2 .. x1.5 and boss add-spawns x1.1 .. x1.5; Silver +15% per tier.
 * Statue of Greed (step 1 to 5): +2 threat per step (threat cap is 20); Silver +10% per step.
 * Challenge: no modifier of its own, it pays Gold, XP and Silver when the fight is cleared.
 *
 * Each shrine can be used once per run, so a tier or step is chosen exactly once and cannot be stacked.
 */
public final class RunModifiers {
    public static final int CURSE_TIERS = 4;
    public static final int GREED_STEPS = 5;

    public static final double THREAT_PER_GREED_STEP = 2.0;
    static final double SILVER_PER_CURSE_TIER = 0.15;
    static final double SILVER_PER_GREED_STEP = 0.10;
    public static final long CHALLENGE_SILVER_BONUS = 25;

    private static final Map<Integer, RunModifiers> BY_SLOT = new HashMap<>();

    private int curseTier = 0;
    private int greedStep = 0;
    private boolean challengeCleared = false;

    public static RunModifiers of(int slot) {
        return BY_SLOT.computeIfAbsent(slot, s -> new RunModifiers());
    }

    public static RunModifiers peek(int slot) {
        return BY_SLOT.get(slot);
    }

    public static void clear(int slot) {
        BY_SLOT.remove(slot);
    }

    public int curseTier() {
        return curseTier;
    }

    public int greedStep() {
        return greedStep;
    }

    public boolean challengeCleared() {
        return challengeCleared;
    }

    /** Returns false (and changes nothing) when the curse was already taken or the tier is out of range. */
    public boolean takeCurse(int tier) {
        if (curseTier != 0 || tier < 1 || tier > CURSE_TIERS) {
            return false;
        }
        curseTier = tier;
        return true;
    }

    /** Returns false (and changes nothing) when greed was already taken or the step is out of range. */
    public boolean takeGreed(int step) {
        if (greedStep != 0 || step < 1 || step > GREED_STEPS) {
            return false;
        }
        greedStep = step;
        return true;
    }

    public void markChallengeCleared() {
        challengeCleared = true;
    }

    /** Boss health and damage multiplier: 1.0 with no curse, then 1.2, 1.3, 1.4, 1.5. */
    public double bossStatMultiplier() {
        return curseTier == 0 ? 1.0 : 1.1 + 0.1 * curseTier;
    }

    /** Boss add-spawn multiplier: 1.0 with no curse, then 1.1, 1.2, 1.3, 1.5 (the user's stated range is 1.1 to 1.5). */
    public double bossSpawnMultiplier() {
        return switch (curseTier) {
            case 1 -> 1.1;
            case 2 -> 1.2;
            case 3 -> 1.3;
            case 4 -> 1.5;
            default -> 1.0;
        };
    }

    public double bonusThreat() {
        return greedStep * THREAT_PER_GREED_STEP;
    }

    /** Multiplier applied to the Silver a run pays out. */
    public double silverMultiplier() {
        return 1.0 + curseTier * SILVER_PER_CURSE_TIER + greedStep * SILVER_PER_GREED_STEP;
    }

    /** The Silver for a run after shrine bonuses: the base is scaled, then the Challenge flat bonus is added once. */
    public long applySilver(long base) {
        long scaled = Math.round(base * silverMultiplier());
        return scaled + (challengeCleared ? CHALLENGE_SILVER_BONUS : 0);
    }
}
