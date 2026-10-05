package com.solme.emberfall.entity;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.LeapAtTargetGoal;
import net.minecraft.world.entity.ai.goal.MeleeAttackGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.monster.spider.Spider;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;

/**
 * A Broodmother's hatchling. A REAL emberfall mob (the first version was a vanilla CaveSpider, which the run
 * purge deleted on arrival and which no mod weapon could target, since both only recognise the emberfall
 * namespace). Small, quick and fragile: it exists to swarm, not to threaten alone.
 *
 * <p>AI: vanilla Spider goals minus the two brightness gates (a spider never acquires a player in daylight, and
 * randomly drops its target there), the same fix {@link BroodmotherStalker} carries. It hunts a player from any
 * distance ({@link #FOLLOW_RANGE}) so a hatchling never idles in a corner of the arena.
 *
 * <p>Entity budget: no rider displays, no rig. A lifespan cap removes a survivor after {@link #LIFESPAN_TICKS}
 * so a long fight cannot accumulate them.
 */
public class Broodling extends Spider {
    public static final double BASE_HEALTH = 8.0;
    public static final double BASE_DAMAGE = 3.0;
    public static final float SCALE = 0.55F;
    /** Reaches across the whole 28 block arena, so a hatchling always has somewhere to go. */
    public static final double FOLLOW_RANGE = ModEntities.HUNT_RANGE;
    /** 30s. A survivor is discarded, not left to pile up over a long boss fight. */
    public static final int LIFESPAN_TICKS = 600;

    public Broodling(EntityType<? extends Spider> type, Level level) {
        super(type, level);
    }

    /** Applies the tuned stats. Called right after construction and before {@code addFreshEntity}. */
    public void configure(double statMultiplier) {
        AttributeInstance scale = this.getAttribute(Attributes.SCALE);
        if (scale != null) {
            scale.setBaseValue(SCALE);
        }
        AttributeInstance health = this.getAttribute(Attributes.MAX_HEALTH);
        if (health != null) {
            health.setBaseValue(BASE_HEALTH * statMultiplier);
            this.setHealth((float) (BASE_HEALTH * statMultiplier));
        }
        AttributeInstance dmg = this.getAttribute(Attributes.ATTACK_DAMAGE);
        if (dmg != null) {
            dmg.setBaseValue(BASE_DAMAGE * statMultiplier);
        }
        AttributeInstance follow = this.getAttribute(Attributes.FOLLOW_RANGE);
        if (follow != null) {
            follow.setBaseValue(FOLLOW_RANGE);
        }
        this.setPersistenceRequired();
    }

    @Override
    protected void registerGoals() {
        SpiderGoals.install(this, this.goalSelector, this.targetSelector);
    }

    @Override
    protected void customServerAiStep(ServerLevel level) {
        super.customServerAiStep(level);
        if (this.tickCount >= LIFESPAN_TICKS) {
            level.sendParticles(ParticleTypes.POOF, this.getX(), this.getY() + 0.2, this.getZ(), 4, 0.15, 0.1, 0.15, 0.01);
            this.discard();
        }
    }
}
