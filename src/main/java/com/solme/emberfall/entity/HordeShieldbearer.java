package com.solme.emberfall.entity;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.control.MoveControl;
import net.minecraft.world.entity.monster.zombie.Zombie;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/**
 * Seventh horde filler: a slow armoured zombie behind a shield. Hits that arrive from its front are mostly blocked, so
 * the player has to get round to a flank or the back, and the mob turns slowly enough that doing so works.
 *
 * <ul>
 *   <li><b>One hook for every weapon.</b> All eight weapons, their splash, orbit, beacon and ultimate damage reach a
 *       mob through {@code mob.hurtServer(level, player.damageSources().playerAttack(player), amount)} (checked in
 *       {@code combat/}), so the block lives in this class's {@link #hurtServer} and no weapon code changes.</li>
 *   <li><b>Where "front" is.</b> The attacker's position comes from {@code source.getEntity()}, falling back to the
 *       direct entity, and the mob's body yaw ({@code getYRot()}) is its facing. The block applies inside
 *       {@value #BLOCK_HALF_ANGLE_DEG} degrees either side of it.</li>
 *   <li><b>Slow turning.</b> {@link TurnLimitedMoveControl} overrides {@code rotlerp} (vanilla clamps the body turn to
 *       90 degrees a tick, which is effectively instant) to {@value #TURN_DEG_PER_TICK} degrees a tick.</li>
 *   <li>Cost: no goals, no scans, no spawned entities; the block is a yaw comparison on a hit.</li>
 * </ul>
 */
public class HordeShieldbearer extends Zombie {
    private static final double BASE_HEALTH = 40.0;
    private static final double BASE_SPEED = 0.21;
    private static final double VETERAN_HEALTH_MULT = 2.5;
    /** Damage that gets through a frontal block, as a fraction (0.2 = 80 percent blocked). */
    private static final float FRONT_PASS_FRACTION = 0.2F;
    /** Half the width of the shielded cone, measured from the body's facing. */
    static final double BLOCK_HALF_ANGLE_DEG = 55.0;
    /** Body turn rate. 6 degrees a tick turns a full half circle in 30 ticks (1.5 s). */
    static final float TURN_DEG_PER_TICK = 6.0F;
    private static final int SOUND_COOLDOWN_TICKS = 6;
    private static final boolean TEST_MODE = Boolean.getBoolean("emberfall.testMode");

    private boolean veteran = false;
    private long nextSoundAtTick = 0L;

    public HordeShieldbearer(EntityType<? extends Zombie> type, Level level) {
        super(type, level);
        this.moveControl = new TurnLimitedMoveControl(this);
    }

    /** Door breaking stays off, like every horde zombie. */
    @Override
    public void setCanBreakDoors(boolean canBreakDoors) {
        // intentionally ignored
    }

    public static AttributeSupplier.Builder createShieldbearerAttributes() {
        return Zombie.createAttributes()
                .add(Attributes.MAX_HEALTH, BASE_HEALTH)
                .add(Attributes.MOVEMENT_SPEED, BASE_SPEED);
    }

    /** Called once right after construction, before the entity is added to the world. */
    public void prepare() {
        this.setHealth(this.getMaxHealth());
        this.setItemSlot(EquipmentSlot.OFFHAND, new ItemStack(Items.SHIELD));
        this.setDropChance(EquipmentSlot.OFFHAND, 0.0F);
        MobNames.apply(this, "Horde Shieldbearer", MobNames.Tier.BONE);
    }

    public void becomeVeteran() {
        this.veteran = true;
        AttributeInstance health = this.getAttribute(Attributes.MAX_HEALTH);
        if (health != null) {
            health.setBaseValue(health.getBaseValue() * VETERAN_HEALTH_MULT);
            this.setHealth((float) health.getValue());
        }
        MobNames.apply(this, "Veteran Horde Shieldbearer", MobNames.Tier.VETERAN);
    }

    public boolean isVeteran() {
        return veteran;
    }

    /**
     * True when {@code attackerPos} lies inside the shielded cone. The angle is taken between the body's facing and the
     * horizontal direction from this mob to the attacker; height is ignored so a hit from above still counts as front.
     */
    boolean isFrontHit(Vec3 attackerPos) {
        double dx = attackerPos.x - this.getX();
        double dz = attackerPos.z - this.getZ();
        if (dx * dx + dz * dz < 1.0E-6) {
            return true;    // standing on top of us: treat as front
        }
        // Minecraft yaw 0 faces +Z, and increases clockwise seen from above; -atan2(dx, dz) gives the yaw to the attacker.
        float toAttackerYaw = (float) (Math.toDegrees(Math.atan2(-dx, dz)));
        float diff = Math.abs(net.minecraft.util.Mth.wrapDegrees(toAttackerYaw - this.getYRot()));
        return diff <= BLOCK_HALF_ANGLE_DEG;
    }

    private float lastYaw = Float.NaN;

    @Override
    protected void customServerAiStep(ServerLevel level) {
        super.customServerAiStep(level);
        if (TEST_MODE) {
            float y = this.getYRot();
            if (!Float.isNaN(lastYaw)) {
                float d = Math.abs(net.minecraft.util.Mth.wrapDegrees(y - lastYaw));
                if (d > 0.5F) {
                    com.solme.emberfall.EmberfallMod.LOGGER.info("SHIELD_TEST turn tick={} dyaw={}", level.getGameTime(), String.format("%.2f", d));
                }
            }
            lastYaw = y;
        }
    }

    @Override
    public boolean hurtServer(ServerLevel level, DamageSource source, float amount) {
        Entity from = source.getEntity() != null ? source.getEntity() : source.getDirectEntity();
        boolean blocked = false;
        float passed = amount;
        if (from != null && amount > 0.0F && isFrontHit(from.position())) {
            blocked = true;
            passed = amount * FRONT_PASS_FRACTION;
            long now = level.getGameTime();
            if (now >= nextSoundAtTick) {
                nextSoundAtTick = now + SOUND_COOLDOWN_TICKS;
                level.playSound(null, this.getX(), this.getY(), this.getZ(),
                        SoundEvents.SHIELD_BLOCK, SoundSource.HOSTILE, 0.8F, 1.0F);
                level.sendParticles(ParticleTypes.CRIT, this.getX(), this.getY() + 1.2, this.getZ(), 3, 0.3, 0.3, 0.3, 0.1);
            }
        }
        if (TEST_MODE) {
            com.solme.emberfall.EmberfallMod.LOGGER.info("SHIELD_TEST hit amount={} passed={} blocked={}",
                    String.format("%.2f", amount), String.format("%.2f", passed), blocked);
        }
        return super.hurtServer(level, source, passed);
    }

    /** A move control that turns the body slowly, so a player can get round to the shield's blind side. */
    static final class TurnLimitedMoveControl extends MoveControl {
        TurnLimitedMoveControl(net.minecraft.world.entity.Mob mob) {
            super(mob);
        }

        @Override
        protected float rotlerp(float current, float target, float maxStep) {
            return super.rotlerp(current, target, Math.min(maxStep, TURN_DEG_PER_TICK));
        }
    }
}
