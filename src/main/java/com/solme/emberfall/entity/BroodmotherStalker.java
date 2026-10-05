package com.solme.emberfall.entity;

import com.mojang.math.Transformation;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Display;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.LeapAtTargetGoal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.MeleeAttackGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.WaterAvoidingRandomStrollGoal;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.monster.spider.Spider;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.HashSet;
import java.util.Iterator;
import java.util.Set;
import java.util.UUID;

/**
 * Broodmother Stalker - Elite-track Spider (2026-09-28 elite pass): a fast
 * ambusher (Pounce), a trapper (Silk Snare) and a brood-caller (Brood Call),
 * carrying a head and an egg sac as visible flavor.
 *
 * Visual accessories: the "Spider" minecraft-heads.com head (#48198, chosen by
 * the user over the earlier Peacock Spider candidate) sits on the front of the
 * body and the "Spider Egg Sac" head (#45040) on the back. Spider's model is
 * not humanoid, so armor/head equipment slots are not a reliable way to show
 * them; both are ItemDisplay riders instead (the technique {@link TikiSegment}
 * already proved). That is 2 extra entities per Broodmother, acceptable only
 * because this is a rare Elite-track spawn (fodder/veteran spiders stay
 * entity-free). Both are discarded in {@link #remove}.
 *
 * Abilities:
 * <ul>
 *   <li>Brood Call: the mother rears up and lays up to {@link #BROOD_SPAWN_COUNT} egg sacs behind her; each
 *       sac sits on the ground for a second or two, then hatches a {@link Broodling} that hunts the player.
 *       Hard-capped at {@link #BROOD_MAX_ACTIVE} hatchlings plus waiting sacs at once (dead UUIDs are pruned
 *       before every cast). See {@link #broodCall}.</li>
 *   <li>Silk Snare: a virtual bolt ({@link TrackedProjectiles}, no in-flight
 *       entity) that roots the target with Slowness IV and Mining Fatigue III.
 *       Deliberately places no real cobweb blocks: the codebase's arena
 *       hazards (see {@link ScorchedGround}) never modify terrain, so there is
 *       no revert bookkeeping to lose if the mob dies mid-timer.</li>
 *   <li>Pounce: a short leap at a target inside a mid-range window.</li>
 * </ul>
 */
public class BroodmotherStalker extends Spider {
    private static final double BASE_HEALTH = 100.0;
    private static final double BASE_ATTACK_DAMAGE = 6.0;
    private static final float SCALE = 1.5F;

    private static final long BROOD_COOLDOWN_TICKS = 280L;   // 14s
    private static final int BROOD_MAX_ACTIVE = 3;
    private static final int BROOD_SPAWN_COUNT = 2;
    private static final double BROOD_TRIGGER_RANGE = 18.0;
    private static final int LAY_TICKS = 24;            // 1.2s rear-up before the egg drops
    private static final int HATCH_MIN_TICKS = 24;       // the sac waits 1.2s to 2.0s, then bursts
    private static final int HATCH_MAX_TICKS = 40;
    private static final float EGG_SIZE = 1.1F;
    // Rig part indexes and resting offsets (right, up, forward), matching attachAccessories().
    private static final int RIG_HEAD = 0;
    private static final int RIG_SAC = 1;
    private static final float HEAD_UP = 0.56F, HEAD_FWD = 0.44F;
    private static final float SAC_UP = 0.86F, SAC_BACK = -0.55F;

    private static final long SNARE_COOLDOWN_TICKS = 200L;   // 10s
    private static final double SNARE_RANGE = 16.0;
    private static final double SNARE_SPEED = 0.9;
    private static final double SNARE_HIT_RADIUS = 1.0;
    private static final int SNARE_MAX_TICKS = 40;
    private static final int SNARE_DURATION_TICKS = 80;      // 4s
    private static final int SNARE_SLOWNESS_AMPLIFIER = 3;   // Slowness IV
    private static final int SNARE_FATIGUE_AMPLIFIER = 2;    // Mining Fatigue III

    private static final long POUNCE_COOLDOWN_TICKS = 120L;  // 6s
    private static final double POUNCE_MIN_RANGE = 5.0;
    private static final double POUNCE_MAX_RANGE = 10.0;
    private static final double POUNCE_HORIZONTAL_SPEED = 1.1;
    private static final double POUNCE_VERTICAL_SPEED = 0.45;

    private double statMultiplier = 1.0;
    private long nextBroodAtTick = 0L;
    private long nextSnareAtTick = 0L; // initialised in spawn()
    private long nextPounceAtTick = 0L;
    private final Set<UUID> brood = new HashSet<>();
    /** Egg sacs on the ground waiting to hatch. Each is one ItemDisplay, discarded on hatching or on the mother's death. */
    private final java.util.List<Egg> eggs = new java.util.ArrayList<>();
    private int layTicksLeft = 0;
    private int layingLeft = 0;

    private static final class Egg {
        final Display.ItemDisplay display;
        final Vec3 pos;
        final int hatchAt;
        int age = 0;
        Egg(Display.ItemDisplay display, Vec3 pos, int hatchAt) {
            this.display = display;
            this.pos = pos;
            this.hatchAt = hatchAt;
        }
    }

    /** Glues the head and egg sac to the body (see {@link MobRig}); replaces the old seat-riding displays. */
    private final MobRig rig = new MobRig(this);

    public BroodmotherStalker(EntityType<? extends Spider> type, Level level) {
        super(type, level);
    }

    public static BroodmotherStalker spawn(ServerLevel level, BlockPos pos, double statMultiplier) {
        BroodmotherStalker b = new BroodmotherStalker(ModEntities.BROODMOTHER_STALKER, level);
        b.statMultiplier = statMultiplier;
        b.setPos(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5);
        b.setYRot(level.getRandom().nextFloat() * 360.0F);

        AttributeInstance scale = b.getAttribute(Attributes.SCALE);
        if (scale != null) {
            scale.setBaseValue(SCALE);
        }
        AttributeInstance health = b.getAttribute(Attributes.MAX_HEALTH);
        if (health != null) {
            double hp = BASE_HEALTH * statMultiplier;
            health.setBaseValue(hp);
            b.setHealth((float) hp);
        }
        AttributeInstance dmg = b.getAttribute(Attributes.ATTACK_DAMAGE);
        if (dmg != null) {
            dmg.setBaseValue(BASE_ATTACK_DAMAGE * statMultiplier);
        }

        MobNames.apply(b, "Broodmother Stalker", MobNames.Tier.CORRUPTED);
        b.setPersistenceRequired();

        b.nextSnareAtTick = level.getGameTime() + 40L; // do not open the fight with a snare the instant it spawns
        b.nextBroodAtTick = level.getGameTime() + 60L; // the first Brood Call comes after a beat, so it reads as a move, not a spawn reflex
        level.addFreshEntity(b);
        b.attachAccessories(level);
        level.sendParticles(ParticleTypes.SQUID_INK, b.getX(), b.getY() + 0.8, b.getZ(), 15, 0.6, 0.4, 0.6, 0.02);
        level.playSound(null, b.getX(), b.getY(), b.getZ(),
                SoundEvents.SPIDER_AMBIENT, SoundSource.HOSTILE, 1.6F, 0.5F);
        return b;
    }

    private void attachAccessories(ServerLevel level) {
        // Measured from the decompiled SpiderModel: the head cube is 8x8x8 model units centred 9/16 block up
        // and 7/16 block forward of the feet (negative model-z is the front). A player head item fills
        // its display scale exactly, so 0.5 matches the cube it is covering.
        rig.addItem(level, EliteHeads.broodSpiderHead(), new Vector3f(0.0F, 0.56F, 0.44F), 0.62F, 180.0F, false);
        // The abdomen is 10x8x12 units centred 9/16 back and 9/16 up; the sac sits on top of it, slightly larger.
        rig.addItem(level, EliteHeads.spiderEggSacHead(), new Vector3f(0.0F, 0.86F, -0.55F), 0.7F, 0.0F, false);
    }

    /**
     * Replaces vanilla Spider's goal set. Confirmed from the decompiled source (and reproduced live:
     * {@code light=1.0 target=null} for an entire fight) that three vanilla behaviors would otherwise
     * make this Elite useless in this mod's always-bright arenas:
     * <ol>
     *   <li>{@code Spider.SpiderTargetGoal.canUse()} returns false at brightness &gt;= 0.5, so a vanilla
     *       Spider never acquires a player in daylight.</li>
     *   <li>{@code Spider.SpiderAttackGoal.canContinueToUse()} randomly drops the target (1%/tick) at the
     *       same brightness.</li>
     *   <li>{@code Spider.SpiderAttackGoal.canUse()} requires {@code !isVehicle()}, and
     *       {@code Entity.isVehicle()} is {@code !passengers.isEmpty()}. This mob's head and egg-sac
     *       ItemDisplays are passengers, so vanilla melee could never start.</li>
     * </ol>
     * So this uses the same goals with those three gates removed. Nothing else about the AI changes.
     * The list itself lives in {@link SpiderGoals}, shared with the other Emberfall spiders.
     */
    @Override
    protected void registerGoals() {
        SpiderGoals.install(this, this.goalSelector, this.targetSelector);
    }

    @Override
    protected void customServerAiStep(ServerLevel level) {
        super.customServerAiStep(level);
        rig.tick();
        if (!this.isAlive()) {
            return;
        }
        long now = level.getGameTime();
        if (this.tickCount % 10 == 0) {
            level.sendParticles(ParticleTypes.SQUID_INK, this.getX(), this.getY() + 0.6, this.getZ(),
                    1, 0.5, 0.2, 0.5, 0.0);
        }
        LivingEntity target = this.getTarget();
        tickBrood(level, target);
        if (target == null || !target.isAlive()) {
            return;
        }
        double distSq = this.distanceToSqr(target);

        if (now >= nextBroodAtTick && distSq <= BROOD_TRIGGER_RANGE * BROOD_TRIGGER_RANGE) {
            nextBroodAtTick = now + BROOD_COOLDOWN_TICKS;
            broodCall(level, target);
        }
        if (now >= nextSnareAtTick && distSq <= SNARE_RANGE * SNARE_RANGE && this.hasLineOfSight(target)) {
            nextSnareAtTick = now + SNARE_COOLDOWN_TICKS;
            silkSnare(level, target);
        }
        if (now >= nextPounceAtTick && this.onGround()
                && distSq >= POUNCE_MIN_RANGE * POUNCE_MIN_RANGE && distSq <= POUNCE_MAX_RANGE * POUNCE_MAX_RANGE
                && this.hasLineOfSight(target)) {
            nextPounceAtTick = now + POUNCE_COOLDOWN_TICKS;
            pounce(level, target);
        }
    }

    /**
     * Brood Call, in three visible beats so the player can read it and react:
     * <ol>
     *   <li>Lay ({@link #LAY_TICKS}): the mother rears up and stands still, hissing, with ink dripping from her
     *       back sac. A ground ring marks where the egg will land.</li>
     *   <li>Sac ({@link #HATCH_MIN_TICKS} to {@link #HATCH_MAX_TICKS} later): one egg-sac display sits on the
     *       ground and wobbles, growing restless as it nears hatching.</li>
     *   <li>Hatch: the sac bursts and a {@link Broodling} climbs out already hunting the player.</li>
     * </ol>
     * Only the sac is a new entity, and only while it waits. It is discarded the moment it hatches, and every
     * unhatched sac is discarded if the mother dies ({@link #remove}).
     */
    private void broodCall(ServerLevel level, LivingEntity target) {
        for (Iterator<UUID> it = brood.iterator(); it.hasNext(); ) {
            Entity e = level.getEntity(it.next());
            if (e == null || !e.isAlive()) {
                it.remove();
            }
        }
        // Sacs still waiting count against the cap too, so a fast cooldown cannot stack more than the limit.
        int room = BROOD_MAX_ACTIVE - brood.size() - eggs.size() - layingLeft;
        if (room <= 0) {
            return;
        }
        layingLeft = Math.min(room, BROOD_SPAWN_COUNT);
        layTicksLeft = LAY_TICKS;
        level.playSound(null, this.getX(), this.getY(), this.getZ(),
                SoundEvents.SPIDER_HURT, SoundSource.HOSTILE, 1.4F, 0.4F);
    }

    /** Runs every AI tick: the rear-up while laying, then the egg timers. */
    private void tickBrood(ServerLevel level, LivingEntity target) {
        if (layTicksLeft > 0) {
            layTicksLeft--;
            // Root her in place and rear up: a small held hop, so the move is visible from across the arena.
            this.getNavigation().stop();
            Vec3 dm = this.getDeltaMovement();
            this.setDeltaMovement(dm.x * 0.2, this.onGround() && layTicksLeft % 8 == 0 ? 0.28 : dm.y, dm.z * 0.2);
            this.hurtMarked = true;
            // Rear-up pose from the rig, no extra entity: the head lifts, the back sac heaves and sinks as it drops.
            float prog = 1.0F - (float) layTicksLeft / LAY_TICKS;
            float heave = (float) Math.sin(prog * Math.PI * 3.0) * 0.12F;
            rig.setLocal(RIG_HEAD, 0.0F, HEAD_UP + 0.35F * Math.min(1.0F, prog * 3.0F), HEAD_FWD + 0.12F);
            rig.setLocal(RIG_SAC, 0.0F, SAC_UP - 0.25F * prog + heave, SAC_BACK - 0.30F * prog);
            Vec3 back = backPoint();
            if (layTicksLeft % 2 == 0) {
                level.sendParticles(ParticleTypes.SQUID_INK, back.x, back.y, back.z, 3, 0.15, 0.1, 0.15, 0.01);
                level.sendParticles(ParticleTypes.ITEM_SLIME, back.x, back.y - 0.2, back.z, 2, 0.1, 0.1, 0.1, 0.0);
            }
            if (layTicksLeft == 0) {
                layEggs(level);
                rig.setLocal(RIG_HEAD, 0.0F, HEAD_UP, HEAD_FWD);
                rig.setLocal(RIG_SAC, 0.0F, SAC_UP, SAC_BACK);
            }
        }
        for (Iterator<Egg> it = eggs.iterator(); it.hasNext(); ) {
            Egg egg = it.next();
            egg.age++;
            if (egg.display.isRemoved()) {
                it.remove();
                continue;
            }
            // Wobble faster as it nears hatching, so the burst never comes as a surprise.
            float t = (float) egg.age / egg.hatchAt;
            float tilt = (float) Math.sin(egg.age * (0.6 + 1.6 * t)) * (0.10F + 0.30F * t);
            egg.display.setTransformationInterpolationDelay(0);
            egg.display.setTransformationInterpolationDuration(2);
            egg.display.setTransformation(new Transformation(new Vector3f(0.0F, EGG_SIZE * 0.5F, 0.0F),
                    new Quaternionf().rotationZ(tilt), new Vector3f(EGG_SIZE, EGG_SIZE, EGG_SIZE), new Quaternionf()));
            if (egg.age % 6 == 0) {
                level.sendParticles(ParticleTypes.ITEM_SLIME, egg.pos.x, egg.pos.y + 0.4, egg.pos.z, 1, 0.2, 0.15, 0.2, 0.0);
            }
            if (egg.age >= egg.hatchAt) {
                hatch(level, egg, target);
                it.remove();
            }
        }
    }

    /** The point just behind her back sac, where the egg is laid. */
    private Vec3 backPoint() {
        float yaw = (float) Math.toRadians(this.yBodyRot);
        double back = 1.1 * this.getScale();
        return new Vec3(this.getX() + Math.sin(yaw) * back, this.getY() + 0.5, this.getZ() - Math.cos(yaw) * back);
    }

    private void layEggs(ServerLevel level) {
        int n = layingLeft;
        layingLeft = 0;
        for (int i = 0; i < n; i++) {
            double spread = (i - (n - 1) / 2.0) * 1.6;
            Vec3 back = backPoint();
            float yaw = (float) Math.toRadians(this.yBodyRot);
            double x = back.x + Math.cos(yaw) * spread;
            double z = back.z + Math.sin(yaw) * spread;
            Vec3 ground = new Vec3(x, groundY(level, x, this.getY(), z), z);
            Display.ItemDisplay d = new Display.ItemDisplay(EntityType.ITEM_DISPLAY, level);
            d.setItemStack(EliteHeads.spiderEggSacHead());
            d.setNoGravity(true);
            d.setInvulnerable(true);
            d.setViewRange(1.5F);
            d.setPos(ground.x, ground.y, ground.z);
            d.setTransformation(new Transformation(new Vector3f(0.0F, EGG_SIZE * 0.5F, 0.0F), new Quaternionf(),
                    new Vector3f(EGG_SIZE, EGG_SIZE, EGG_SIZE), new Quaternionf()));
            d.addTag("emberfall_run");
            level.addFreshEntity(d);
            int hatchAt = HATCH_MIN_TICKS + level.getRandom().nextInt(HATCH_MAX_TICKS - HATCH_MIN_TICKS + 1);
            eggs.add(new Egg(d, ground, hatchAt));
            level.sendParticles(ParticleTypes.SQUID_INK, ground.x, ground.y + 0.2, ground.z, 10, 0.25, 0.1, 0.25, 0.02);
        }
        level.playSound(null, this.getX(), this.getY(), this.getZ(),
                SoundEvents.SLIME_SQUISH, SoundSource.HOSTILE, 1.3F, 0.6F);
    }

    /** Walks down from just above the laying spot to the first solid block, so the sac never floats or sinks. */
    private static double groundY(ServerLevel level, double x, double fromY, double z) {
        BlockPos.MutableBlockPos p = new BlockPos.MutableBlockPos(Mth.floor(x), Mth.floor(fromY) + 2, Mth.floor(z));
        for (int i = 0; i < 8; i++) {
            if (!level.getBlockState(p.below()).isAir() && level.getBlockState(p).isAir()) {
                return p.getY();
            }
            p.move(0, -1, 0);
        }
        return fromY;
    }

    private void hatch(ServerLevel level, Egg egg, LivingEntity target) {
        egg.display.discard();
        level.sendParticles(ParticleTypes.ITEM_SLIME, egg.pos.x, egg.pos.y + 0.4, egg.pos.z, 18, 0.3, 0.25, 0.3, 0.05);
        level.sendParticles(ParticleTypes.POOF, egg.pos.x, egg.pos.y + 0.3, egg.pos.z, 6, 0.2, 0.15, 0.2, 0.02);
        level.playSound(null, egg.pos.x, egg.pos.y, egg.pos.z,
                SoundEvents.TURTLE_EGG_CRACK, SoundSource.HOSTILE, 1.6F, 0.7F);
        Broodling b = new Broodling(ModEntities.BROODLING, level);
        b.configure(statMultiplier);
        b.setPos(egg.pos.x, egg.pos.y, egg.pos.z);
        b.setYRot(level.getRandom().nextFloat() * 360.0F);
        level.addFreshEntity(b);
        if (target != null && target.isAlive()) {
            b.setTarget(target);
        }
        brood.add(b.getUUID());
    }

    private void silkSnare(ServerLevel level, LivingEntity target) {
        level.playSound(null, this.getX(), this.getY(), this.getZ(),
                SoundEvents.SPIDER_HURT, SoundSource.HOSTILE, 1.2F, 0.5F);
        Vec3 origin = this.getEyePosition();
        Vec3 velocity = target.getEyePosition().subtract(origin).normalize().scale(SNARE_SPEED);
        TrackedProjectiles.launchAtTarget(level, origin, velocity, this, target,
                SNARE_HIT_RADIUS, SNARE_MAX_TICKS, ParticleTypes.ITEM_SNOWBALL,
                impact -> onSnareHit(level, target));
    }

    private void onSnareHit(ServerLevel level, LivingEntity target) {
        target.addEffect(new MobEffectInstance(MobEffects.SLOWNESS,
                SNARE_DURATION_TICKS, SNARE_SLOWNESS_AMPLIFIER, false, true, true));
        target.addEffect(new MobEffectInstance(MobEffects.MINING_FATIGUE,
                SNARE_DURATION_TICKS, SNARE_FATIGUE_AMPLIFIER, false, true, true));
        // Cobweb-look burst around the victim, particles only (no terrain changes - see class javadoc).
        level.sendParticles(ParticleTypes.ITEM_COBWEB, target.getX(), target.getY() + 0.6, target.getZ(),
                30, 0.5, 0.6, 0.5, 0.02);
        level.playSound(null, target.getX(), target.getY(), target.getZ(),
                SoundEvents.COBWEB_PLACE, SoundSource.HOSTILE, 1.2F, 0.8F);
    }

    private void pounce(ServerLevel level, LivingEntity target) {
        Vec3 toTarget = target.position().subtract(this.position());
        Vec3 flat = new Vec3(toTarget.x, 0.0, toTarget.z);
        if (flat.lengthSqr() < 1.0E-4) {
            return;
        }
        Vec3 push = flat.normalize().scale(POUNCE_HORIZONTAL_SPEED);
        this.setDeltaMovement(push.x, POUNCE_VERTICAL_SPEED, push.z);
        this.hurtMarked = true;
        level.playSound(null, this.getX(), this.getY(), this.getZ(),
                SoundEvents.SPIDER_AMBIENT, SoundSource.HOSTILE, 1.3F, 1.3F);
        level.sendParticles(ParticleTypes.CLOUD, this.getX(), this.getY() + 0.2, this.getZ(), 6, 0.3, 0.1, 0.3, 0.02);
    }

    @Override
    public void remove(Entity.RemovalReason reason) {
        rig.discard();
        for (Egg egg : eggs) {
            egg.display.discard();
        }
        eggs.clear();
        super.remove(reason);
    }
}
