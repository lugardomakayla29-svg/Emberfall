package com.solme.emberfall.rift;

import com.solme.emberfall.command.RunCommand;
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

    /** A party that finished the countdown and is choosing characters. Keyed by the phase id the client echoes if it closes the screen. */
    private static final class Selecting {
        final int id;
        RiftManager.Rift rift;
        final RiftSelect.Phase phase;
        /** Where each member stood when they clicked (their return spot), kept so the run starts exactly as the old direct start did. */
        final Map<UUID, Member> anchors = new java.util.LinkedHashMap<>();
        int lastShown = -1;

        Selecting(int id, long now, List<Member> members) {
            this.id = id;
            java.util.Set<UUID> ids = new java.util.LinkedHashSet<>();
            for (Member m : members) {
                ids.add(m.id());
                anchors.put(m.id(), m);
            }
            this.phase = new RiftSelect.Phase(now, ids);
        }
    }

    private static final Map<Integer, Selecting> SELECTIONS = new HashMap<>();
    /** Phase ids start at 1: 0 is {@code OpenCharacterSelectPayload.NO_PHASE}. */
    private static int nextSelectId = 1;
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
        SELECTIONS.clear();
        DEPARTURES.clear();
        ENDED.clear();
    }

    private static void drop(UUID id) {
        for (Selecting sel : SELECTIONS.values()) {
            sel.phase.drop(id);
        }
        for (Departure d : new ArrayList<>(DEPARTURES.values())) {
            d.members.removeIf(m -> m.id().equals(id));
            if (d.members.isEmpty()) {
                DEPARTURES.remove(d.rift);
            }
        }
    }

    /** True while this player is waiting in a countdown. */
    public static boolean counting(UUID id) {
        if (selecting(id)) {
            return true;
        }
        for (Departure d : DEPARTURES.values()) {
            if (d.has(id)) {
                return true;
            }
        }
        return false;
    }

    /** True while this player's party is choosing characters. */
    public static boolean selecting(UUID id) {
        for (Selecting sel : SELECTIONS.values()) {
            if (sel.phase.party().contains(id)) {
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
                beginSelection(d.rift, now, going);
            }
        }
    }

    /**
     * The countdown ended: the party now chooses characters, THEN the run starts (owner decision 2026-10-08). Each member gets the screen with
     * this phase's id; {@code rift.waiting} stays at the party size so an idle Rift is not closed under them.
     */
    private static void beginSelection(RiftManager.Rift rift, long now, List<RunCommand.PartyMember> going) {
        List<Member> members = new ArrayList<>();
        for (RunCommand.PartyMember m : going) {
            members.add(new Member(m.player().getUUID(), m.backX(), m.backY(), m.backZ()));
        }
        Selecting sel = new Selecting(nextSelectId++, now, members);
        sel.rift = rift;
        SELECTIONS.put(sel.id, sel);
        rift.waiting = members.size();
        for (RunCommand.PartyMember m : going) {
            com.solme.emberfall.character.CharacterSelectManager.open(m.player(), sel.id, RiftSelect.secondsLeft(0));
        }
    }

    /** Test reader: "SELECT phases=N [id=I party=P pending=Q waiting=W]...". The live test reads the server's own counters, not chat. */
    public static String selectState() {
        StringBuilder sb = new StringBuilder("SELECT phases=" + SELECTIONS.size());
        for (Selecting sel : SELECTIONS.values()) {
            sb.append(" [id=").append(sel.id).append(" party=").append(sel.phase.party().size())
                    .append(" pending=").append(sel.phase.pendingCount()).append(" waiting=").append(sel.rift.waiting).append(']');
        }
        return sb.toString();
    }

    /** A Rift party member picked a character (accepted or refused): they have answered. A pick from anywhere else is a no-op. */
    public static void onSelectAnswered(ServerPlayer player) {
        for (Selecting sel : SELECTIONS.values()) {
            if (sel.phase.resolve(player.getUUID())) {
                return;
            }
        }
    }

    /** A Rift party member closed the screen without picking. Honoured only for the phase id the server issued to THIS player. */
    public static void onSelectClosed(ServerPlayer player, int selectId) {
        Selecting sel = SELECTIONS.get(selectId);
        if (sel != null) {
            sel.phase.resolve(player.getUUID());
        }
    }

    /** Every tick: drop members who left or died, remind the party of the time, and start each run whose phase is done. */
    public static void tickSelections(MinecraftServer server) {
        if (SELECTIONS.isEmpty()) {
            return;
        }
        long now = server.getTickCount();
        for (Selecting sel : new ArrayList<>(SELECTIONS.values())) {
            for (UUID id : new ArrayList<>(sel.phase.party())) {
                ServerPlayer p = server.getPlayerList().getPlayer(id);
                if (p == null || !p.isAlive()) {
                    sel.phase.drop(id);
                }
            }
            if (sel.phase.empty()) {
                SELECTIONS.remove(sel.id);
                sel.rift.waiting = 0;
                continue;
            }
            sel.rift.waiting = sel.phase.party().size();
            long elapsed = now - sel.phase.startedTick();
            int left = RiftSelect.secondsLeft(elapsed);
            if (!sel.phase.done(now)) {
                if (left != sel.lastShown && elapsed % 20 == 0) {
                    sel.lastShown = left;
                    for (UUID id : sel.phase.party()) {
                        ServerPlayer p = server.getPlayerList().getPlayer(id);
                        if (p != null && sel.phase.isPending(id)) {
                            p.sendSystemMessage(Component.literal("\u00A75Choose your character \u00A77(" + left + " s)"), true);
                        }
                    }
                }
                continue;
            }
            SELECTIONS.remove(sel.id);
            sel.rift.waiting = 0;
            List<RunCommand.PartyMember> going = new ArrayList<>();
            for (UUID id : sel.phase.party()) {
                ServerPlayer p = server.getPlayerList().getPlayer(id);
                Member a = sel.anchors.get(id);
                if (p != null && a != null) {
                    going.add(new RunCommand.PartyMember(p, a.x(), a.y(), a.z()));
                }
            }
            String error = RunCommand.tryStartParty(going);
            if (error != null) {
                for (RunCommand.PartyMember m : going) {
                    m.player().sendSystemMessage(Component.literal("\u00A7c" + error));
                }
            }
        }
    }
}
