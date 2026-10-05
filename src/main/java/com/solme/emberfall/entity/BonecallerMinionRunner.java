package com.solme.emberfall.entity;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.animal.equine.AbstractHorse;
import net.minecraft.world.entity.animal.equine.ZombieHorse;
import net.minecraft.world.entity.monster.zombie.Husk;
import net.minecraft.world.entity.monster.zombie.Zombie;
import net.minecraft.world.entity.monster.zombie.ZombieVillager;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Owns every temporary/companion entity Bonecaller Necromancer creates:
 * the undead horse mount it rides (with its own periodic "restless foal"
 * decoy spawns), the temporary Stitched Brute/Archer/Vanguard combat
 * minions, and the Stitched Anchorite healer minion's heal/damage beam.
 *
 * Ported from SlopPack's per-mob BukkitRunnables (spawnUndeadMount,
 * startHorseSummonTicker, driveAndKickBabyHorse, startHealerTicker,
 * enrageHorse) - condensed into one global "tick everything once a tick"
 * tracker, matching the {@link UmbralMinionRunner} pattern already used
 * for Umbral Magus's summons, since none of these need a full custom
 * entity subclass of their own (they're vanilla mobs driven externally).
 *
 * DEVIATION (deliberate, not a fidelity loss): SlopPack's decorative
 * "stitched" BlockDisplay limbs + idle scream sound on every minion are
 * pure cosmetic flourish with no gameplay effect - dropped to keep this
 * already-large system's scope manageable, matching the "refined, not
 * verbatim" direction for anything beyond the core 5-mob port.
 */
public final class BonecallerMinionRunner {
    private BonecallerMinionRunner() {}

    private static final double HORSE_MAX_HEALTH = 24.0;
    private static final double HORSE_SPEED_MULT = 0.85;
    private static final double HORSE_FOLLOW_SPEED = 1.0;
    private static final double HORSE_CHASE_RANGE = 20.0;
    private static final int HORSE_FOLLOW_DISTANCE_TICKS_MIN = 200; // 10s
    private static final int HORSE_FOLLOW_DISTANCE_TICKS_MAX = 320; // 16s

    private static final double BABY_HORSE_MAX_HEALTH = 8.0;
    private static final double BABY_HORSE_SPEED_MULT = 1.15;
    private static final double BABY_HORSE_SCALE = 0.55;
    private static final int BABY_HORSE_MIN_LIFESPAN_TICKS = 300;
    private static final int BABY_HORSE_MAX_LIFESPAN_TICKS = 480;
    private static final int BABY_HORSE_ATTACK_RANGE_SQ_THRESHOLD = 400; // 20 blocks
    private static final double BABY_HORSE_MELEE_RANGE = 1.8;
    private static final double BABY_HORSE_DAMAGE = 5.0;
    private static final int BABY_HORSE_ATTACK_COOLDOWN_TICKS = 26; // ~1.3s
    private static final int MAX_BABY_HORSES = 2;
    private static final int BABY_HORSE_SPAWN_MIN_TICKS = 200; // 10s
    private static final int BABY_HORSE_SPAWN_MAX_TICKS = 320; // 16s

    private static final int VENGEFUL_HORSE_LIFESPAN_TICKS = 600; // 30s
    private static final double VENGEFUL_HORSE_SPEED_MULT = 1.3;
    private static final double VENGEFUL_HORSE_DAMAGE = 2.0;
    private static final int VENGEFUL_HORSE_ATTACK_COOLDOWN_TICKS = 40; // 2s

    private static final int RISE_TICKS = 12; // 0.6s

    private static final int HEALER_BEAM_RANGE = 5;
    private static final int HEALER_DAMAGE_BEAM_COOLDOWN_TICKS = 50; // 2.5s
    private static final int HEALER_HEAL_BEAM_COOLDOWN_TICKS = 60; // 3s
    private static final double HEALER_DAMAGE_BEAM_DAMAGE = 2.5;
    private static final double HEALER_HEAL_MIN = 1.5;
    private static final double HEALER_HEAL_MAX = 3.0;

    // ---- undead horse mount ----

    private static final class MountState {
        final LivingEntity owner;
        AbstractHorse horse;
        final List<AbstractHorse> babyHorses = new ArrayList<>();
        long nextBabyAtTick;
        boolean vengeful = false;
        long vengefulEndsAtTick;
        long nextAttackAtTick = 0L;

        MountState(LivingEntity owner, AbstractHorse horse, long now) {
            this.owner = owner;
            this.horse = horse;
            this.nextBabyAtTick = now + ThreadLocalRandom.current().nextInt(BABY_HORSE_SPAWN_MIN_TICKS, BABY_HORSE_SPAWN_MAX_TICKS + 1);
        }
    }

    private static final Map<UUID, MountState> MOUNTS = new HashMap<>();

    /** Spawns the undead mount and puts the necromancer in the saddle. Call once, right after the boss itself spawns. */
    public static void spawnMount(ServerLevel level, LivingEntity owner) {
        AbstractHorse horse = EntityType.ZOMBIE_HORSE.create(level, EntitySpawnReason.MOB_SUMMONED);
        if (horse == null) {
            return;
        }
        horse.setPos(owner.getX(), owner.getY(), owner.getZ());
        horse.setTamed(true);
        horse.setBaby(false);
        horse.setPersistenceRequired();
        // SlopPack saddles its mount too; an unsaddled tamed horse with a rider renders bare. Never drops.
        horse.setItemSlot(net.minecraft.world.entity.EquipmentSlot.SADDLE, new ItemStack(Items.SADDLE));
        horse.setDropChance(net.minecraft.world.entity.EquipmentSlot.SADDLE, 0.0f);
        AttributeInstance maxHealth = horse.getAttribute(Attributes.MAX_HEALTH);
        if (maxHealth != null) {
            maxHealth.setBaseValue(HORSE_MAX_HEALTH);
            horse.setHealth((float) HORSE_MAX_HEALTH);
        }
        AttributeInstance speed = horse.getAttribute(Attributes.MOVEMENT_SPEED);
        if (speed != null) {
            speed.setBaseValue(speed.getBaseValue() * HORSE_SPEED_MULT);
        }
        level.addFreshEntity(horse);
        owner.startRiding(horse);
        level.sendParticles(ParticleTypes.SOUL, horse.getX(), horse.getY() + 0.5, horse.getZ(), 15, 0.4, 0.4, 0.4, 0.02);

        MOUNTS.put(owner.getUUID(), new MountState(owner, horse, level.getGameTime()));
    }

    public static int countActiveMinions(UUID ownerId) {
        List<TrackedTemp> minions = MINIONS.get(ownerId);
        if (minions == null) {
            return 0;
        }
        int count = 0;
        for (TrackedTemp t : minions) {
            if (t.minion.isAlive()) {
                count++;
            }
        }
        return count;
    }

    /** True if {@code entity} is the boss itself, or one of its currently-tracked minions - used to suppress friendly fire. */
    public static boolean isFriendly(UUID ownerId, LivingEntity entity) {
        if (entity == null) {
            return false;
        }
        if (entity.getUUID().equals(ownerId)) {
            return true;
        }
        List<TrackedTemp> minions = MINIONS.get(ownerId);
        if (minions != null) {
            for (TrackedTemp t : minions) {
                if (t.minion.getUUID().equals(entity.getUUID())) {
                    return true;
                }
            }
        }
        return false;
    }

    public static boolean hasHealerMinion(UUID ownerId) {
        List<TrackedTemp> minions = MINIONS.get(ownerId);
        if (minions == null) {
            return false;
        }
        for (TrackedTemp t : minions) {
            if (t.isHealer && t.minion.isAlive()) {
                return true;
            }
        }
        return false;
    }

    // ---- temporary combat minions + healer minion ----

    private static final class TrackedTemp {
        final UUID ownerId;
        final LivingEntity minion;
        final long expireAtTick;
        final long riseStartsAtTick;
        final long riseEndsAtTick;
        final Vec3 riseTargetPos;
        final boolean isHealer;
        long nextBeamAtTick = 0L;

        TrackedTemp(UUID ownerId, LivingEntity minion, long expireAtTick, long riseStartsAtTick, long riseEndsAtTick,
                    Vec3 riseTargetPos, boolean isHealer) {
            this.ownerId = ownerId;
            this.minion = minion;
            this.expireAtTick = expireAtTick;
            this.riseStartsAtTick = riseStartsAtTick;
            this.riseEndsAtTick = riseEndsAtTick;
            this.riseTargetPos = riseTargetPos;
            this.isHealer = isHealer;
        }
    }

    private static final Map<UUID, List<TrackedTemp>> MINIONS = new HashMap<>();

    private enum MinionKind { BRUTE, ARCHER, VANGUARD }

    /** Spawns one temporary Stitched combat minion near {@code owner}, rising out of the ground. */
    public static void spawnCombatMinion(ServerLevel level, LivingEntity owner) {
        double roll = ThreadLocalRandom.current().nextDouble();
        MinionKind kind = roll < 0.4 ? MinionKind.BRUTE : (roll < 0.8 ? MinionKind.ARCHER : MinionKind.VANGUARD);

        EntityType<? extends LivingEntity> type;
        String name;
        double minHp;
        double maxHp;
        ItemStack weapon;
        ItemStack helmet;
        switch (kind) {
            case BRUTE -> {
                type = ThreadLocalRandom.current().nextBoolean() ? EntityType.HUSK : EntityType.ZOMBIE;
                name = "Stitched Brute";
                minHp = 20.0; maxHp = 30.0;
                weapon = new ItemStack(ThreadLocalRandom.current().nextBoolean() ? Items.IRON_AXE : Items.IRON_SWORD);
                helmet = new ItemStack(Items.IRON_HELMET);
            }
            case ARCHER -> {
                type = EntityType.SKELETON;
                name = "Stitched Archer";
                minHp = 15.0; maxHp = 20.0;
                weapon = new ItemStack(Items.BOW);
                helmet = new ItemStack(Items.LEATHER_HELMET);
            }
            default -> {
                type = ThreadLocalRandom.current().nextBoolean() ? EntityType.STRAY : EntityType.WITHER_SKELETON;
                name = "Stitched Vanguard";
                minHp = 25.0; maxHp = 35.0;
                weapon = new ItemStack(Items.STONE_SWORD);
                helmet = new ItemStack(Items.CHAINMAIL_HELMET);
            }
        }

        net.minecraft.world.entity.Mob minion = (net.minecraft.world.entity.Mob) type.create(level, EntitySpawnReason.MOB_SUMMONED);
        if (minion == null) {
            return;
        }
        double ox = ThreadLocalRandom.current().nextDouble(-1.5, 1.5);
        double oz = ThreadLocalRandom.current().nextDouble(-1.5, 1.5);
        Vec3 landingPos = owner.position().add(ox, 0.0, oz);
        minion.setPos(landingPos.x, landingPos.y - 1.2, landingPos.z);
        MobNames.apply(minion, name, MobNames.Tier.BONE);
        double hp = ThreadLocalRandom.current().nextDouble(minHp, maxHp);
        AttributeInstance maxHealth = minion.getAttribute(Attributes.MAX_HEALTH);
        if (maxHealth != null) {
            maxHealth.setBaseValue(hp);
            minion.setHealth((float) hp);
        }
        minion.setItemSlot(net.minecraft.world.entity.EquipmentSlot.MAINHAND, weapon);
        minion.setItemSlot(net.minecraft.world.entity.EquipmentSlot.HEAD, helmet);
        minion.setDropChance(net.minecraft.world.entity.EquipmentSlot.MAINHAND, 0.0f);
        minion.setDropChance(net.minecraft.world.entity.EquipmentSlot.HEAD, 0.0f);
        level.addFreshEntity(minion);

        beginRise(level, minion, landingPos);
        int lifespanTicks = ThreadLocalRandom.current().nextInt(400, 601);
        long now = level.getGameTime();
        TrackedTemp tracked = new TrackedTemp(owner.getUUID(), minion, now + lifespanTicks, now, now + RISE_TICKS, landingPos, false);
        MINIONS.computeIfAbsent(owner.getUUID(), k -> new ArrayList<>()).add(tracked);
        ALL_TEMP.add(tracked);

        level.playSound(null, owner.getX(), owner.getY(), owner.getZ(), SoundEvents.ZOMBIE_VILLAGER_CONVERTED, SoundSource.HOSTILE, 1.0f, 0.8f);
        level.sendParticles(ParticleTypes.SQUID_INK, owner.getX(), owner.getY() + 1.0, owner.getZ(), 25, 1.0, 0.5, 1.0, 0.1);
    }

    /** Spawns the Stitched Anchorite healer minion. Returns true if it was actually spawned. */
    public static boolean spawnHealerMinion(ServerLevel level, LivingEntity owner) {
        net.minecraft.world.entity.Mob minion = EntityType.ZOMBIE_VILLAGER.create(level, EntitySpawnReason.MOB_SUMMONED);
        if (!(minion instanceof ZombieVillager)) {
            return false;
        }
        double ox = ThreadLocalRandom.current().nextDouble(-1.5, 1.5);
        double oz = ThreadLocalRandom.current().nextDouble(-1.5, 1.5);
        Vec3 landingPos = owner.position().add(ox, 0.0, oz);
        minion.setPos(landingPos.x, landingPos.y - 1.2, landingPos.z);
        MobNames.apply(minion, "Stitched Anchorite", MobNames.Tier.BONE);
        double hp = ThreadLocalRandom.current().nextDouble(18.0, 24.0);
        AttributeInstance maxHealth = minion.getAttribute(Attributes.MAX_HEALTH);
        if (maxHealth != null) {
            maxHealth.setBaseValue(hp);
            minion.setHealth((float) hp);
        }
        minion.setTarget(null);
        minion.setItemSlot(net.minecraft.world.entity.EquipmentSlot.MAINHAND, new ItemStack(Items.GLOWSTONE_DUST));
        minion.setDropChance(net.minecraft.world.entity.EquipmentSlot.MAINHAND, 0.0f);
        level.addFreshEntity(minion);

        beginRise(level, minion, landingPos);
        int lifespanTicks = ThreadLocalRandom.current().nextInt(500, 801);
        long now = level.getGameTime();
        TrackedTemp tracked = new TrackedTemp(owner.getUUID(), minion, now + lifespanTicks, now, now + RISE_TICKS, landingPos, true);
        MINIONS.computeIfAbsent(owner.getUUID(), k -> new ArrayList<>()).add(tracked);
        ALL_TEMP.add(tracked);
        return true;
    }

    private static final List<TrackedTemp> ALL_TEMP = new ArrayList<>();

    /** Suspends AI/gravity and eases the minion up out of the ground over {@link #RISE_TICKS}. */
    private static void beginRise(ServerLevel level, LivingEntity minion, Vec3 finalPos) {
        minion.setNoGravity(true);
        if (minion instanceof net.minecraft.world.entity.Mob mob) {
            mob.setNoAi(true);
        }
        level.sendParticles(new net.minecraft.core.particles.BlockParticleOption(ParticleTypes.BLOCK, net.minecraft.world.level.block.Blocks.DIRT.defaultBlockState()),
                finalPos.x, finalPos.y, finalPos.z, 20, 0.4, 0.1, 0.4, 0.0);
        level.playSound(null, finalPos.x, finalPos.y, finalPos.z, SoundEvents.GRAVEL_BREAK, SoundSource.HOSTILE, 1.0f, 0.7f);
    }

    // ---- main tick ----

    public static void tickAll(MinecraftServer server) {
        long now = server.overworld().getGameTime();
        tickMounts(server, now);
        tickTempMinions(server, now);
    }

    private static void tickMounts(MinecraftServer server, long now) {
        if (MOUNTS.isEmpty()) {
            return;
        }
        Iterator<Map.Entry<UUID, MountState>> it = MOUNTS.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<UUID, MountState> entry = it.next();
            MountState state = entry.getValue();
            AbstractHorse horse = state.horse;
            if (!horse.isAlive() || !(horse.level() instanceof ServerLevel level)) {
                despawnBabyHorses(state);
                it.remove();
                continue;
            }

            // baby-foal decoys
            state.babyHorses.removeIf(b -> !b.isAlive());
            if (!state.vengeful && now >= state.nextBabyAtTick && state.babyHorses.size() < MAX_BABY_HORSES) {
                state.nextBabyAtTick = now + ThreadLocalRandom.current().nextInt(BABY_HORSE_SPAWN_MIN_TICKS, BABY_HORSE_SPAWN_MAX_TICKS + 1);
                spawnBabyHorse(level, horse, state);
            }
            for (AbstractHorse baby : state.babyHorses) {
                tickBabyHorse(level, baby, now);
            }

            if (state.vengeful) {
                tickVengefulHorse(level, horse, state, now);
                if (now >= state.vengefulEndsAtTick) {
                    horse.setCustomName(null);
                    horse.setCustomNameVisible(false);
                    despawnBabyHorses(state);
                    it.remove();
                }
                continue;
            }

            // ridden mount: walk the horse toward the nearest player so the rider can cast from horseback
            Player nearest = level.getNearestPlayer(horse, HORSE_CHASE_RANGE);
            if (nearest != null && horse.distanceTo(nearest) > 6.0) {
                horse.getNavigation().moveTo(nearest, HORSE_FOLLOW_SPEED);
            }
        }
    }

    private static void spawnBabyHorse(ServerLevel level, AbstractHorse parent, MountState state) {
        double ox = ThreadLocalRandom.current().nextDouble(-1.5, 1.5);
        double oz = ThreadLocalRandom.current().nextDouble(-1.5, 1.5);
        AbstractHorse baby = EntityType.ZOMBIE_HORSE.create(level, EntitySpawnReason.MOB_SUMMONED);
        if (baby == null) {
            return;
        }
        Vec3 pos = parent.position().add(ox, 0.0, oz);
        baby.setPos(pos.x, pos.y, pos.z);
        baby.setBaby(true);
        baby.setTamed(true);
        MobNames.apply(baby, "Restless Foal", MobNames.Tier.BONE);
        AttributeInstance scale = baby.getAttribute(Attributes.SCALE);
        if (scale != null) {
            scale.setBaseValue(BABY_HORSE_SCALE);
        }
        AttributeInstance maxHealth = baby.getAttribute(Attributes.MAX_HEALTH);
        if (maxHealth != null) {
            maxHealth.setBaseValue(BABY_HORSE_MAX_HEALTH);
            baby.setHealth((float) BABY_HORSE_MAX_HEALTH);
        }
        AttributeInstance speed = baby.getAttribute(Attributes.MOVEMENT_SPEED);
        if (speed != null) {
            speed.setBaseValue(speed.getBaseValue() * BABY_HORSE_SPEED_MULT);
        }
        level.addFreshEntity(baby);
        level.playSound(null, pos.x, pos.y, pos.z, SoundEvents.HORSE_AMBIENT, SoundSource.HOSTILE, 1.0f, 1.6f);
        level.sendParticles(ParticleTypes.SOUL, pos.x, pos.y + 0.4, pos.z, 12, 0.3, 0.3, 0.3, 0.02);

        state.babyHorses.add(baby);
        BABY_ATTACK_COOLDOWN.put(baby.getUUID(), 0L);
        BABY_EXPIRE.put(baby.getUUID(), level.getGameTime() + ThreadLocalRandom.current().nextInt(BABY_HORSE_MIN_LIFESPAN_TICKS, BABY_HORSE_MAX_LIFESPAN_TICKS + 1));
    }

    private static final Map<UUID, Long> BABY_ATTACK_COOLDOWN = new HashMap<>();
    private static final Map<UUID, Long> BABY_EXPIRE = new HashMap<>();

    private static void tickBabyHorse(ServerLevel level, AbstractHorse baby, long now) {
        Long expireAt = BABY_EXPIRE.get(baby.getUUID());
        if (expireAt != null && now >= expireAt) {
            level.sendParticles(ParticleTypes.POOF, baby.getX(), baby.getY(), baby.getZ(), 8, 0.2, 0.3, 0.2, 0.05);
            baby.discard();
            BABY_ATTACK_COOLDOWN.remove(baby.getUUID());
            BABY_EXPIRE.remove(baby.getUUID());
            return;
        }
        Player nearest = level.getNearestPlayer(baby, 14.0);
        if (nearest == null) {
            return;
        }
        double dist = baby.distanceTo(nearest);
        if (dist > BABY_HORSE_MELEE_RANGE) {
            baby.getNavigation().moveTo(nearest, 1.1);
            return;
        }
        long cd = BABY_ATTACK_COOLDOWN.getOrDefault(baby.getUUID(), 0L);
        if (now >= cd) {
            BABY_ATTACK_COOLDOWN.put(baby.getUUID(), now + BABY_HORSE_ATTACK_COOLDOWN_TICKS);
            nearest.hurtServer(level, baby.damageSources().mobAttack(baby), (float) BABY_HORSE_DAMAGE);
            Vec3 knock = nearest.position().subtract(baby.position());
            if (knock.lengthSqr() > 0.001) {
                knock = knock.normalize().scale(0.8);
                nearest.setDeltaMovement(nearest.getDeltaMovement().add(knock.x, 0.35, knock.z));
            }
            level.playSound(null, baby.getX(), baby.getY(), baby.getZ(), SoundEvents.HORSE_ANGRY, SoundSource.HOSTILE, 1.2f, 0.8f);
            level.sendParticles(ParticleTypes.CRIT, nearest.getX(), nearest.getY() + 1.0, nearest.getZ(), 10, 0.3, 0.3, 0.3, 0.05);
        }
    }

    private static void tickVengefulHorse(ServerLevel level, AbstractHorse horse, MountState state, long now) {
        Player nearest = level.getNearestPlayer(horse, 16.0);
        if (nearest == null) {
            return;
        }
        horse.getNavigation().moveTo(nearest, 1.35);
        double dist = horse.distanceTo(nearest);
        if (dist <= 2.6 && now >= state.nextAttackAtTick) {
            state.nextAttackAtTick = now + VENGEFUL_HORSE_ATTACK_COOLDOWN_TICKS;
            nearest.hurtServer(level, horse.damageSources().mobAttack(horse), (float) VENGEFUL_HORSE_DAMAGE);
            Vec3 knock = nearest.position().subtract(horse.position());
            if (knock.lengthSqr() > 0.001) {
                knock = knock.normalize().scale(0.6);
                nearest.setDeltaMovement(nearest.getDeltaMovement().add(knock.x, 0.3, knock.z));
            }
            level.playSound(null, horse.getX(), horse.getY(), horse.getZ(), SoundEvents.HORSE_ANGRY, SoundSource.HOSTILE, 1.0f, 1.5f);
            level.sendParticles(ParticleTypes.CRIT, nearest.getX(), nearest.getY() + 0.8, nearest.getZ(), 6, 0.2, 0.2, 0.2, 0.03);
        }
    }

    private static void despawnBabyHorses(MountState state) {
        for (AbstractHorse baby : state.babyHorses) {
            if (baby.isAlive()) {
                baby.discard();
            }
            BABY_ATTACK_COOLDOWN.remove(baby.getUUID());
            BABY_EXPIRE.remove(baby.getUUID());
        }
        state.babyHorses.clear();
    }

    private static void tickTempMinions(MinecraftServer server, long now) {
        if (ALL_TEMP.isEmpty()) {
            return;
        }
        Iterator<TrackedTemp> it = ALL_TEMP.iterator();
        while (it.hasNext()) {
            TrackedTemp tracked = it.next();
            LivingEntity minion = tracked.minion;
            if (!(minion.level() instanceof ServerLevel level) || !minion.isAlive()) {
                if (minion.level() instanceof ServerLevel lvl) {
                    lvl.sendParticles(ParticleTypes.SMOKE, minion.getX(), minion.getY() + 0.5, minion.getZ(), 10, 0.2, 0.2, 0.2, 0.01);
                }
                removeFromOwnerList(tracked);
                it.remove();
                continue;
            }
            if (now >= tracked.expireAtTick) {
                level.sendParticles(ParticleTypes.POOF, minion.getX(), minion.getY(), minion.getZ(), 10, 0.3, 0.5, 0.3, 0.05);
                minion.discard();
                removeFromOwnerList(tracked);
                it.remove();
                continue;
            }
            if (now < tracked.riseEndsAtTick) {
                // ease upward out of the ground (start 1.2 blocks under the landing spot), then
                // hand control back to vanilla AI. FIX vs an earlier pass of this file: this used
                // to suspend gravity/AI for the rise window without ever actually repositioning the
                // entity, so it sat fully embedded in solid ground and several minions suffocated
                // to death before the window even ended - confirmed live, now actually interpolates.
                double progress = Math.min(1.0, (double) (now - tracked.riseStartsAtTick) / RISE_TICKS);
                double y = tracked.riseTargetPos.y - 1.2 + 1.2 * progress;
                minion.setPos(tracked.riseTargetPos.x, y, tracked.riseTargetPos.z);
                level.sendParticles(ParticleTypes.SOUL, minion.getX(), minion.getY() + 0.3, minion.getZ(), 2, 0.2, 0.05, 0.2, 0.01);
                if (now + 1 >= tracked.riseEndsAtTick) {
                    minion.setNoGravity(false);
                    if (minion instanceof net.minecraft.world.entity.Mob mob) {
                        mob.setNoAi(false);
                    }
                }
                continue;
            }
            if (minion instanceof net.minecraft.world.entity.Mob mob && isFriendly(tracked.ownerId, mob.getTarget())) {
                mob.setTarget(null);
            }
            if (tracked.isHealer) {
                tickHealerMinion(level, minion, tracked, now);
            }
        }
    }

    private static void removeFromOwnerList(TrackedTemp tracked) {
        List<TrackedTemp> list = MINIONS.get(tracked.ownerId);
        if (list != null) {
            list.remove(tracked);
            if (list.isEmpty()) {
                MINIONS.remove(tracked.ownerId);
            }
        }
    }

    private static void tickHealerMinion(ServerLevel level, LivingEntity healer, TrackedTemp tracked, long now) {
        LivingEntity owner = findOwnerEntity(level, tracked.ownerId);
        if (owner == null || !owner.isAlive()) {
            return;
        }
        Player nearest = level.getNearestPlayer(healer, HEALER_BEAM_RANGE);
        if (healer instanceof net.minecraft.world.entity.Mob mob) {
            mob.setTarget(null);
            if (nearest == null && healer.distanceTo(owner) > 3.0) {
                mob.getNavigation().moveTo(owner, 1.1);
            }
        }
        if (now < tracked.nextBeamAtTick) {
            return;
        }
        if (nearest != null) {
            tracked.nextBeamAtTick = now + HEALER_DAMAGE_BEAM_COOLDOWN_TICKS;
            fireDamageBeam(level, healer, nearest);
            return;
        }
        AttributeInstance ownerMaxHealth = owner.getAttribute(Attributes.MAX_HEALTH);
        double maxHp = ownerMaxHealth != null ? ownerMaxHealth.getValue() : owner.getMaxHealth();
        if (owner.getHealth() < maxHp - 0.5f) {
            tracked.nextBeamAtTick = now + HEALER_HEAL_BEAM_COOLDOWN_TICKS;
            fireHealBeam(level, healer, owner);
        }
    }

    private static LivingEntity findOwnerEntity(ServerLevel level, UUID ownerId) {
        Entity e = level.getEntity(ownerId);
        return e instanceof LivingEntity le ? le : null;
    }

    private static void fireHealBeam(ServerLevel level, LivingEntity healer, LivingEntity owner) {
        Vec3 from = healer.getEyePosition();
        Vec3 to = owner.getEyePosition();
        Vec3 dir = to.subtract(from);
        double dist = dir.length();
        if (dist < 0.1) {
            return;
        }
        dir = dir.normalize();
        drawBeam(level, from, dir, dist, 0xFFF0A0);
        level.playSound(null, from.x, from.y, from.z, SoundEvents.BEACON_AMBIENT, SoundSource.HOSTILE, 0.8f, 1.6f);
        level.playSound(null, from.x, from.y, from.z, SoundEvents.EVOKER_CAST_SPELL, SoundSource.HOSTILE, 0.5f, 1.8f);
        AttributeInstance maxHealth = owner.getAttribute(Attributes.MAX_HEALTH);
        double maxHp = maxHealth != null ? maxHealth.getValue() : owner.getMaxHealth();
        double healAmount = ThreadLocalRandom.current().nextDouble(HEALER_HEAL_MIN, HEALER_HEAL_MAX);
        owner.setHealth((float) Math.min(maxHp, owner.getHealth() + healAmount));
        level.sendParticles(ParticleTypes.HEART, owner.getX(), owner.getY() + 2.0, owner.getZ(), 3, 0.3, 0.2, 0.3, 0.0);
    }

    private static void fireDamageBeam(ServerLevel level, LivingEntity healer, Player target) {
        Vec3 from = healer.getEyePosition();
        Vec3 to = target.getEyePosition();
        Vec3 dir = to.subtract(from);
        double dist = dir.length();
        if (dist < 0.1) {
            return;
        }
        dir = dir.normalize();
        drawBeam(level, from, dir, dist, 0x780028);
        level.playSound(null, from.x, from.y, from.z, SoundEvents.ELDER_GUARDIAN_CURSE, SoundSource.HOSTILE, 1.0f, 0.6f);
        level.playSound(null, from.x, from.y, from.z, SoundEvents.WITHER_SHOOT, SoundSource.HOSTILE, 0.6f, 1.4f);
        target.hurtServer(level, healer.damageSources().mobAttack(healer), (float) HEALER_DAMAGE_BEAM_DAMAGE);
        target.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, 60, 0));
    }

    private static void drawBeam(ServerLevel level, Vec3 from, Vec3 dir, double dist, int rgb) {
        net.minecraft.core.particles.DustParticleOptions dust = new net.minecraft.core.particles.DustParticleOptions(rgb, 1.0f);
        for (double d = 0.0; d <= dist; d += 0.4) {
            Vec3 p = from.add(dir.scale(d));
            level.sendParticles(dust, p.x, p.y, p.z, 1, 0.05, 0.05, 0.05, 0.0);
        }
    }

    // ---- owner death / removal cleanup ----

    /** Owner died: eject the horse and let it go berserk for a while, matching SlopPack's "Vengeful Horse" payoff. Also clears minions. */
    public static void onOwnerDeath(ServerLevel level, LivingEntity owner) {
        MountState state = MOUNTS.get(owner.getUUID());
        if (state != null && state.horse.isAlive()) {
            AbstractHorse horse = state.horse;
            horse.ejectPassengers();
            AttributeInstance speed = horse.getAttribute(Attributes.MOVEMENT_SPEED);
            if (speed != null) {
                speed.setBaseValue(speed.getBaseValue() * VENGEFUL_HORSE_SPEED_MULT);
            }
            MobNames.apply(horse, "Vengeful Horse", MobNames.Tier.BONE);
            state.vengeful = true;
            state.vengefulEndsAtTick = level.getGameTime() + VENGEFUL_HORSE_LIFESPAN_TICKS;
            level.playSound(null, horse.getX(), horse.getY(), horse.getZ(), SoundEvents.HORSE_ANGRY, SoundSource.HOSTILE, 1.6f, 0.6f);
            level.sendParticles(ParticleTypes.ANGRY_VILLAGER, horse.getX(), horse.getY() + 1.2, horse.getZ(), 12, 0.4, 0.3, 0.4, 0.0);
            level.sendParticles(ParticleTypes.SOUL, horse.getX(), horse.getY() + 1.0, horse.getZ(), 20, 0.4, 0.5, 0.4, 0.02);
        } else {
            MOUNTS.remove(owner.getUUID());
        }
        clearMinions(owner.getUUID(), true);
    }

    /** Owner despawned/removed without dying (e.g. discarded on server shutdown): quietly poof everything, no enrage payoff. */
    public static void onOwnerRemoved(ServerLevel level, LivingEntity owner) {
        MountState state = MOUNTS.remove(owner.getUUID());
        if (state != null && state.horse.isAlive()) {
            level.sendParticles(ParticleTypes.POOF, state.horse.getX(), state.horse.getY(), state.horse.getZ(), 10, 0.3, 0.5, 0.3, 0.05);
            state.horse.discard();
        }
        if (state != null) {
            despawnBabyHorses(state);
        }
        clearMinions(owner.getUUID(), true);
    }

    private static void clearMinions(UUID ownerId, boolean poof) {
        List<TrackedTemp> list = MINIONS.remove(ownerId);
        if (list == null) {
            return;
        }
        for (TrackedTemp tracked : list) {
            ALL_TEMP.remove(tracked);
            if (tracked.minion.isAlive()) {
                if (poof && tracked.minion.level() instanceof ServerLevel level) {
                    level.sendParticles(ParticleTypes.POOF, tracked.minion.getX(), tracked.minion.getY(), tracked.minion.getZ(), 10, 0.3, 0.5, 0.3, 0.05);
                }
                tracked.minion.discard();
            }
        }
    }
}
