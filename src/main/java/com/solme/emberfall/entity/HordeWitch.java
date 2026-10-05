package com.solme.emberfall.entity;

import com.mojang.math.Transformation;
import com.solme.emberfall.combat.Fx;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Display;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Witch;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Horde Witch: the fodder version of the Umbral Magus. A ranged caster that keeps its distance and circles four
 * differently coloured Star Bits around itself, then lobs them at the player one by one. Each star lands on a
 * marked spot and bursts into shards over a small radius (1.5 to 2.5 blocks, see {@link #BURST_REACH}).
 *
 * <p>Reuses {@link StarBitLob} for the throw, the arc, the landing ring and the burst, so its damage code is the
 * same code the Magus uses, and that code only ever selects {@code Player}s: the shards can never hurt another
 * expedition mob. The Witch itself fights nobody but players (see {@code world/RunMobTeam} for the run-wide rule).
 *
 * <p>Vanilla {@link Witch} is a raid support mob. Its potion throw is switched off by removing the vanilla ranged goal
 * and no-oping {@link #performRangedAttack}. {@code super.registerGoals()} still runs, because {@code Witch.aiStep}
 * reads two goal fields that only it assigns.
 *
 * <p>Cost: four item displays per witch (cosmetic orbit only, view range kept short), all discarded in
 * {@link #remove} on any removal path, plus the tag {@code emberfall_run} so the run teardown sweeps strays.
 */
public class HordeWitch extends Witch {
    /** Stars circling the witch at once. */
    public static final int ORBIT_COUNT = 4;
    /** How far a burst reaches; the honest damage radius, and the warning ring is drawn at the same size. */
    public static final double BURST_REACH = 2.0;
    private static final double ORBIT_RADIUS = 1.1;
    private static final double ORBIT_HEIGHT = 2.1;
    private static final int THROW_EVERY_TICKS = 44;       // one star roughly every 2.2s
    private static final int REARM_AFTER_TICKS = 120;      // all four gone: pause, then a fresh set circles in
    private static final double THROW_RANGE = 18.0;
    private static final double KEEP_DISTANCE = 9.0;
    private static final double SWAY_PULL_FROM = 11.5;   // beyond this, the sway leans inward
    private static final double APPROACH_DISTANCE = 14.0;   // inside THROW_RANGE (18) so it always ends up able to throw
    private static final float DISPLAY_SCALE = 0.55F;
    private static final double VETERAN_HEALTH_MULT = 2.0;
    private static final double VETERAN_DAMAGE_MULT = 1.5;

    private boolean veteran = false;
    private double damageScale = 1.0;
    private long nextThrowAtTick = 0L;
    private long rearmAtTick = 0L;
    private long nextCackleAtTick = 0L;
    private static final int CACKLE_COOLDOWN_TICKS = 100;   // at most one cackle per 5 s
    private static final float STRUGGLE_HEALTH_FRACTION = 0.4F;
    private int orbitPhase = 0;
    private final List<StarBitLob.Colour> colours = new ArrayList<>();
    private final List<Display.ItemDisplay> orbiters = new ArrayList<>();

    public HordeWitch(EntityType<? extends Witch> type, Level level) {
        super(type, level);
    }

    /** Called right after construction and before addFreshEntity, like the other horde fillers. */
    public void becomeVeteran() {
        this.veteran = true;
        this.damageScale = VETERAN_DAMAGE_MULT;
        AttributeInstance health = this.getAttribute(Attributes.MAX_HEALTH);
        if (health != null) {
            health.setBaseValue(health.getBaseValue() * VETERAN_HEALTH_MULT);
            this.setHealth((float) health.getValue());
        }
        MobNames.apply(this, "Veteran Horde Witch", MobNames.Tier.ARCANE);
    }

    public boolean isVeteran() {
        return veteran;
    }

    /** Number of orbit displays currently alive (used by the tests). */
    public int orbitingStars() {
        int n = 0;
        for (Display.ItemDisplay d : orbiters) {
            if (d != null && !d.isRemoved()) {
                n++;
            }
        }
        return n;
    }

    @Override
    protected void registerGoals() {
        // super.registerGoals() MUST run: Witch.aiStep dereferences two private goal fields that only Witch.registerGoals
        // assigns (healRaidersGoal, attackPlayersGoal) with no null check. Skipping it crashed the server in "Ticking
        // entity" (verified live and in the bytecode). So keep the vanilla set, then strip what a fodder caster must not do.
        super.registerGoals();
        this.goalSelector.removeAllGoals(g -> g instanceof net.minecraft.world.entity.ai.goal.RangedAttackGoal);
        this.goalSelector.addGoal(2, new KeepDistanceGoal(this));
        // The vanilla stroll goal walked a hunting witch away from its target; only stroll when nothing is being hunted.
        this.goalSelector.removeAllGoals(g -> g instanceof net.minecraft.world.entity.ai.goal.WaterAvoidingRandomStrollGoal);
        this.goalSelector.addGoal(6, new net.minecraft.world.entity.ai.goal.WaterAvoidingRandomStrollGoal(this, 0.8) {
            @Override
            public boolean canUse() {
                return HordeWitch.this.getTarget() == null && super.canUse();
            }
        });
    }

    /** The witch's laugh: WITCH_CELEBRATE at a slightly higher pitch, plus a few happy-villager sparks. */
    private void cackle(ServerLevel level, long now) {
        nextCackleAtTick = now + CACKLE_COOLDOWN_TICKS;
        Fx.sound(level, this.position(), SoundEvents.WITCH_CELEBRATE, 1.0F, 1.1F + this.getRandom().nextFloat() * 0.3F);
        level.sendParticles(net.minecraft.core.particles.ParticleTypes.HAPPY_VILLAGER,
                this.getX(), this.getY() + 2.2, this.getZ(), 4, 0.3, 0.2, 0.3, 0.0);
    }

    /** A witch that lands or takes a hit may laugh, but never more than once per cooldown. */
    @Override
    public boolean hurtServer(ServerLevel level, net.minecraft.world.damagesource.DamageSource source, float amount) {
        boolean hurt = super.hurtServer(level, source, amount);
        if (hurt && this.isAlive() && level.getGameTime() >= nextCackleAtTick) {
            cackle(level, level.getGameTime());
        }
        return hurt;
    }

    /** The vanilla potion throw is replaced by the Star Bit cycle in {@link #customServerAiStep}. */
    @Override
    public void performRangedAttack(LivingEntity target, float distanceFactor) {
        // intentionally empty
    }

    @Override
    protected void customServerAiStep(ServerLevel level) {
        super.customServerAiStep(level);
        if (!this.isAlive()) {
            return;
        }
        long now = level.getGameTime();
        LivingEntity target = this.getTarget();
        boolean hasTarget = target != null && target.isAlive() && target instanceof Player;

        // Cackle when the player is struggling (under 40% health) and within earshot; rate-limited.
        if (hasTarget && now >= nextCackleAtTick && this.distanceToSqr(target) <= THROW_RANGE * THROW_RANGE
                && target.getHealth() < target.getMaxHealth() * STRUGGLE_HEALTH_FRACTION) {
            cackle(level, now);
        }

        // Refill the ring of four when the last set is spent and the pause is over.
        if (orbiters.isEmpty() && hasTarget && now >= rearmAtTick) {
            spawnOrbit(level);
            nextThrowAtTick = now + THROW_EVERY_TICKS;
        }

        tickOrbit(level);

        if (!orbiters.isEmpty() && hasTarget && now >= nextThrowAtTick
                && this.distanceToSqr(target) <= THROW_RANGE * THROW_RANGE) {
            throwOne(level, target);
            nextThrowAtTick = now + THROW_EVERY_TICKS;
            if (orbiters.isEmpty()) {
                rearmAtTick = now + REARM_AFTER_TICKS;
            }
        }
    }

    private void spawnOrbit(ServerLevel level) {
        StarBitLob.Colour[] all = StarBitLob.Colour.values();
        List<StarBitLob.Colour> pool = new ArrayList<>(List.of(all));
        Collections.shuffle(pool, new java.util.Random(this.getRandom().nextLong()));
        colours.clear();
        orbiters.clear();
        for (int i = 0; i < ORBIT_COUNT; i++) {
            StarBitLob.Colour colour = pool.get(i % pool.size());
            Display.ItemDisplay d = new Display.ItemDisplay(EntityType.ITEM_DISPLAY, level);
            Vec3 at = orbitPoint(i, 0);
            d.setPos(at.x, at.y, at.z);
            d.addTag("emberfall_run");
            d.addTag("emberfall_witchstar");
            d.setItemStack(colour.stack());
            d.setViewRange(1.5F);
            d.setPosRotInterpolationDuration(2);
            d.setBillboardConstraints(Display.BillboardConstraints.CENTER);
            d.setTransformation(new Transformation(new Vector3f(), new Quaternionf(),
                    new Vector3f(DISPLAY_SCALE, DISPLAY_SCALE, DISPLAY_SCALE), new Quaternionf()));
            d.setNoGravity(true);
            d.setInvulnerable(true);
            level.addFreshEntity(d);
            colours.add(colour);
            orbiters.add(d);
        }
        Fx.sound(level, this.position(), SoundEvents.EVOKER_PREPARE_SUMMON, 0.8F, 1.6F);
    }

    private Vec3 orbitPoint(int index, int phase) {
        double a = Math.toRadians(phase * 9.0) + (Math.PI * 2.0 / ORBIT_COUNT) * index;
        return new Vec3(this.getX() + Math.cos(a) * ORBIT_RADIUS, this.getY() + ORBIT_HEIGHT, this.getZ() + Math.sin(a) * ORBIT_RADIUS);
    }

    private void tickOrbit(ServerLevel level) {
        if (orbiters.isEmpty()) {
            return;
        }
        orbitPhase++;
        // Slots are fixed at spawn time, so a thrown star leaves a gap instead of the ring re-spacing.
        for (int i = 0; i < orbiters.size(); i++) {
            Display.ItemDisplay d = orbiters.get(i);
            if (d == null || d.isRemoved()) {
                continue;
            }
            Vec3 p = orbitPoint(i, orbitPhase);
            d.setPos(p.x, p.y, p.z);
            if (orbitPhase % 3 == 0) {
                level.sendParticles(new net.minecraft.core.particles.DustParticleOptions(colours.get(i).rgbForWarning(), 0.7F),
                        p.x, p.y, p.z, 1, 0.0, 0.0, 0.0, 0.0);
            }
        }
    }

    /** Releases the next live star: its display is removed and a real arcing Star Bit takes over. */
    private void throwOne(ServerLevel level, LivingEntity target) {
        int slot = -1;
        for (int i = 0; i < orbiters.size(); i++) {
            Display.ItemDisplay d = orbiters.get(i);
            if (d != null && !d.isRemoved()) {
                slot = i;
                break;
            }
        }
        if (slot < 0) {
            orbiters.clear();
            colours.clear();
            return;
        }
        Display.ItemDisplay d = orbiters.get(slot);
        Vec3 from = d.position();
        StarBitLob.Colour colour = colours.get(slot);
        Vec3 landing = groundAt(level, target.position());
        boolean launched = StarBitLob.launch(level, this, colour, damageScale, from, landing, BURST_REACH, 2);
        if (!launched) {
            return; // the two-in-flight cap is full: keep this star and try again next cadence
        }
        d.discard();
        orbiters.set(slot, null);
        Fx.telegraphRing(level, landing, BURST_REACH, colour.rgbForWarning());
        boolean any = false;
        for (Display.ItemDisplay o : orbiters) {
            if (o != null && !o.isRemoved()) {
                any = true;
                break;
            }
        }
        if (!any) {
            orbiters.clear();
            colours.clear();
        }
    }

    private static Vec3 groundAt(ServerLevel level, Vec3 p) {
        BlockPos pos = BlockPos.containing(p.x, p.y + 1.0, p.z);
        for (int i = 0; i < 10; i++) {
            BlockPos below = pos.below(i);
            if (!level.isLoaded(below)) {
                break;
            }
            if (!level.getBlockState(below).getCollisionShape(level, below).isEmpty()) {
                return new Vec3(p.x, below.getY() + 1.05, p.z);
            }
        }
        return new Vec3(p.x, p.y + 0.05, p.z);
    }

    @Override
    public void remove(RemovalReason reason) {
        // Every removal path (death, discard, unload) must free the orbit displays, like the other composite mobs.
        for (Display.ItemDisplay d : orbiters) {
            if (d != null && !d.isRemoved()) {
                d.discard();
            }
        }
        orbiters.clear();
        colours.clear();
        StarBitLob.clearFor(this);
        super.remove(reason);
    }

    @Override
    public void die(net.minecraft.world.damagesource.DamageSource source) {
        if (this.level() instanceof ServerLevel level && !this.isDeadOrDying()) {
            level.sendParticles(ParticleTypes.WITCH, this.getX(), this.getY() + 1.2, this.getZ(), 16, 0.4, 0.6, 0.4, 0.05);
            level.playSound(null, this.getX(), this.getY(), this.getZ(), SoundEvents.WITCH_DEATH, SoundSource.HOSTILE, 1.0F, 1.2F);
        }
        super.die(source);
    }

    /**
     * Holds a firing band around the target: backs away inside {@link #KEEP_DISTANCE}, closes in beyond
     * {@link #APPROACH_DISTANCE}. Without the closing-in half a kiting witch drifted out of throw range and stood
     * there with a star in hand forever (measured: it ended 26 blocks away, throw range is 18).
     */
    private static final class KeepDistanceGoal extends net.minecraft.world.entity.ai.goal.Goal {
        private final HordeWitch witch;
        private int repathIn = 0;
        private int strafeDir = 1;
        private int strafeFlipIn = 0;

        KeepDistanceGoal(HordeWitch witch) {
            this.witch = witch;
            this.setFlags(java.util.EnumSet.of(Flag.MOVE, Flag.LOOK));
        }

        @Override
        public boolean canUse() {
            LivingEntity t = witch.getTarget();
            return t != null && t.isAlive();
        }

        @Override
        public boolean canContinueToUse() {
            return canUse();
        }

        @Override
        public void start() {
            strafeDir = witch.getRandom().nextBoolean() ? 1 : -1;
            strafeFlipIn = 0;
        }

        /**
         * Three bands, like the vanilla skeleton: beyond APPROACH_DISTANCE walk in; inside KEEP_DISTANCE back away;
         * in between (the old dead zone, where the witch just stood) sway side to side facing the player, flipping
         * direction every 1 to 2.5 s, as a taunt and a dodge. One goal, one path request per 10 ticks.
         */
        @Override
        public void tick() {
            LivingEntity t = witch.getTarget();
            if (t == null) {
                return;
            }
            witch.getLookControl().setLookAt(t, 30.0F, 30.0F);
            double d2 = witch.distanceToSqr(t);
            if (d2 > APPROACH_DISTANCE * APPROACH_DISTANCE) {
                if (--repathIn <= 0) {
                    repathIn = 10;
                    witch.getNavigation().moveTo(t, 1.0);
                }
                return;
            }
            if (d2 < KEEP_DISTANCE * KEEP_DISTANCE) {
                if (--repathIn <= 0) {
                    repathIn = 10;
                    Vec3 away = witch.position().subtract(t.position());
                    Vec3 dir = new Vec3(away.x, 0.0, away.z);
                    dir = dir.lengthSqr() > 1.0E-4 ? dir.normalize() : new Vec3(1.0, 0.0, 0.0);
                    Vec3 to = witch.position().add(dir.scale(6.0));
                    witch.getNavigation().moveTo(to.x, to.y, to.z, 1.1);
                }
                return;
            }
            // sway band
            if (--strafeFlipIn <= 0) {
                strafeFlipIn = 20 + witch.getRandom().nextInt(31);
                strafeDir = -strafeDir;
            }
            // A sideways step runs along a tangent, so the range creeps outward. Past SWAY_PULL_FROM, walk to a point on
            // the mid-band ring a little further round in the sway direction (re-pathed twice a second), then go back to
            // pure strafing once she is inside it. Mixing a forward input into strafe() fought the sideways motion.
            double range = Math.sqrt(d2);
            if (range > SWAY_PULL_FROM) {
                if (--repathIn <= 0) {
                    repathIn = 10;
                    Vec3 fromPlayer = witch.position().subtract(t.position());
                    double ang = Math.atan2(fromPlayer.z, fromPlayer.x) + strafeDir * 0.35;
                    double r = (KEEP_DISTANCE + APPROACH_DISTANCE) * 0.5 - 1.0;
                    witch.getNavigation().moveTo(t.getX() + Math.cos(ang) * r, witch.getY(), t.getZ() + Math.sin(ang) * r, 1.0);
                }
            } else {
                witch.getNavigation().stop();
                witch.getMoveControl().strafe(0.0F, 1.0F * strafeDir);
            }
        }

        @Override
        public void stop() {
            witch.getNavigation().stop();
        }
    }
}
