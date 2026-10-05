package com.solme.emberfall.hub;

import com.solme.emberfall.command.RunCommand;
import com.solme.emberfall.world.ReturnPoints;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * The Expedition Gate ritual. A deliberate right click on the gate starts a short countdown (moving away cancels it); when it
 * ends, the run starts and the player's return point is set BESIDE the gate, never on it. After any run ends the gate refuses
 * for a few seconds, so a stray click can never restart a run at once. Nothing here is triggered by walking: that was the
 * old departure plate's flaw (a returning player landed on it and it started a new run).
 */
public final class GateManager {
    private GateManager() {}

    private record Pending(BlockPos gate, double x, double z, long startedTick) {}

    private static final Map<UUID, Pending> PENDING = new HashMap<>();
    /** Server tick at which each player's last run ended. */
    private static final Map<UUID, Long> ENDED = new HashMap<>();

    /** Stamped when a player's run ends, from {@code RunEndHandler}. */
    public static void runEnded(ServerPlayer player, long nowTick) {
        ENDED.put(player.getUUID(), nowTick);
        PENDING.remove(player.getUUID());
    }

    public static void forget(UUID id) {
        PENDING.remove(id);
        ENDED.remove(id);
    }

    public static boolean counting(UUID id) {
        return PENDING.containsKey(id);
    }

    /** A click on the gate at {@code gate}. Returns a player-facing refusal, or null when the countdown began. */
    public static String click(ServerPlayer player, BlockPos gate, long nowTick) {
        if (PENDING.containsKey(player.getUUID())) {
            return "The gate is already opening. Hold still.";
        }
        Long ended = ENDED.get(player.getUUID());
        if (ended != null && GateRules.lockedOut(nowTick - ended)) {
            return "The gate is still settling. Give it a moment.";
        }
        if (player.distanceToSqr(gate.getX() + 0.5, gate.getY(), gate.getZ() + 0.5) > GateRules.REACH * GateRules.REACH) {
            return "Step closer to the gate.";
        }
        PENDING.put(player.getUUID(), new Pending(gate, player.getX(), player.getZ(), nowTick));
        player.sendSystemMessage(Component.literal("§6The gate stirs... hold still."), true);
        return null;
    }

    /** Every tick: advance each countdown, cancel on movement, start the run when it ends. */
    public static void tickAll(MinecraftServer server) {
        if (PENDING.isEmpty()) {
            return;
        }
        long now = server.getTickCount();
        for (Map.Entry<UUID, Pending> e : new java.util.ArrayList<>(PENDING.entrySet())) {
            ServerPlayer p = server.getPlayerList().getPlayer(e.getKey());
            Pending pend = e.getValue();
            if (p == null || !p.isAlive()) {
                PENDING.remove(e.getKey());
                continue;
            }
            if (GateRules.moved(p.getX() - pend.x(), p.getZ() - pend.z())) {
                PENDING.remove(e.getKey());
                p.sendSystemMessage(Component.literal("§7You stepped away. The gate falls still."), true);
                continue;
            }
            long elapsed = now - pend.startedTick();
            if (GateRules.countdownDone(elapsed)) {
                PENDING.remove(e.getKey());
                start(p, pend.gate());
                continue;
            }
            if (elapsed % 20 == 0) {
                p.sendSystemMessage(Component.literal("§6Departing in §e" + GateRules.secondsLeft(elapsed)), true);
                // A slow darkening as the gate opens: Darkness is vanilla, costs no entity, and ends by itself.
                p.addEffect(new MobEffectInstance(MobEffects.DARKNESS, 50, 0, false, false));
            }
        }
    }

    private static void start(ServerPlayer p, BlockPos gate) {
        HubLayout.Spot spot = GateRules.returnSpot(Math.floorMod(p.getUUID().hashCode(), 2));
        // The return spot is relative to the gate cell at (0,-1): shift by the difference from the gate's own offset.
        BlockPos back = gate.offset(spot.dx() - HubLayout.departureSpot().dx(), 0, spot.dz() - HubLayout.departureSpot().dz());
        String error = RunCommand.tryStartFrom(p, back.getX() + 0.5, back.getY(), back.getZ() + 0.5);
        if (error != null) {
            p.sendSystemMessage(Component.literal("§c" + error));
        }
    }
}
