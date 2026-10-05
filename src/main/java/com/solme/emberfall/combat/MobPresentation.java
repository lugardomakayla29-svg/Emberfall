package com.solme.emberfall.combat;

import com.solme.emberfall.entity.HordeSkeleton;
import com.solme.emberfall.entity.HordeSpider;
import com.solme.emberfall.entity.HordeZombie;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;

/**
 * Cross-cutting mob presentation, owned in ONE place instead of copy-pasted into 20 mob classes:
 * spawn effect, tier aura and death payoff for every Emberfall hostile.
 *
 * <p>Tiers: 0 filler, 1 veteran, 2 elite or boss part. A veteran is a horde mob whose own
 * {@code isVeteran()} is true; anything that is not a plain horde mob or slime fragment is an elite.
 *
 * <p>Performance: the aura loop runs every {@link #AURA_INTERVAL} ticks, walks only the mobs near a
 * player, and each aura call is additionally phase-gated per entity by {@link Fx#shouldPulse}. Fillers
 * (tier 0) are skipped entirely, so the common case costs one classification and nothing else.
 */
public final class MobPresentation {
    private MobPresentation() {}

    private static final int AURA_INTERVAL = 2;
    /** Beyond this a player cannot see an aura anyway, so do not spend packets on it. */
    private static final double AURA_RANGE = 48.0;

    public static void register() {
        // Spawn effect.
        ServerEntityEvents.ENTITY_LOAD.register((entity, world) -> {
            if (entity instanceof Mob mob && world instanceof ServerLevel level && isEmberfall(mob)) {
                int tier = tierOf(mob);
                if (tier >= 1) {
                    Fx.spawn(level, mob, tier);
                }
            }
        });
        // Death payoff.
        ServerLivingEntityEvents.AFTER_DEATH.register((entity, source) -> {
            if (entity instanceof Mob mob && entity.level() instanceof ServerLevel level && isEmberfall(mob)) {
                Fx.death(level, mob, tierOf(mob));
            }
        });
        // Tier auras.
        ServerTickEvents.END_SERVER_TICK.register(MobPresentation::tickAuras);
    }

    private static void tickAuras(MinecraftServer server) {
        if (server.getTickCount() % AURA_INTERVAL != 0) {
            return;
        }
        for (ServerLevel level : server.getAllLevels()) {
            if (level.players().isEmpty()) {
                continue;
            }
            // One pass per level over the union of "near a player", so a mob in range of several players
            // is visited once (no doubled aura) and no intermediate lists are allocated.
            for (Mob mob : level.getEntitiesOfClass(Mob.class, unionBox(level), MobPresentation::isEmberfall)) {
                if (!nearAnyPlayer(level, mob)) {
                    continue;
                }
                switch (tierOf(mob)) {
                    case 1 -> Fx.veteranAura(level, mob);
                    case 2 -> Fx.eliteAura(level, mob);
                    default -> { }
                }
            }
        }
    }

    private static net.minecraft.world.phys.AABB unionBox(ServerLevel level) {
        net.minecraft.world.phys.AABB box = null;
        for (var p : level.players()) {
            net.minecraft.world.phys.AABB b = p.getBoundingBox().inflate(AURA_RANGE);
            box = box == null ? b : box.minmax(b);
        }
        return box;
    }

    private static boolean nearAnyPlayer(ServerLevel level, Mob mob) {
        double r2 = AURA_RANGE * AURA_RANGE;
        for (var p : level.players()) {
            if (p.distanceToSqr(mob) <= r2) {
                return true;
            }
        }
        return false;
    }

    public static boolean isEmberfall(LivingEntity e) {
        return BuiltInRegistries.ENTITY_TYPE.getKey(e.getType()).getNamespace().equals("emberfall");
    }

    /** 0 filler, 1 veteran, 2 elite or boss. */
    public static int tierOf(LivingEntity e) {
        if (e instanceof HordeZombie z) return z.isVeteran() ? 1 : 0;
        if (e instanceof HordeSkeleton s) return s.isVeteran() ? 1 : 0;
        if (e instanceof HordeSpider sp) return sp.isVeteran() ? 1 : 0;
        String path = BuiltInRegistries.ENTITY_TYPE.getKey(e.getType()).getPath();
        // Slime fragments and minions are fillers; segments and display parts are handled by their brain.
        if (path.contains("pink_slime") || path.contains("minion") || path.contains("segment") || path.contains("spawn")) {
            return 0;
        }
        return 2;
    }
}
