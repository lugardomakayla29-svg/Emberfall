package com.solme.emberfall.entity;

import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.GoalSelector;
import net.minecraft.world.entity.ai.goal.LeapAtTargetGoal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.MeleeAttackGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.WaterAvoidingRandomStrollGoal;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.monster.spider.Spider;
import net.minecraft.world.entity.player.Player;

/**
 * The goal set every Emberfall spider uses. Vanilla {@code Spider} has three behaviours that make it useless in
 * this mod's always-bright arenas (confirmed from the decompiled source, and measured live: six horde spiders
 * between 9 and 26 blocks from a survival player all read {@code target=none} at daytime tick 1389 with a follow
 * range of 48, while a zombie and a skeleton in the same test had the player targeted):
 * <ol>
 *   <li>{@code Spider.SpiderTargetGoal.canUse()} returns false at brightness &gt;= 0.5, so it never acquires a
 *       player in daylight.</li>
 *   <li>{@code Spider.SpiderAttackGoal.canContinueToUse()} randomly drops the target (1% per tick) at the same
 *       brightness.</li>
 *   <li>{@code Spider.SpiderAttackGoal.canUse()} requires {@code !isVehicle()}, which fails for any spider that
 *       carries rider displays (the Broodmother's head and sac).</li>
 * </ol>
 * These are the same goals with those three gates removed and nothing else changed.
 */
public final class SpiderGoals {
    private SpiderGoals() {}

    /**
     * Installs the goal set. The selectors are passed in because {@code goalSelector} and {@code targetSelector} are
     * protected in {@code Mob}, so only the spider's own {@code registerGoals} can read them.
     */
    public static void install(Spider spider, GoalSelector goals, GoalSelector targets) {
        goals.addGoal(1, new FloatGoal(spider));
        goals.addGoal(3, new LeapAtTargetGoal(spider, 0.4F));
        goals.addGoal(4, new MeleeAttackGoal(spider, 1.0, true));
        goals.addGoal(5, new WaterAvoidingRandomStrollGoal(spider, 0.8));
        goals.addGoal(6, new LookAtPlayerGoal(spider, Player.class, 8.0F));
        goals.addGoal(6, new RandomLookAroundGoal(spider));
        targets.addGoal(1, new HurtByTargetGoal(spider));
        targets.addGoal(2, new NearestAttackableTargetGoal<>(spider, Player.class, true));
    }
}
