package com.solme.emberfall.entity;

import java.util.EnumSet;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.control.FlyingMoveControl;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.ai.navigation.FlyingPathNavigation;
import net.minecraft.world.entity.ai.navigation.PathNavigation;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.pathfinder.PathType;
import net.minecraft.world.phys.Vec3;

/**
 * Eighth horde filler: a small flying imp that arrives in packs. One is a nuisance; a pack is an area problem that favours
 * splash and projectile weapons over short-reach melee, because it hovers above the ground.
 *
 * <ul>
 *   <li><b>Flight</b> uses the same public pieces a vanilla Bee does (read from its bytecode): a {@link FlyingMoveControl}
 *       (max turn 20, hovers in place), a {@link FlyingPathNavigation} with doors and floating off, and the
 *       {@code FLYING_SPEED} attribute. It extends {@link Monster} rather than {@code Vex}, whose {@code tick()} sets
 *       {@code noPhysics} (walls would not stop it) and whose move control is a private inner class.</li>
 *   <li><b>Pack size is clamped</b> to the arena's hostile-cap headroom by {@link #packSize}, so a pack can never push the
 *       arena over {@code HOSTILE_CAP}.</li>
 *   <li><b>Entity budget</b>: no rig, no displays, no passengers. A {@value #LIFESPAN_TICKS} tick lifespan discards a
 *       survivor, the same rule {@link Broodling} uses, so a long fight cannot accumulate them.</li>
 * </ul>
 */
public class HordeImp extends Monster {
    public static final double BASE_HEALTH = 6.0;
    public static final double BASE_DAMAGE = 2.0;
    public static final float SCALE = 0.6F;
    private static final double VETERAN_HEALTH_MULT = 2.5;
    private static final double VETERAN_DAMAGE_MULT = 1.6;
    public static final int MIN_PACK = 3;
    public static final int MAX_PACK = 5;
    /** 30 s. A survivor is discarded, not left to pile up over a long boss fight. */
    public static final int LIFESPAN_TICKS = 600;
    /** How far above the target's eyes an attacking imp hovers while it circles. */
    static final double HOVER_ABOVE_EYE = 0.6;
    private static final boolean TEST_MODE = Boolean.getBoolean("emberfall.testMode");

    private boolean veteran = false;

    public HordeImp(EntityType<? extends Monster> type, Level level) {
        super(type, level);
        this.moveControl = new FlyingMoveControl(this, 20, true);
        this.setPathfindingMalus(PathType.DANGER_FIRE, -1.0F);
        this.setPathfindingMalus(PathType.WATER, -1.0F);
    }

    public static AttributeSupplier.Builder createImpAttributes() {
        return Monster.createMonsterAttributes()
                .add(Attributes.MAX_HEALTH, BASE_HEALTH)
                .add(Attributes.ATTACK_DAMAGE, BASE_DAMAGE)
                .add(Attributes.MOVEMENT_SPEED, 0.3)
                .add(Attributes.FLYING_SPEED, 0.55);
    }

    @Override
    protected PathNavigation createNavigation(Level level) {
        FlyingPathNavigation nav = new FlyingPathNavigation(this, level);
        nav.setCanOpenDoors(false);
        nav.setCanFloat(false);
        return nav;
    }

    /** Applies scale and marks the imp as a run mob. Called once before {@code addFreshEntity}. */
    public void prepare() {
        AttributeInstance scale = this.getAttribute(Attributes.SCALE);
        if (scale != null) {
            scale.setBaseValue(SCALE);
        }
        this.setHealth(this.getMaxHealth());
        this.setPersistenceRequired();
        MobNames.apply(this, "Horde Imp", MobNames.Tier.ELITE);
    }

    public void becomeVeteran() {
        this.veteran = true;
        AttributeInstance health = this.getAttribute(Attributes.MAX_HEALTH);
        if (health != null) {
            health.setBaseValue(health.getBaseValue() * VETERAN_HEALTH_MULT);
            this.setHealth((float) health.getValue());
        }
        AttributeInstance damage = this.getAttribute(Attributes.ATTACK_DAMAGE);
        if (damage != null) {
            damage.setBaseValue(damage.getBaseValue() * VETERAN_DAMAGE_MULT);
        }
        MobNames.apply(this, "Veteran Horde Imp", MobNames.Tier.VETERAN);
    }

    public boolean isVeteran() {
        return veteran;
    }

    @Override
    public boolean isNoGravity() {
        return true;
    }

    @Override
    protected void registerGoals() {
        this.goalSelector.addGoal(1, new HoverBiteGoal(this));
        this.targetSelector.addGoal(1, new HurtByTargetGoal(this));
        this.targetSelector.addGoal(2, new NearestAttackableTargetGoal<>(this, Player.class, true));
    }

    @Override
    protected void customServerAiStep(ServerLevel level) {
        super.customServerAiStep(level);
        if (this.tickCount >= LIFESPAN_TICKS) {
            level.sendParticles(ParticleTypes.POOF, this.getX(), this.getY() + 0.2, this.getZ(), 4, 0.15, 0.1, 0.15, 0.01);
            this.discard();
        }
    }

    /**
     * How many imps a pack may hold right now: a random {@link #MIN_PACK} to {@link #MAX_PACK}, cut down to the room left
     * under the hostile cap. Zero when the arena is full, so the caller skips the event like any other capped spawn.
     */
    public static int packSize(net.minecraft.util.RandomSource random, int hostileCap, int currentHostile) {
        int wanted = MIN_PACK + random.nextInt(MAX_PACK - MIN_PACK + 1);
        return Math.max(0, Math.min(wanted, hostileCap - currentHostile));
    }

    /** Flies to a point just above the target's eyes, then bites when it is in reach. One goal, no path recalculation spam. */
    private static final class HoverBiteGoal extends Goal {
        private static final int ATTACK_COOLDOWN_TICKS = 20;
        private final HordeImp imp;
        private int cooldown = 0;

        HoverBiteGoal(HordeImp imp) {
            this.imp = imp;
            this.setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
        }

        @Override
        public boolean canUse() {
            LivingEntity target = imp.getTarget();
            return target != null && target.isAlive();
        }

        @Override
        public boolean canContinueToUse() {
            return canUse();
        }

        @Override
        public void stop() {
            imp.getNavigation().stop();
        }

        @Override
        public boolean requiresUpdateEveryTick() {
            return true;
        }

        @Override
        public void tick() {
            LivingEntity target = imp.getTarget();
            if (target == null) {
                return;
            }
            imp.getLookControl().setLookAt(target, 30.0F, 30.0F);
            if (cooldown > 0) {
                cooldown--;
            }
            // FlyingMoveControl.tick() resets its operation to WAIT every time it consumes a request (bytecode offset 11), and in WAIT
            // it applies no forward input. So the request must be re-issued EVERY tick; re-aiming every 4 ticks measured a tenth of speed.
            imp.getMoveControl().setWantedPosition(target.getX(), target.getEyeY() + HOVER_ABOVE_EYE, target.getZ(), 1.0);
            double reach = imp.getBbWidth() + target.getBbWidth() + 0.6;
            if (cooldown <= 0 && imp.distanceToSqr(target.getX(), target.getY(0.5), target.getZ()) <= reach * reach + 1.0) {
                cooldown = ATTACK_COOLDOWN_TICKS;
                imp.doHurtTarget((ServerLevel) imp.level(), target);
                if (TEST_MODE) {
                    com.solme.emberfall.EmberfallMod.LOGGER.info("IMP_TEST bite tick={}", imp.level().getGameTime());
                }
            }
        }
    }
}
