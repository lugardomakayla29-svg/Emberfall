package com.solme.emberfall.network;

import com.solme.emberfall.pickup.PickupSystem;
import com.solme.emberfall.progression.MetaProgressionData;
import com.solme.emberfall.world.RunManager;
import com.solme.emberfall.world.RunStats;
import com.solme.emberfall.world.RunTelemetry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Feeds the top-middle timer and the resource counters. Once a second, per in-run player, it builds the
 * numbers and sends them only if something other than the clock changed, or every {@link #RESYNC_SECONDS}
 * seconds so the client clock cannot drift. The clock itself runs on the client between packets.
 */
public final class RunHudSync {
    private static final int CHECK_EVERY_TICKS = 20;
    /** How often the elapsed time is re-sent even if nothing else changed (client clock correction). */
    static final int RESYNC_SECONDS = 30;

    private record Sent(RunHudPayload state, long atTick) {}

    private static final Map<UUID, Sent> lastSent = new HashMap<>();
    private static final Map<UUID, Integer> sendCounts = new HashMap<>();

    private RunHudSync() {}

    public static void tickAll(MinecraftServer server) {
        long now = server.getTickCount();
        if (now % CHECK_EVERY_TICKS != 0) {
            return;
        }
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            Integer slot = RunManager.slotOf(player);
            if (slot == null) {
                continue; // not in a run: hide() already cleared the display
            }
            RunHudPayload built = build(server, player, slot);
            Sent prev = lastSent.get(player.getUUID());
            boolean sameValues = prev != null && sameExceptClock(prev.state(), built);
            boolean resyncDue = prev == null || now - prev.atTick() >= RESYNC_SECONDS * 20L;
            if (sameValues && !resyncDue) {
                continue;
            }
            lastSent.put(player.getUUID(), new Sent(built, now));
            sendCounts.merge(player.getUUID(), 1, Integer::sum);
            ServerPlayNetworking.send(player, built);
        }
    }

    static RunHudPayload build(MinecraftServer server, ServerPlayer player, int slot) {
        int xpPercent = Math.max(0, Math.min(100, Math.round(player.experienceProgress * 100.0F)));
        long silver = MetaProgressionData.get(server).getBalance(player.getUUID());
        int elapsed = (int) Math.min(Integer.MAX_VALUE, RunTelemetry.elapsedSeconds(slot));
        return new RunHudPayload(true, elapsed, player.experienceLevel, xpPercent, PickupSystem.gold(player),
                silver, RunStats.kills(player), com.solme.emberfall.relic.PlayerRelics.nextChestPrice(player), relicPairs(player));
    }

    /** Owned relics as (RelicPool index, stacks) pairs in pool order, so two builds of the same inventory are equal. */
    static int[] relicPairs(ServerPlayer player) {
        Map<String, Integer> owned = com.solme.emberfall.relic.PlayerRelics.all(player);
        if (owned.isEmpty()) {
            return new int[0];
        }
        var pool = com.solme.emberfall.relic.RelicPool.all();
        int[] out = new int[owned.size() * 2];
        int n = 0;
        for (int i = 0; i < pool.size() && n < out.length; i++) {
            int stacks = owned.getOrDefault(pool.get(i).id(), 0);
            if (stacks > 0) {
                out[n++] = i;
                out[n++] = stacks;
            }
        }
        return n == out.length ? out : java.util.Arrays.copyOf(out, n);
    }

    private static boolean sameExceptClock(RunHudPayload a, RunHudPayload b) {
        return a.level() == b.level() && a.xpPercent() == b.xpPercent() && a.gold() == b.gold()
                && a.silver() == b.silver() && a.kills() == b.kills() && a.chestPrice() == b.chestPrice()
                && java.util.Arrays.equals(a.relics(), b.relics());
    }

    /** Clears the display: called when the player leaves a run. Safe for a disconnected player. */
    public static void hide(ServerPlayer player) {
        sendCounts.remove(player.getUUID());
        if (lastSent.remove(player.getUUID()) != null) {
            try {
                ServerPlayNetworking.send(player, RunHudPayload.HIDDEN);
            } catch (RuntimeException ignored) {
                // connection may already be closing on a disconnect
            }
        }
    }

    /** Debug/test: how many packets this player has been sent since the run began. */
    public static int sendCount(ServerPlayer player) {
        return sendCounts.getOrDefault(player.getUUID(), 0);
    }

    /** Debug/test: "elapsed|level|xp%|gold|silver|kills" of the last packet, or "none". */
    public static String describeLast(ServerPlayer player) {
        Sent s = lastSent.get(player.getUUID());
        if (s == null) {
            return "none";
        }
        RunHudPayload p = s.state();
        return p.elapsedSeconds() + "|" + p.level() + "|" + p.xpPercent() + "|" + p.gold() + "|" + p.silver() + "|" + p.kills()
                + "|" + java.util.Arrays.toString(p.relics());
    }
}
