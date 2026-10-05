package com.solme.emberfall.network;

import com.solme.emberfall.wave.SwarmPortal;
import com.solme.emberfall.wave.WaveDirector;
import com.solme.emberfall.world.RunManager;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/** Once a second, tells each in-run player the swarm multiplier, but only when it changed (or when it must be hidden again). */
public final class SwarmHudSync {
    private SwarmHudSync() {}

    private static final Map<UUID, SwarmHudPayload> LAST = new HashMap<>();

    public static void tickAll(MinecraftServer server) {
        if (server.getTickCount() % 20 != 0) {
            return;
        }
        for (ServerPlayer p : server.getPlayerList().getPlayers()) {
            Integer slot = RunManager.slotOf(p);
            SwarmHudPayload now = SwarmHudPayload.HIDDEN;
            if (slot != null) {
                WaveDirector d = WaveDirector.get(slot);
                if (d != null && d.swarmActive()) {
                    now = new SwarmHudPayload(d.swarmTenths(), SwarmPortal.isOpen(slot));
                }
            }
            SwarmHudPayload prev = LAST.get(p.getUUID());
            if ((prev == null && now.tenths() == 0) || now.equals(prev)) {
                continue; // nothing to say
            }
            LAST.put(p.getUUID(), now);
            ServerPlayNetworking.send(p, now);
        }
    }

    public static void forget(UUID player) {
        LAST.remove(player);
    }
}
