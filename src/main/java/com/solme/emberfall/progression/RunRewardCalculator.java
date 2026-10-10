package com.solme.emberfall.progression;

import com.solme.emberfall.EmberfallMod;
import com.solme.emberfall.world.RunTelemetry;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

/**
 * Design doc 10.1 v1 formula: 1 meta-currency per 10 seconds survived
 * (rounded down) plus a flat bonus for each boss this run's player
 * genuinely defeated (per the retired HydraBrain/
 * {@link com.solme.emberfall.entity.DevourerBrain#wasDefeated}, not merely a
 * rig discarded early by run teardown - see {@link
 * com.solme.emberfall.boss.GuardianBossFight#tickAll}/{@link
 * com.solme.emberfall.boss.DevourerBossFight#tickAll}).
 *
 * Two separate boss bonuses (not one flat "a boss died" bonus) since
 * 2026-09-28's two-tier escalation: Hydra is the tier-1 boss every run
 * reaches on a fixed time mark, Devourer is the harder tier-2 boss that
 * only appears after Hydra falls (see {@link
 * com.solme.emberfall.wave.WaveDirector}'s javadoc) - a run that clears
 * both should be rewarded for both, and Devourer's bonus is deliberately
 * larger to reflect it being the harder, later fight, not a cosmetic
 * reskin of the same risk.
 *
 * Deliberately simple and explicitly a first pass, not a final balance
 * number - real tuning against playtesting data is Section 12's later
 * work, not this milestone's. The design doc's "full clear bonus" is not
 * implemented yet: there's no existing signal anywhere in the codebase for
 * "cleared every wave the director had queued", so inventing one wasn't in
 * scope for this pass - only the reward inputs that already have a real,
 * tested signal to hang off of (elapsed time, each boss's own defeat flag).
 */
public final class RunRewardCalculator {

    private RunRewardCalculator() {}

    /** Computes and credits this run's reward for {@code player}, and returns the amount earned. */
    public static long awardRunReward(MinecraftServer server, ServerPlayer player, int slot) {
        long seconds = RunTelemetry.elapsedSeconds(slot);
        boolean hydraDefeated = RunTelemetry.wasHydraDefeated(slot);
        boolean devourerDefeated = RunTelemetry.wasDevourerDefeated(slot);
        // Read what the run produced BEFORE anything clears it. Same numbers the run-end screen shows.
        int level = player.experienceLevel;
        int kills = com.solme.emberfall.world.RunStats.kills(player);
        int gold = com.solme.emberfall.pickup.PickupSystem.gold(player);
        long baseReward = RewardFormula.total(seconds, level, kills, gold, hydraDefeated, devourerDefeated);
        long reward = baseReward;
        // Shrine bonuses (Boss Curse tier, Statue of Greed step, cleared Challenge) raise what the run pays.
        com.solme.emberfall.shrine.RunModifiers mods = com.solme.emberfall.shrine.RunModifiers.peek(slot);
        if (mods != null) {
            reward = mods.applySilver(reward);
        }
        // Final Swarm: leaving through the portal pays the multiplier reached (never below the plain reward). A death or a
        // disconnect pays the plain reward, so staying is a gamble with no hidden penalty and no free ride.
        int escapeTenths = com.solme.emberfall.wave.SwarmPortal.takeEscape(player.getUUID());
        long beforeEscape = reward;
        if (escapeTenths > 0) {
            reward = com.solme.emberfall.wave.FinalSwarm.cashOut(reward, escapeTenths);
            if (Boolean.getBoolean("emberfall.testMode")) {
                EmberfallMod.LOGGER.info("SWARM_TEST payout tenths={} before={} after={}", escapeTenths, beforeEscape, reward);
            }
        }
        long newBalance = MetaProgressionData.get(server).addCurrency(player.getUUID(), reward);
        EmberfallMod.LOGGER.info(
                "{} earned {} meta-currency ({}s survived{}{}) - new balance {} [base {}, shrine bonus {}]",
                player.getGameProfile().name(), reward, seconds,
                hydraDefeated ? com.solme.emberfall.boss.FirstBoss.defeatedLine() : "",
                devourerDefeated ? ", Devourer defeated" : "",
                newBalance, baseReward, reward - baseReward);
        return reward;
    }
}
