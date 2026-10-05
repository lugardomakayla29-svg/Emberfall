package com.solme.emberfall.relic;

import com.solme.emberfall.world.RunManager;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Applies Campfire Core and Dragon's Heart regeneration. The rules are the pure {@link RelicRegen}.
 *
 * Cost: a player with no regeneration relic is skipped after one map lookup. A player with one is sampled for movement
 * every tick (two subtractions) and healed once per second. No entities, no allocations in the steady state.
 */
public final class RelicRegenSystem {
    private RelicRegenSystem() {}


    private static final Map<UUID, Integer> STILL_TICKS = new ConcurrentHashMap<>();
    private static final Map<UUID, double[]> LAST_POS = new ConcurrentHashMap<>();

    public static void clear(ServerPlayer player) {
        STILL_TICKS.remove(player.getUUID());
        LAST_POS.remove(player.getUUID());
    }

    public static void tick(MinecraftServer server) {
        for (ServerPlayer p : server.getPlayerList().getPlayers()) {
            if (!PlayerRelics.active(p) || RunManager.slotOf(p) == null || !p.isAlive() || p.isSpectator()) {
                continue;
            }
            RelicStats stats = RelicEffects.stats(p);
            if (!RelicRegen.active(stats)) {
                clear(p);
                continue;
            }
            UUID id = p.getUUID();
            double[] last = LAST_POS.computeIfAbsent(id, k -> new double[] {p.getX(), p.getZ()});
            boolean still = RelicRegen.isStill(p.getX() - last[0], p.getZ() - last[1]);
            last[0] = p.getX();
            last[1] = p.getZ();
            int ticks = RelicRegen.nextStillTicks(STILL_TICKS.getOrDefault(id, 0), still);
            STILL_TICKS.put(id, ticks);
            if (server.getTickCount() % RelicRegen.PERIOD_TICKS == 0 && p.getHealth() < p.getMaxHealth()) {
                float heal = RelicRegen.healFor(stats, ticks);
                if (heal > 0.0F) {
                    p.heal(heal);
                }
            }
        }
    }
}
