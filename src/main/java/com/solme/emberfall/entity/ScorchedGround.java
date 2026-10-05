package com.solme.emberfall.entity;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * Lightweight lingering fire-damage-zone helper shared by Corrupted
 * archetype abilities (currently Cinderbrand Reaver's Solarburst Overdrive
 * finisher and Magma Plume). Deliberately does NOT place real fire
 * blocks - arena floors shouldn't be permanently scarred or need cleanup
 * bookkeeping - it's a pure particle + periodic-damage volume tracked here
 * and driven by {@link #tickAll}, registered once from
 * {@code EmberfallMod.onInitialize}.
 */
public final class ScorchedGround {
    private ScorchedGround() {}

    private static final List<Patch> ACTIVE = new ArrayList<>();

    private static final class Patch {
        final ServerLevel level;
        final BlockPos pos;
        final double radius;
        final long expiresAtTick;
        final float damagePerSecond;

        Patch(ServerLevel level, BlockPos pos, double radius, long expiresAtTick, float damagePerSecond) {
            this.level = level;
            this.pos = pos;
            this.radius = radius;
            this.expiresAtTick = expiresAtTick;
            this.damagePerSecond = damagePerSecond;
        }
    }

    /** Starts a new scorched patch. durationTicks total lifetime, 1 damage tick/sec. */
    public static void spawn(ServerLevel level, BlockPos pos, int durationTicks, double radius) {
        spawn(level, pos, durationTicks, radius, 2.0F);
    }

    public static void spawn(ServerLevel level, BlockPos pos, int durationTicks, double radius, float damagePerSecond) {
        ACTIVE.add(new Patch(level, pos, radius, level.getGameTime() + durationTicks, damagePerSecond));
    }

    public static void tickAll(MinecraftServer server) {
        if (ACTIVE.isEmpty()) {
            return;
        }
        Iterator<Patch> it = ACTIVE.iterator();
        while (it.hasNext()) {
            Patch patch = it.next();
            long now = patch.level.getGameTime();
            if (now >= patch.expiresAtTick) {
                it.remove();
                continue;
            }
            AABB box = new AABB(patch.pos).inflate(patch.radius, 1.0, patch.radius);
            if (now % 20 == 0) {
                List<Player> caught = patch.level.getEntitiesOfClass(Player.class, box,
                        p -> p.isAlive() && !p.isSpectator());
                for (Player player : caught) {
                    player.hurtServer(patch.level, patch.level.damageSources().hotFloor(), patch.damagePerSecond);
                }
            }
            if (now % 4 == 0) {
                patch.level.sendParticles(ParticleTypes.FLAME,
                        patch.pos.getX() + 0.5, patch.pos.getY() + 0.15, patch.pos.getZ() + 0.5,
                        3, patch.radius * 0.4, 0.08, patch.radius * 0.4, 0.01);
            }
        }
    }
}
