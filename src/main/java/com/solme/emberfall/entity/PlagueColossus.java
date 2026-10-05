package com.solme.emberfall.entity;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.zombie.Zombie;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;

import java.util.HashSet;
import java.util.Iterator;
import java.util.Set;
import java.util.UUID;

/**
 * Plague Colossus - Elite-track Zombie (2026-09-28 elite pass), a deliberate
 * mix of three ideas: an iron-bound tank (real iron armor, reactive Iron
 * Ward), a bloating plague carrier (grows over the life of the fight, Plague
 * Burst nausea/poison pulse, Rupture on death scaled by how bloated it got),
 * and a horde-caller (Muster spawns capped {@link HordeZombie} reinforcements).
 *
 * Visuals cost zero extra entities: Zombie uses the humanoid model, so the
 * "Plagued Zombie" minecraft-heads.com head (#75669) and real iron armor go
 * in ordinary equipment slots (the same technique {@link CorruptedSentinel}
 * uses). Growth is the vanilla SCALE attribute, so hitbox and model stay in
 * sync.
 *
 * Once-per-death Rupture lives in {@link #remove(Entity.RemovalReason)}, not a
 * tick method (an instant-kill can remove the entity before its next tick),
 * gated by {@code isDeadOrDying()} so despawn/unload never detonates it.
 */
public class PlagueColossus extends Zombie {

    /**
     * Emberfall zombies never break doors. Vanilla switches this on at spawn (Hard difficulty) and BreakDoorGoal then
     * calls removeBlock, which would delete a door from the designed map. Making the setter a no-op means the
     * flag stays false and the goal is never added.
     */
    @Override
    public void setCanBreakDoors(boolean canBreakDoors) {
        // intentionally ignored
    }

    private static final double BASE_HEALTH = 140.0;
    private static final double BASE_ATTACK_DAMAGE = 8.0;
    private static final float START_SCALE = 1.3F;
    private static final float MAX_SCALE = 2.0F;
    private static final float GROWTH_PER_SECOND = 0.0117F; // ~60s from start to max

    private static final long BURST_COOLDOWN_TICKS = 200L;   // 10s
    private static final double BURST_RADIUS = 4.5;
    private static final int BURST_NAUSEA_TICKS = 100;
    private static final int BURST_POISON_TICKS = 80;
    private static final double BURST_TRIGGER_RANGE = 4.0;

    private static final long MUSTER_COOLDOWN_TICKS = 300L;  // 15s
    private static final int MUSTER_MAX_ACTIVE = 3;
    private static final int MUSTER_SPAWN_COUNT = 2;
    private static final double MUSTER_TRIGGER_RANGE = 16.0;

    private static final long WARD_COOLDOWN_TICKS = 160L;    // 8s
    private static final int WARD_DURATION_TICKS = 60;
    private static final int WARD_AMPLIFIER = 1;             // Resistance II

    private static final float RUPTURE_BASE_DAMAGE = 6.0F;
    private static final double RUPTURE_BASE_RADIUS = 3.5;
    /** Below this health fraction the Rupture's reach is outlined on the ground. */
    private static final double RUPTURE_WARN_HP_FRACTION = 0.25;

    private double statMultiplier = 1.0;
    private float currentScale = START_SCALE;
    private long nextBurstAtTick = 0L;
    private long nextMusterAtTick = 0L;
    private long nextWardAtTick = 0L;
    private final Set<UUID> musterMinions = new HashSet<>();

    public PlagueColossus(EntityType<? extends Zombie> type, Level level) {
        super(type, level);
    }

    public static PlagueColossus spawn(ServerLevel level, BlockPos pos, double statMultiplier) {
        PlagueColossus colossus = new PlagueColossus(ModEntities.PLAGUE_COLOSSUS, level);
        colossus.statMultiplier = statMultiplier;
        colossus.setPos(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5);
        colossus.setYRot(level.getRandom().nextFloat() * 360.0F);

        AttributeInstance scale = colossus.getAttribute(Attributes.SCALE);
        if (scale != null) {
            scale.setBaseValue(START_SCALE);
        }
        AttributeInstance health = colossus.getAttribute(Attributes.MAX_HEALTH);
        if (health != null) {
            double hp = BASE_HEALTH * statMultiplier;
            health.setBaseValue(hp);
            colossus.setHealth((float) hp);
        }
        AttributeInstance dmg = colossus.getAttribute(Attributes.ATTACK_DAMAGE);
        if (dmg != null) {
            dmg.setBaseValue(BASE_ATTACK_DAMAGE * statMultiplier);
        }
        AttributeInstance kb = colossus.getAttribute(Attributes.KNOCKBACK_RESISTANCE);
        if (kb != null) {
            kb.setBaseValue(0.6);
        }
        AttributeInstance speed = colossus.getAttribute(Attributes.MOVEMENT_SPEED);
        if (speed != null) {
            speed.setBaseValue(speed.getBaseValue() * 0.85);
        }

        MobNames.apply(colossus, "Plague Colossus", MobNames.Tier.VENOM);
        colossus.setPersistenceRequired();

        colossus.setItemSlot(EquipmentSlot.HEAD, EliteHeads.plaguedZombieHead());
        colossus.setDropChance(EquipmentSlot.HEAD, 0.0F);
        colossus.setItemSlot(EquipmentSlot.CHEST, new ItemStack(Items.IRON_CHESTPLATE));
        colossus.setDropChance(EquipmentSlot.CHEST, 0.0F);
        colossus.setItemSlot(EquipmentSlot.LEGS, new ItemStack(Items.IRON_LEGGINGS));
        colossus.setDropChance(EquipmentSlot.LEGS, 0.0F);
        colossus.setItemSlot(EquipmentSlot.FEET, new ItemStack(Items.IRON_BOOTS));
        colossus.setDropChance(EquipmentSlot.FEET, 0.0F);
        colossus.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.IRON_AXE));
        colossus.setDropChance(EquipmentSlot.MAINHAND, 0.0F);

        level.addFreshEntity(colossus);
        level.sendParticles(ParticleTypes.SNEEZE, colossus.getX(), colossus.getY() + 1.5, colossus.getZ(),
                25, 0.7, 1.0, 0.7, 0.02);
        level.playSound(null, colossus.getX(), colossus.getY(), colossus.getZ(),
                SoundEvents.ZOMBIE_VILLAGER_CONVERTED, SoundSource.HOSTILE, 1.4F, 0.5F);
        return colossus;
    }

    @Override
    public boolean isSunSensitive() {
        return false;
    }

    @Override
    protected void customServerAiStep(ServerLevel level) {
        super.customServerAiStep(level);
        if (!this.isAlive()) {
            return;
        }
        long now = level.getGameTime();

        if (this.tickCount % 20 == 0) {
            growBloat();
        }
        // Warn about the death Rupture: once the Colossus is nearly dead, outline how far it will reach. The damage is an AABB
        // (not a distance check), so the outline is a square, not a ring.
        // There is no wind-up to show (it fires from remove()), so this is a standing hazard ring, drawn
        // sparingly (16 packets every 8 ticks).
        if (this.tickCount % 8 == 0 && this.getHealth() <= this.getMaxHealth() * RUPTURE_WARN_HP_FRACTION) {
            com.solme.emberfall.combat.Fx.telegraphSquare(level, this.position(), ruptureReach(),
                    com.solme.emberfall.combat.Fx.WARN_ORANGE);
        }
        if (this.tickCount % 6 == 0) {
            level.sendParticles(ParticleTypes.ITEM_SLIME, this.getX(), this.getY() + 1.2 * currentScale / START_SCALE,
                    this.getZ(), 1, 0.4, 0.5, 0.4, 0.0);
        }

        LivingEntity target = this.getTarget();
        if (target == null || !target.isAlive()) {
            return;
        }
        double distSq = this.distanceToSqr(target);

        if (now >= nextBurstAtTick && distSq <= BURST_TRIGGER_RANGE * BURST_TRIGGER_RANGE) {
            nextBurstAtTick = now + BURST_COOLDOWN_TICKS;
            plagueBurst(level);
        }
        if (now >= nextMusterAtTick && distSq <= MUSTER_TRIGGER_RANGE * MUSTER_TRIGGER_RANGE) {
            nextMusterAtTick = now + MUSTER_COOLDOWN_TICKS;
            muster(level);
        }
    }

    private void growBloat() {
        if (currentScale >= MAX_SCALE) {
            return;
        }
        currentScale = Math.min(MAX_SCALE, currentScale + GROWTH_PER_SECOND);
        AttributeInstance scale = this.getAttribute(Attributes.SCALE);
        if (scale != null) {
            scale.setBaseValue(currentScale);
        }
    }

    /** 0.0 at spawn size, 1.0 fully bloated - drives Rupture strength. */
    private float bloatFraction() {
        return (currentScale - START_SCALE) / (MAX_SCALE - START_SCALE);
    }

    private void plagueBurst(ServerLevel level) {
        level.playSound(null, this.getX(), this.getY(), this.getZ(),
                SoundEvents.SLIME_SQUISH, SoundSource.HOSTILE, 1.5F, 0.4F);
        level.sendParticles(ParticleTypes.SNEEZE, this.getX(), this.getY() + 1.0, this.getZ(),
                40, BURST_RADIUS / 2.5, 0.6, BURST_RADIUS / 2.5, 0.05);
        level.sendParticles(ParticleTypes.ITEM_SLIME, this.getX(), this.getY() + 0.5, this.getZ(),
                30, BURST_RADIUS / 2.5, 0.3, BURST_RADIUS / 2.5, 0.05);
        for (Player player : level.getEntitiesOfClass(Player.class,
                this.getBoundingBox().inflate(BURST_RADIUS), p -> p.isAlive() && !p.isSpectator())) {
            player.addEffect(new MobEffectInstance(MobEffects.NAUSEA, BURST_NAUSEA_TICKS, 0, false, true, true));
            player.addEffect(new MobEffectInstance(MobEffects.POISON, BURST_POISON_TICKS, 0, false, true, true));
        }
    }

    private void muster(ServerLevel level) {
        for (Iterator<UUID> it = musterMinions.iterator(); it.hasNext(); ) {
            Entity e = level.getEntity(it.next());
            if (e == null || !e.isAlive()) {
                it.remove();
            }
        }
        int room = MUSTER_MAX_ACTIVE - musterMinions.size();
        if (room <= 0) {
            return;
        }
        int count = Math.min(room, MUSTER_SPAWN_COUNT);
        level.playSound(null, this.getX(), this.getY(), this.getZ(),
                SoundEvents.ZOMBIE_VILLAGER_AMBIENT, SoundSource.HOSTILE, 1.5F, 0.4F);
        for (int i = 0; i < count; i++) {
            HordeZombie minion = new HordeZombie(ModEntities.HORDE_ZOMBIE, level);
            double angle = level.getRandom().nextDouble() * Math.PI * 2.0;
            double dist = 2.0 + level.getRandom().nextDouble() * 1.5;
            minion.setPos(this.getX() + Math.cos(angle) * dist, this.getY(), this.getZ() + Math.sin(angle) * dist);
            minion.setYRot(level.getRandom().nextFloat() * 360.0F);
            LivingEntity target = this.getTarget();
            if (target != null) {
                minion.setTarget(target);
            }
            level.addFreshEntity(minion);
            musterMinions.add(minion.getUUID());
            level.sendParticles(ParticleTypes.SOUL, minion.getX(), minion.getY() + 0.5, minion.getZ(),
                    8, 0.3, 0.4, 0.3, 0.02);
        }
    }

    /** Iron Ward: reactive damage reduction, off cooldown, triggered by taking a hit. */
    @Override
    public boolean hurtServer(ServerLevel level, DamageSource source, float amount) {
        boolean hurt = super.hurtServer(level, source, amount);
        if (hurt && this.isAlive() && level.getGameTime() >= nextWardAtTick) {
            nextWardAtTick = level.getGameTime() + WARD_COOLDOWN_TICKS;
            this.addEffect(new MobEffectInstance(MobEffects.RESISTANCE, WARD_DURATION_TICKS, WARD_AMPLIFIER, false, true));
            level.playSound(null, this.getX(), this.getY(), this.getZ(),
                    SoundEvents.IRON_GOLEM_REPAIR, SoundSource.HOSTILE, 1.2F, 0.6F);
            level.sendParticles(ParticleTypes.CRIT, this.getX(), this.getY() + 1.4, this.getZ(),
                    12, 0.4, 0.6, 0.4, 0.1);
        }
        return hurt;
    }

    /** Rupture inflation radius. Grows with the bloat, so a fatter Colossus is more dangerous to finish. */
    private double ruptureRadius() {
        return RUPTURE_BASE_RADIUS * (1.0 + bloatFraction());
    }

    /**
     * How far the Rupture actually reaches from the Colossus's centre along each axis. The damage box is
     * {@code getBoundingBox().inflate(radius)}, so its half-width is the body half-width plus the radius.
     * The ring is drawn at exactly this distance so the warning matches what will hit.
     */
    private double ruptureReach() {
        return this.getBoundingBox().getXsize() / 2.0 + ruptureRadius();
    }

    private void rupture(ServerLevel level) {
        double radius = ruptureRadius();
        float frac = bloatFraction();
        float damage = (float) (RUPTURE_BASE_DAMAGE * statMultiplier * (1.0 + frac * 1.5));
        level.playSound(null, this.getX(), this.getY(), this.getZ(),
                SoundEvents.SLIME_SQUISH, SoundSource.HOSTILE, 2.0F, 0.3F);
        level.sendParticles(ParticleTypes.SNEEZE, this.getX(), this.getY() + 1.0, this.getZ(),
                60, radius / 2.0, 0.8, radius / 2.0, 0.08);
        level.sendParticles(ParticleTypes.ITEM_SLIME, this.getX(), this.getY() + 0.8, this.getZ(),
                50, radius / 2.0, 0.6, radius / 2.0, 0.1);
        AABB area = this.getBoundingBox().inflate(radius);
        for (Player player : level.getEntitiesOfClass(Player.class, area, p -> p.isAlive() && !p.isSpectator())) {
            player.hurtServer(level, level.damageSources().magic(), damage);
            player.addEffect(new MobEffectInstance(MobEffects.POISON, BURST_POISON_TICKS, 1, false, true, true));
        }
    }

    /**
     * Fires synchronously for every removal path, so the once-per-death Rupture belongs here
     * (see class javadoc). Minions are left alive on purpose: killing the summoner does not wipe the wave.
     */
    @Override
    public void remove(Entity.RemovalReason reason) {
        if (this.isDeadOrDying() && this.level() instanceof ServerLevel serverLevel) {
            rupture(serverLevel);
        }
        super.remove(reason);
    }
}
