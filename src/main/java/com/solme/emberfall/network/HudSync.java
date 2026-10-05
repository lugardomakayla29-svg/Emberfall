package com.solme.emberfall.network;

import com.solme.emberfall.item.Loadout;
import com.solme.emberfall.item.PlayerWeapon;
import com.solme.emberfall.item.WeaponGrowth;
import com.solme.emberfall.item.WeaponType;
import com.solme.emberfall.tome.PlayerBuild;
import com.solme.emberfall.tome.Tome;
import com.solme.emberfall.tome.TomePool;
import com.solme.emberfall.world.RunManager;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Keeps each in-run player's bottom-left panel up to date. Once a second it builds the panel state,
 * compares it with what that player last received, and sends ONLY on a change, so an idle run costs
 * nothing on the wire. {@link #hide} is the single way the panel is removed (run exit).
 */
public final class HudSync {
    private static final int CHECK_EVERY_TICKS = 20;

    /** Last state each player was sent. Records compare by value, so equals() is the change test. */
    private static final Map<UUID, HudStatePayload> lastSent = new HashMap<>();
    /** Test-only counter of states sent per player; tiny, and cleared with the player's other HUD state. */
    private static final Map<UUID, Integer> sendCounts = new HashMap<>();

    private HudSync() {
    }

    public static void tickAll(MinecraftServer server) {
        if (server.getTickCount() % CHECK_EVERY_TICKS != 0) {
            return;
        }
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            if (RunManager.slotOf(player) == null) {
                continue; // not in a run: hide() already told the client to drop the panel
            }
            push(player, build(player));
        }
    }

    /** Builds the current panel state from the authoritative server data. */
    static HudStatePayload build(ServerPlayer player) {
        List<HudStatePayload.WeaponEntry> weapons = new ArrayList<>();
        Loadout loadout = Loadout.peek(player); // peek never allocates state for a player without one
        if (loadout != null) {
            for (int i = 0; i < loadout.weapons().size(); i++) {
                WeaponType w = loadout.weapons().get(i);
                Loadout.Slot slot = loadout.slot(i);
                int meter = Math.max(0, Math.min(WeaponGrowth.METER_MAX, slot.meter()));
                // Quantised: the bar moves in 5% steps, so the payload (and the wire) changes only when it visibly does.
                int step = meter >= WeaponGrowth.METER_MAX ? HudStatePayload.METER_STEPS
                        : meter * HudStatePayload.METER_STEPS / WeaponGrowth.METER_MAX;
                weapons.add(new HudStatePayload.WeaponEntry(w.id(), w.displayName(), slot.level(), step));
            }
        }
        List<HudStatePayload.TomeEntry> tomes = new ArrayList<>();
        for (Map.Entry<String, Integer> e : PlayerBuild.allOf(player).entrySet()) {
            Tome tome = TomePool.byId(e.getKey());
            if (tome != null) {
                tomes.add(new HudStatePayload.TomeEntry(tome.id(), tome.displayName(), e.getValue(), tome.maxStacks()));
            }
        }
        // Stable order so an unchanged build never looks "changed" because a map iterated differently.
        tomes.sort((a, b) -> a.id().compareTo(b.id()));
        return new HudStatePayload(PlayerWeapon.allowedWeaponSlots(player), PlayerBuild.allowedTomeSlots(player),
                weapons, tomes);
    }

    private static void push(ServerPlayer player, HudStatePayload state) {
        if (state.equals(lastSent.get(player.getUUID()))) {
            return;
        }
        lastSent.put(player.getUUID(), state);
        sendCounts.merge(player.getUUID(), 1, Integer::sum);
        ServerPlayNetworking.send(player, state);
    }

    /** Removes the panel: called when the player leaves a run. Safe to call for a disconnected player. */
    public static void hide(ServerPlayer player) {
        sendCounts.remove(player.getUUID());
        if (lastSent.remove(player.getUUID()) != null) {
            try {
                ServerPlayNetworking.send(player, HudStatePayload.HIDDEN);
            } catch (RuntimeException ignored) {
                // The connection may already be closing on a disconnect; nothing left to hide.
            }
        }
    }

    /** Debug/test view of what this player was last sent: "wSlots/tSlots|w,w|t:stacks,t:stacks" or "none". */
    public static String describeLast(ServerPlayer player) {
        HudStatePayload s = lastSent.get(player.getUUID());
        if (s == null) {
            return "none";
        }
        StringBuilder sb = new StringBuilder().append(s.weaponSlots()).append('/').append(s.tomeSlots()).append('|');
        s.weapons().forEach(w -> sb.append(w.id()).append(','));
        sb.append('|');
        s.tomes().forEach(t -> sb.append(t.id()).append(':').append(t.stacks()).append(','));
        // Appended AFTER the original fields so every older check on the leading text still means what it did: "#id@level/meterStep,".
        sb.append('#');
        s.weapons().forEach(w -> sb.append(w.id()).append('@').append(w.level()).append('/').append(w.meterStep()).append(','));
        return sb.toString();
    }

    /** Debug/test: how many states this player has been sent so far (proves unchanged builds send nothing). */
    public static int sendCount(ServerPlayer player) {
        return sendCounts.getOrDefault(player.getUUID(), 0);
    }
}
