package com.solme.emberfall.hub;

import com.solme.emberfall.command.RunCommand;
import com.solme.emberfall.world.ReturnPoints;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * The Expedition Gate ritual. A deliberate right click on the gate starts a short countdown (moving away cancels it). Anyone who
 * clicks while that countdown runs JOINS the same departure (up to {@link GateRules#MAX_PARTY}); when the first click's clock ends,
 * ONE run starts for everyone still holding still, and each member's return point is the spot where they stood, never the gate. After any run ends the gate refuses
 * for a few seconds, so a stray click can never restart a run at once. Nothing here is triggered by walking: that was the
 * old departure plate's flaw (a returning player landed on it and it started a new run).
 */
public final class GateManager {
    private GateManager() {}

    /** One member's wait: where they stood when they clicked (their cancel anchor and their return spot). */
    private record Member(UUID id, double x, double y, double z) {}

    /** One shared departure at one gate. {@code startedTick} is the FIRST click; the group leaves when its clock ends. */
    private static final class Departure {
        final BlockPos gate;
        final long startedTick;
        final List<Member> members = new ArrayList<>();
        /** The "seconds left" value the bell last rang for, so each of 3, 2, 1 rings once however the first tick lines up. */
        int lastBell = -1;

        Departure(BlockPos gate, long startedTick) {
            this.gate = gate;
            this.startedTick = startedTick;
        }

        boolean has(UUID id) {
            for (Member m : members) {
                if (m.id().equals(id)) {
                    return true;
                }
            }
            return false;
        }
    }

    /** The countdown running at each gate position. */
    private static final Map<BlockPos, Departure> DEPARTURES = new HashMap<>();
    /** Server tick at which each player's last run ended. */
    private static final Map<UUID, Long> ENDED = new HashMap<>();

    /** Stamped when a player's run ends, from {@code RunEndHandler}. */
    public static void runEnded(ServerPlayer player, long nowTick) {
        ENDED.put(player.getUUID(), nowTick);
        drop(player.getUUID());
    }

    public static void forget(UUID id) {
        drop(id);
        ENDED.remove(id);
    }

    /** Removes a player from whatever departure holds them; an emptied departure disappears. */
    private static void drop(UUID id) {
        for (java.util.Iterator<Departure> it = DEPARTURES.values().iterator(); it.hasNext(); ) {
            Departure d = it.next();
            d.members.removeIf(m -> m.id().equals(id));
            if (d.members.isEmpty()) {
                it.remove();
            }
        }
    }

    public static boolean counting(UUID id) {
        for (Departure d : DEPARTURES.values()) {
            if (d.has(id)) {
                return true;
            }
        }
        return false;
    }

    /** How many players are waiting at {@code gate} (0 when no countdown runs there). For tests and the join message. */
    public static int waiting(BlockPos gate) {
        Departure d = DEPARTURES.get(gate);
        return d == null ? 0 : d.members.size();
    }

    /** A click on the gate at {@code gate}. Returns a player-facing refusal, or null when the player is now counting down. */
    public static String click(ServerPlayer player, BlockPos gate, long nowTick) {
        if (counting(player.getUUID())) {
            return "The gate is already opening. Hold still.";
        }
        Long ended = ENDED.get(player.getUUID());
        if (ended != null && GateRules.lockedOut(nowTick - ended)) {
            return "The gate is still settling. Give it a moment.";
        }
        if (player.distanceToSqr(gate.getX() + 0.5, gate.getY(), gate.getZ() + 0.5) > GateRules.REACH * GateRules.REACH) {
            return "Step closer to the gate.";
        }
        Member me = new Member(player.getUUID(), player.getX(), player.getY(), player.getZ());
        Departure d = DEPARTURES.get(gate);
        if (d == null) {
            d = new Departure(gate, nowTick);
            d.members.add(me);
            DEPARTURES.put(gate, d);
            player.sendSystemMessage(Component.literal("\u00A76The gate stirs... hold still. Others may join you."), true);
            return null;
        }
        if (!GateRules.canJoin(d.members.size(), nowTick - d.startedTick)) {
            return d.members.size() >= GateRules.MAX_PARTY
                    ? "This party is full (" + GateRules.MAX_PARTY + ")."
                    : "The gate is about to open. Wait for the next departure.";
        }
        d.members.add(me);
        player.sendSystemMessage(Component.literal("\u00A76You join the departure (" + d.members.size() + " at the gate). Hold still."), true);
        return null;
    }

    /** Every tick: cancel members who moved, then start each departure whose first clock has ended. */
    public static void tickAll(MinecraftServer server) {
        if (DEPARTURES.isEmpty()) {
            return;
        }
        long now = server.getTickCount();
        for (Departure d : new ArrayList<>(DEPARTURES.values())) {
            long elapsed = now - d.startedTick;
            int secondsLeft = GateRules.secondsLeft(elapsed);
            boolean bellDue = !GateRules.groupDeparts(elapsed) && secondsLeft != d.lastBell;
            List<RunCommand.PartyMember> going = new ArrayList<>();
            for (Member m : new ArrayList<>(d.members)) {
                ServerPlayer p = server.getPlayerList().getPlayer(m.id());
                if (p == null || !p.isAlive()) {
                    d.members.remove(m);
                    continue;
                }
                if (GateRules.moved(p.getX() - m.x(), p.getZ() - m.z())) {
                    d.members.remove(m);
                    p.sendSystemMessage(Component.literal("\u00A77You stepped away. The gate falls still."), true);
                    continue;
                }
                if (GateRules.groupDeparts(elapsed)) {
                    going.add(returnFor(p, m, d.gate, going.size()));
                    continue;
                }
                if (bellDue) {
                    // One soft bell per second left (3, 2, 1), a little higher each time. Rung here and not on `elapsed % 20`
                    // because the first tick can land one tick after the click and would skip the first bell.
                    com.solme.emberfall.pickup.Cue.play((net.minecraft.server.level.ServerLevel) p.level(), "gate_countdown",
                            net.minecraft.sounds.SoundEvents.NOTE_BLOCK_BELL.value(), net.minecraft.sounds.SoundSource.BLOCKS,
                            p.position(), 0.6F, 0.9F + 0.05F * (GateRules.COUNTDOWN_SECONDS - secondsLeft));
                }
                if (elapsed % 20 == 0) {
                    p.sendSystemMessage(Component.literal("\u00A76Departing in \u00A7e" + GateRules.secondsLeft(elapsed)
                            + (d.members.size() > 1 ? " \u00A77(" + d.members.size() + " at the gate)" : "")), true);
                    // A slow darkening as the gate opens: Darkness is vanilla, costs no entity, and ends by itself.
                    p.addEffect(new MobEffectInstance(MobEffects.DARKNESS, 50, 0, false, false));
                }
            }
            if (bellDue) {
                d.lastBell = secondsLeft;
            }
            if (d.members.isEmpty()) {
                DEPARTURES.remove(d.gate);
                continue;
            }
            if (GateRules.groupDeparts(elapsed)) {
                DEPARTURES.remove(d.gate);
                String error = RunCommand.tryStartParty(going);
                if (error != null) {
                    for (RunCommand.PartyMember m : going) {
                        m.player().sendSystemMessage(Component.literal("\u00A7c" + error));
                    }
                }
            }
        }
    }

    /**
     * Where this member returns. Their own standing spot when it is clear of the gate; otherwise one of the two beside-gate
     * spots (alternating by their place in the party), so a member who clicked from the gate cell is never sent back onto it.
     */
    private static RunCommand.PartyMember returnFor(ServerPlayer p, Member m, BlockPos gate, int index) {
        if (GateRules.ownSpotIsSafe(m.x() - (gate.getX() + 0.5), m.z() - (gate.getZ() + 0.5))) {
            return new RunCommand.PartyMember(p, m.x(), m.y(), m.z());
        }
        HubLayout.Spot spot = GateRules.returnSpot(index);
        BlockPos back = gate.offset(spot.dx() - HubLayout.departureSpot().dx(), 0, spot.dz() - HubLayout.departureSpot().dz());
        return new RunCommand.PartyMember(p, back.getX() + 0.5, back.getY(), back.getZ() + 0.5);
    }
}
