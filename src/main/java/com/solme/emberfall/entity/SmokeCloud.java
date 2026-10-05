package com.solme.emberfall.entity;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * Blightfeather Marksman's Smoke Bomb zone. Sibling to {@link ScorchedGround}
 * (same lightweight "no real entity, just a tracked volume" pattern) but
 * deliberately not a reskin of it: SlopPack's smoke bomb dealt flat DPS with
 * no other effect, which felt identical to Cinderbrand Reaver's fire patch
 * despite the very different theme (blinding gas trap vs. burning ground).
 * This version keeps the damage tick but adds a short Blindness debuff on
 * the first tick a player is caught, so it actually reads as a smoke bomb
 * rather than a same-numbers-different-particle-color fire patch.
 */
public final class SmokeCloud {
    private SmokeCloud() {}

    private static final List<Cloud> ACTIVE = new ArrayList<>();

    private static final class Cloud {
        final ServerLevel level;
        final BlockPos pos;
        final double radius;
        final long expiresAtTick;
        final float damagePerSecond;

        Cloud(ServerLevel level, BlockPos pos, double radius, long expiresAtTick, float damagePerSecond) {
            this.level = level;
            this.pos = pos;
            this.radius = radius;
            this.expiresAtTick = expiresAtTick;
            this.damagePerSecond = damagePerSecond;
        }
    }

    public static void spawn(ServerLevel level, BlockPos pos, int durationTicks, double radius, float damagePerSecond) {
        ACTIVE.add(new Cloud(level, pos, radius, level.getGameTime() + durationTicks, damagePerSecond));
    }

    public static void tickAll(MinecraftServer server) {
        if (ACTIVE.isEmpty()) {
            return;
        }
        Iterator<Cloud> it = ACTIVE.iterator();
        while (it.hasNext()) {
            Cloud cloud = it.next();
            long now = cloud.level.getGameTime();
            if (now >= cloud.expiresAtTick) {
                it.remove();
                continue;
            }
            AABB box = new AABB(cloud.pos).inflate(cloud.radius, 1.2, cloud.radius);
            if (now % 20 == 0) {
                List<Player> caught = cloud.level.getEntitiesOfClass(Player.class, box,
                        p -> p.isAlive() && !p.isSpectator());
                for (Player player : caught) {
                    player.hurtServer(cloud.level, cloud.level.damageSources().magic(), cloud.damagePerSecond);
                    if (!player.hasEffect(MobEffects.BLINDNESS)) {
                        player.addEffect(new MobEffectInstance(MobEffects.BLINDNESS, 40, 0));
                    }
                }
            }
            if (now % 4 == 0) {
                cloud.level.sendParticles(ParticleTypes.LARGE_SMOKE,
                        cloud.pos.getX() + 0.5, cloud.pos.getY() + 0.6, cloud.pos.getZ() + 0.5,
                        4, cloud.radius * 0.4, 0.4, cloud.radius * 0.4, 0.02);
            }
        }
    }
}
