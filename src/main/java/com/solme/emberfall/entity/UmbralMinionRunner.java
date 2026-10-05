package com.solme.emberfall.entity;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Tracks Umbral Magus's temporary vanilla-mob summons (Umbral Thrall,
 * Umbral Colossus) and the small decorative "stitched" slimes riding
 * each one. Ported from SlopPack's per-mob BukkitRunnable ticker plus
 * its {@code activeMinions}/{@code stitchedSlimes} maps and
 * {@code cleanupMinions} - condensed into one global tracker since
 * these summons are plain vanilla Zombie/Skeleton/Husk/WitherSkeleton
 * entities with no custom class of their own to own this state,
 * following the same "global ACTIVE list ticked once a tick" pattern
 * already used by {@link SpinBarrageRunner} and {@link TrackedProjectiles}.
 */
public final class UmbralMinionRunner {
    private UmbralMinionRunner() {}

    private static final List<TrackedMinion> ACTIVE = new ArrayList<>();
    /** ownerId -> minion entityIds, so a dead/despawned Magus can clean up everything it summoned at once. */
    private static final Map<UUID, List<UUID>> BY_OWNER = new HashMap<>();

    private static final class TrackedMinion {
        final UUID ownerId;
        final LivingEntity minion;
        final long expireAtTick;
        final List<LivingEntity> decor;
        final List<double[]> decorOffsets; // {x, z, yLevel, phase}

        TrackedMinion(UUID ownerId, LivingEntity minion, long expireAtTick, List<LivingEntity> decor, List<double[]> decorOffsets) {
            this.ownerId = ownerId;
            this.minion = minion;
            this.expireAtTick = expireAtTick;
            this.decor = decor;
            this.decorOffsets = decorOffsets;
        }
    }

    /**
     * Registers a summoned minion for timed self-destruct and gives it
     * {@code decorCount} decorative stitched-slime attachments, mirroring
     * SlopPack's stitchSlimesOnto. {@code decorScale} matches the small
     * cosmetic slime's {@code Attributes.SCALE}.
     */
    public static void register(ServerLevel level, LivingEntity owner, LivingEntity minion, int lifespanTicks,
                                 int decorCount, double decorScale) {
        long expireAt = level.getGameTime() + lifespanTicks;
        List<LivingEntity> decor = new ArrayList<>();
        List<double[]> offsets = new ArrayList<>();
        double topOfBody = minion.getBoundingBox().maxY - minion.getY();
        double ringRadius = Math.min(0.35, 0.16 + 0.05 * decorCount);

        for (int i = 0; i < decorCount; i++) {
            double angle = (Math.PI * 2 / decorCount) * i + (level.random.nextDouble() * 0.6 - 0.3);
            double ox = decorCount == 1 ? 0.0 : Math.cos(angle) * ringRadius;
            double oz = decorCount == 1 ? 0.0 : Math.sin(angle) * ringRadius;
            double oy = topOfBody + decorScale * 0.4;
            Vec3 spawnPos = minion.position().add(ox, oy, oz);

            net.minecraft.world.entity.monster.Slime slime = net.minecraft.world.entity.EntityType.SLIME.create(
                    level, net.minecraft.world.entity.EntitySpawnReason.MOB_SUMMONED);
            if (slime == null) {
                continue;
            }
            slime.setSize(1, true);
            slime.setPos(spawnPos.x, spawnPos.y, spawnPos.z);
            slime.setInvulnerable(true);
            slime.setNoGravity(true);
            slime.setNoAi(true);
            slime.setSilent(true);
            net.minecraft.world.entity.ai.attributes.AttributeInstance scaleAttr =
                    slime.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.SCALE);
            if (scaleAttr != null) {
                scaleAttr.setBaseValue(decorScale);
            }
            level.addFreshEntity(slime);
            level.sendParticles(ParticleTypes.ITEM_SLIME, spawnPos.x, spawnPos.y, spawnPos.z, 10, 0.15, 0.15, 0.15, 0.02);

            decor.add(slime);
            offsets.add(new double[]{ox, oz, oy, level.random.nextDouble() * Math.PI * 2});
        }

        TrackedMinion tracked = new TrackedMinion(owner.getUUID(), minion, expireAt, decor, offsets);
        ACTIVE.add(tracked);
        BY_OWNER.computeIfAbsent(owner.getUUID(), k -> new ArrayList<>()).add(minion.getUUID());
    }

    public static void tickAll(MinecraftServer server) {
        if (ACTIVE.isEmpty()) {
            return;
        }
        long now = server.overworld().getGameTime();
        Iterator<TrackedMinion> it = ACTIVE.iterator();
        while (it.hasNext()) {
            TrackedMinion tracked = it.next();
            LivingEntity minion = tracked.minion;
            if (!(minion.level() instanceof ServerLevel level)) {
                removeDecor(tracked);
                it.remove();
                continue;
            }
            if (!minion.isAlive()) {
                level.sendParticles(ParticleTypes.SMOKE, minion.getX(), minion.getY() + 0.5, minion.getZ(),
                        10, 0.2, 0.2, 0.2, 0.01);
                removeDecor(tracked);
                it.remove();
                continue;
            }
            if (now >= tracked.expireAtTick) {
                level.sendParticles(ParticleTypes.SMOKE, minion.getX(), minion.getY() + 0.7, minion.getZ(),
                        20, 0.3, 0.6, 0.3, 0.02);
                minion.discard();
                removeDecor(tracked);
                it.remove();
                continue;
            }

            float yaw = minion.getYRot();
            double rad = Math.toRadians(yaw);
            for (int i = 0; i < tracked.decor.size(); i++) {
                LivingEntity slime = tracked.decor.get(i);
                if (!slime.isAlive()) {
                    continue;
                }
                double[] off = tracked.decorOffsets.get(i);
                double rx = off[0] * Math.cos(rad) - off[1] * Math.sin(rad);
                double rz = off[0] * Math.sin(rad) + off[1] * Math.cos(rad);
                double bob = Math.sin(now * 0.1 + off[3]) * 0.04;
                slime.setPos(minion.getX() + rx, minion.getY() + off[2] + bob, minion.getZ() + rz);
            }
        }
        ACTIVE.removeIf(t -> !t.minion.isAlive());
    }

    private static void removeDecor(TrackedMinion tracked) {
        for (LivingEntity slime : tracked.decor) {
            if (slime.isAlive()) {
                if (slime.level() instanceof ServerLevel level) {
                    level.sendParticles(ParticleTypes.ITEM_SLIME, slime.getX(), slime.getY(), slime.getZ(),
                            8, 0.15, 0.15, 0.15, 0.02);
                }
                slime.discard();
            }
        }
        List<UUID> owned = BY_OWNER.get(tracked.ownerId);
        if (owned != null) {
            owned.remove(tracked.minion.getUUID());
            if (owned.isEmpty()) {
                BY_OWNER.remove(tracked.ownerId);
            }
        }
    }

    /** Immediately removes every minion (and its decor) summoned by {@code owner}. Call when the Magus dies/despawns. */
    public static void cleanupForOwner(UUID ownerId) {
        List<UUID> owned = BY_OWNER.remove(ownerId);
        if (owned == null || owned.isEmpty()) {
            return;
        }
        ACTIVE.removeIf(tracked -> {
            if (!tracked.ownerId.equals(ownerId)) {
                return false;
            }
            LivingEntity minion = tracked.minion;
            if (minion.isAlive()) {
                if (minion.level() instanceof ServerLevel level) {
                    level.sendParticles(ParticleTypes.SMOKE, minion.getX(), minion.getY() + 0.5, minion.getZ(),
                            10, 0.2, 0.2, 0.2, 0.01);
                }
                minion.discard();
            }
            for (LivingEntity slime : tracked.decor) {
                if (slime.isAlive()) {
                    slime.discard();
                }
            }
            return true;
        });
    }
}
