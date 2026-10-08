package com.solme.emberfall.rift;

import com.solme.emberfall.command.RunCommand;
import com.solme.emberfall.hub.GateRules;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Stepping into an open Rift. A deliberate right click on the Rift's click target starts the same short countdown the old Expedition Gate
 * used (moving away cancels it); anyone who clicks while it runs JOINS the same departure, up to {@link RiftRules#MAX_PARTY}; when the first
 * click's clock ends ONE run starts for everyone still holding still, and each member returns to the spot where they stood. After a run ends
 * the Rift refuses that player for {@link RiftRules#LOCKOUT_TICKS}, so a stray click can never restart a run at once.
 *
 * Every rule is a pure predicate in {@link RiftRules}, {@link RiftEntry} and {@code GateRules}; this class supplies the facts (who clicked,
 * where they stand) and keeps the departures. It mirrors {@code GateManager}, which is proven live; the difference is the key (a Rift, not a block).
 */
public final class RiftGate {
    private RiftGate() {}

    /** One member's wait: where they stood when they clicked (their cancel anchor and their return spot). */
    private record Member(UUID id, double x, double y, double z) {}

    /** One shared departure at one Rift. {@code startedTick} is the FIRST click; the group leaves when its clock ends. */
    private static final class Departure {
        final RiftManager.Rift rift;
        final long startedTick;
        final List<Member> members = new ArrayList<>();
        /** The "seconds left" the bell last rang for, so each of 3, 2, 1 rings once however the first tick lines up. */
        int lastBell = -1;

        Departure(RiftManager.Rift rift, long startedTick) {
            this.rift = rift;
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

    /** The countdown running at each Rift. */
    private static final Map<RiftManager.Rift, Departure> DEPARTURES = new HashMap<>();
    /** Server tick at which each player's last run ended. */
    private static final Map<UUID, Long> ENDED = new HashMap<>();

    /** Stamped when a player's run ends, from {@code RunEndHandler}. */
    public static void runEnded(ServerPlayer player, long nowTick) {
        ENDED.put(player.getUUID(), nowTick);
        drop(player.getUUID());
    }

    /** Forgets a player completely (they left the server). */
    public static void forget(UUID id) {
        drop(id);
        ENDED.remove(id);
    }

    /** Forgets everything (server stop, tests). */
    public static void clear() {
        DEPARTURES.clear();
        ENDED.clear();
    }

    private static void drop(UUID id) {
        for (Departure d : new ArrayList<>(DEPARTURES.values())) {
            d.members.removeIf(m -> m.id().equals(id));
            if (d.members.isEmpty()) {
                DEPARTURES.remove(d.rift);
            }
        }
    }

    /** True while this player is waiting in a countdown. */
    public static boolean counting(UUID id) {
        for (Departure d : DEPARTURES.values()) {
            if (d.has(id)) {
                return true;
            }
        }
        return false;
    }

    /** How many are waiting at this Rift (0 when none). {@link RiftManager.Rift#waiting} mirrors this so an occupied Rift is never closed as idle. */
    public static int waiting(RiftManager.Rift rift) {
        Departure d = DEPARTURES.get(rift);
        return d == null ? 0 : d.members.size();
    }

    /**
     * A right click on a Rift click target. Returns a player-facing refusal, or null when the player is now counting down.
     * A target that no live Rift owns (left from before a restart) is discarded here, the first moment the server meets it.
     */
    public static String click(ServerPlayer player, Entity target, long nowTick) {
        RiftManager.Rift rift = RiftManager.riftOf(target);
        if (rift == null) {
            target.discard();
            return RiftEntry.refusal(false, false);
        }
        String notOpen = RiftEntry.refusal(true, rift.isOpen(nowTick));
        if (notOpen != null) {
            return notOpen;
        }
        if (counting(player.getUUID())) {
            return "the Rift is already opening. Hold still";
        }
        Departure d = DEPARTURES.get(rift);
        Long ended = ENDED.get(player.getUUID());
        long sinceEnd = ended == null ? -1L : nowTick - ended;   // negative = never ended (GateRules.lockedOut)
        double dist = RiftEntry.reachDistance(player.getX() - target.getX(), player.getZ() - target.getZ(), player.getY(), target.getY());
        String why = RiftRules.refusal(dist, sinceEnd, d == null ? 0 : d.members.size());
        if (why != null) {
            return why;
        }
        if (d != null && !GateRules.canJoin(d.members.size(), nowTick - d.startedTick)) {
            return "the Rift is about to open. Wait for the next departure";
        }
        Member me = new Member(player.getUUID(), player.getX(), player.getY(), player.getZ());
        if (d == null) {
            d = new Departure(rift, nowTick);
            DEPARTURES.put(rift, d);
            d.members.add(me);
            rift.waiting = d.members.size();
            player.sendSystemMessage(Component.literal("\u00A75The Rift stirs... hold still. Others may join you."), true);
            return null;
        }
        d.members.add(me);
        rift.waiting = d.members.size();
        player.sendSystemMessage(Component.literal("\u00A75You join the departure (" + d.members.size() + " at the Rift). Hold still."), true);
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
                    p.sendSystemMessage(Component.literal("\u00A77You stepped away. The Rift falls still."), true);
                    continue;
                }
                if (GateRules.groupDeparts(elapsed)) {
                    going.add(new RunCommand.PartyMember(p, m.x(), m.y(), m.z()));
                    continue;
                }
                if (bellDue) {
                    com.solme.emberfall.pickup.Cue.play((net.minecraft.server.level.ServerLevel) p.level(), "gate_countdown",
                            net.minecraft.sounds.SoundEvents.NOTE_BLOCK_BELL.value(), net.minecraft.sounds.SoundSource.BLOCKS,
                            p.position(), 0.6F, 0.9F + 0.05F * (GateRules.COUNTDOWN_SECONDS - secondsLeft));
                }
                if (elapsed % 20 == 0) {
                    p.sendSystemMessage(Component.literal("\u00A75Departing in \u00A7e" + GateRules.secondsLeft(elapsed)
                            + (d.members.size() > 1 ? " \u00A77(" + d.members.size() + " at the Rift)" : "")), true);
                    p.addEffect(new MobEffectInstance(MobEffects.DARKNESS, 50, 0, false, false));
                }
            }
            if (bellDue) {
                d.lastBell = secondsLeft;
            }
            d.rift.waiting = d.members.size();
            if (d.members.isEmpty()) {
                DEPARTURES.remove(d.rift);
                continue;
            }
            if (GateRules.groupDeparts(elapsed)) {
                DEPARTURES.remove(d.rift);
                d.rift.waiting = 0;
                String error = RunCommand.tryStartParty(going);
                if (error != null) {
                    for (RunCommand.PartyMember m : going) {
                        m.player().sendSystemMessage(Component.literal("\u00A7c" + error));
                    }
                }
            }
        }
    }
}
