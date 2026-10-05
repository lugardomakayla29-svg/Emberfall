package com.solme.emberfall.world;

import com.solme.emberfall.EmberfallMod;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.TamableAnimal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;

import java.util.List;

/**
 * While an in-place run is active, the play area belongs to the run's own mobs: every other mob in it is removed,
 * and nothing else is allowed to spawn there. Otherwise cows, villagers, creepers and cave spiders wander into the
 * fight, steal kills and muddy what the player is looking at.
 *
 * Two cheap parts share one rule ({@link #shouldPurge}):
 * - a load hook discards a foreign mob the instant it is added inside an active run's box (natural spawns,
 *   spawners, eggs, breeding), so nothing ever appears;
 * - a once-a-second sweep clears whatever was already standing there when the run began.
 *
 * Spared on purpose: emberfall mobs, players, and anything a player owns (tamed pets), named mobs (name tag),
 * and leashed mobs, so nobody loses something they care about. Everything removed is discarded silently, with no
 * drops, so a run cannot farm vanilla loot or experience.
 */
public final class RunMobPurge {
    private static final int SWEEP_EVERY_TICKS = 20;
    /** Margin past the box so a mob cannot wait just outside and stroll in. */
    private static final double MARGIN = 8.0;

    private RunMobPurge() {}

    public static void init() {
        ServerEntityEvents.ENTITY_LOAD.register((entity, level) -> {
            if (shouldPurge(entity) && insideActiveRun(level, entity)) {
                entity.discard();
            }
        });
    }

    public static void tick(MinecraftServer server) {
        if (server.getTickCount() % SWEEP_EVERY_TICKS != 0) {
            return;
        }
        for (ArenaInstance arena : RunManager.activeInstances()) {
            if (!arena.inPlace()) {
                continue;
            }
            ServerLevel level = arena.level();
            AABB box = ArenaBoundary.playArea(arena).inflate(MARGIN, 0, MARGIN);
            List<Mob> foreign = level.getEntitiesOfClass(Mob.class, box, RunMobPurge::shouldPurge);
            for (Mob mob : foreign) {
                mob.discard();
            }
        }
    }

    /** True for a mob the run should not contain. Pure decision, no side effects. */
    static boolean shouldPurge(Entity entity) {
        if (!(entity instanceof Mob mob) || entity instanceof Player) {
            return false;
        }
        var key = BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType());
        if (key != null && EmberfallMod.MOD_ID.equals(key.getNamespace())) {
            return false; // the run's own mobs, including bosses and their parts
        }
        if (entity instanceof TamableAnimal tame && tame.isTame()) {
            return false; // a player's pet
        }
        if (mob.getTags().contains(com.solme.emberfall.bot.BotScout.TAG)) {
            return false; // the EmberTester's path scout: one per level, kept, or it would be respawned every sweep
        }
        return !(mob.hasCustomName() || mob.isLeashed());
    }

    private static boolean insideActiveRun(ServerLevel level, Entity entity) {
        for (ArenaInstance arena : RunManager.activeInstances()) {
            if (arena.inPlace() && arena.level() == level
                    && ArenaBoundary.playArea(arena).inflate(MARGIN, 0, MARGIN).contains(entity.position())) {
                return true;
            }
        }
        return false;
    }
}
