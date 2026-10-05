package com.solme.emberfall.entity;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;

/**
 * Drives Blightfeather Marksman's Spin Barrage (self-spin firing arrows in
 * an expanding ring) over multiple ticks - the Fabric equivalent of
 * SlopPack's BukkitRunnable-driven version. Registered once from
 * {@code EmberfallMod.onInitialize} like {@link ScorchedGround} and
 * {@link TrackedProjectiles}.
 */
public final class SpinBarrageRunner {
    private SpinBarrageRunner() {}

    private static final List<Spin> ACTIVE = new ArrayList<>();

    private static final class Spin {
        final LivingEntity self;
        final double statMultiplier;
        float yaw;
        int ticks = 0;

        Spin(LivingEntity self, double statMultiplier) {
            this.self = self;
            this.statMultiplier = statMultiplier;
            this.yaw = self.getYRot();
        }
    }

    public static void start(LivingEntity self, ServerLevel level, double statMultiplier) {
        ACTIVE.add(new Spin(self, statMultiplier));
    }

    public static void tickAll(MinecraftServer server) {
        if (ACTIVE.isEmpty()) {
            return;
        }
        ACTIVE.removeIf(spin -> {
            if (!spin.self.isAlive() || !(spin.self.level() instanceof ServerLevel level)) {
                return true;
            }
            spin.ticks++;
            spin.yaw += 55.0F;
            spin.self.setYRot(spin.yaw);
            spin.self.setYHeadRot(spin.yaw);
            Vec3 loc = spin.self.position();
            level.sendParticles(ParticleTypes.HAPPY_VILLAGER, loc.x, loc.y + 1.0, loc.z, 3, 0.4, 0.3, 0.4, 0.0);

            if (spin.ticks % 4 == 0) {
                boolean bigShot = spin.ticks % 8 == 0;
                level.playSound(null, loc.x, loc.y, loc.z,
                        bigShot ? SoundEvents.ARROW_SHOOT : SoundEvents.SKELETON_SHOOT,
                        SoundSource.HOSTILE, 1.0F, 0.8F + spin.self.getRandom().nextFloat() * 0.4F);
                Vec3 eye = spin.self.getEyePosition();
                for (int i = 0; i < 4; i++) {
                    float shotYaw = spin.yaw + i * 90.0F;
                    Vec3 dir = directionFromYaw(shotYaw);
                    BlightfeatherMarksman.spawnSpinArrow(spin.self, level, eye, dir);
                }
            }

            if (spin.ticks >= 32) {
                level.playSound(null, loc.x, loc.y, loc.z, SoundEvents.GENERIC_EXPLODE, SoundSource.HOSTILE, 0.7F, 1.4F);
                level.sendParticles(ParticleTypes.CLOUD, loc.x, loc.y + 1.0, loc.z, 25, 0.5, 0.5, 0.5, 0.05);
                return true;
            }
            return false;
        });
    }

    private static Vec3 directionFromYaw(float yaw) {
        double yawRad = Math.toRadians(yaw);
        return new Vec3(-Math.sin(yawRad), 0.0, Math.cos(yawRad));
    }
}
