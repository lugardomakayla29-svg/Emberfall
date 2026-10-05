package com.solme.emberfall.item;

import com.solme.emberfall.network.ChooseWeaponPayload;
import com.solme.emberfall.network.OpenWeaponChoicePayload;
import com.solme.emberfall.progression.WeaponUnlocks;
import com.solme.emberfall.wave.WaveDirector;
import com.solme.emberfall.world.RunManager;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Orchestrates the weapon-pick flow (design doc: "distinct weapon types
 * ... pick a starting weapon before a run / find mid-run / buy in the
 * shop"):
 *   - Mid-run swap offer: opened by {@link com.solme.emberfall.leveling.LevelingHandler}
 *     on milestone levels, offering unlocked weapons other than the one
 *     currently equipped. Pauses the Wave Director for the grace window,
 *     same reasoning as {@link com.solme.emberfall.tome.TomeChoiceManager}.
 * Grace-window auto-resolve (first offer wins) so an AFK/headless client
 * can never stall a run.
 *
 * A free "pick any unlocked weapon to start a run" screen used to also live
 * here, opened once per run by {@link RunManager#joinPlayer} - superseded
 * when the Character system (design doc Section 5) took over fixing each
 * run's starting weapon from the selected Character instead (see {@link
 * RunManager#joinPlayer}'s own comment on that switch). The {@code
 * isStartingPick} plumbing below (offer payload, resolve() branch) is kept
 * rather than ripped out purely for the mid-run-vs-starting distinction it
 * already threads through {@link com.solme.emberfall.client.WeaponChoiceScreen}'s
 * title text - it's just permanently false in practice today, not dead
 * code that does nothing.
 */
public final class WeaponChoiceManager {
    private static final long GRACE_WINDOW_MS = 8000;

    private static final Map<UUID, PendingChoice> pending = new HashMap<>();

    private WeaponChoiceManager() {}

    /**
     * {@code incoming} is null for a normal offer. When the loadout is full it holds the weapon the player just
     * picked, and {@code offers} then lists the weapons they already carry: choosing one replaces it.
     */
    private record PendingChoice(boolean isStartingPick, int requestId, List<WeaponType> offers, int slot,
                                 long deadlineAtMillis, WeaponType incoming) {}

    /** Every weapon id this player can currently pick from (default-unlocked + bought). */
    private static List<WeaponType> unlockedFor(MinecraftServer server, ServerPlayer player) {
        WeaponUnlocks unlocks = WeaponUnlocks.get(server);
        return WeaponPool.ALL.stream()
                .filter(w -> unlocks.isUnlocked(player.getUUID(), w.id()))
                .collect(Collectors.toList());
    }

    public static void openMidRunChoice(ServerPlayer player, int level) {
        MinecraftServer server = player.level().getServer();
        if (server == null) {
            return;
        }
        // SLOTS FULL = NO OFFER. The old screen let a one-weapon player click the offer and silently swap his only weapon
        // (a Ranger ended up with a Broadsword). With no free slot there is nothing to add, so nothing is shown at all.
        if (!PlayerWeapon.hasFreeWeaponSlot(player)) {
            return;
        }
        List<WeaponType> offers = unlockedFor(server, player).stream()
                .filter(w -> !PlayerWeapon.isRunning(player, w.id()))
                .collect(Collectors.toList());
        if (offers.isEmpty()) {
            player.sendSystemMessage(Component.literal("§7(No other weapons unlocked yet - visit the shop to unlock more.)"));
            return;
        }

        Integer slot = RunManager.slotOf(player);
        int slotValue = slot == null ? -1 : slot;
        if (slot != null) {
            WaveDirector director = WaveDirector.get(slot);
            if (director != null) {
                director.setPaused(true);
            }
        }

        pending.put(player.getUUID(), new PendingChoice(false, level, offers, slotValue, System.currentTimeMillis() + GRACE_WINDOW_MS, null));
        send(player, false, level, offers);
    }

    private static void send(ServerPlayer player, boolean isStartingPick, int requestId, List<WeaponType> offers) {
        List<OpenWeaponChoicePayload.OfferInfo> offerInfos = offers.stream()
                .map(w -> new OpenWeaponChoicePayload.OfferInfo(w.id(), w.displayName(), w.description(), w.moveset().name()))
                .collect(Collectors.toList());
        ServerPlayNetworking.send(player, new OpenWeaponChoicePayload(isStartingPick, requestId, offerInfos));
    }

    public static void onChoiceReceived(ServerPlayer player, ChooseWeaponPayload payload) {
        PendingChoice choice = pending.get(player.getUUID());
        if (choice == null || choice.isStartingPick() != payload.isStartingPick() || choice.requestId() != payload.requestId()) {
            return; // stale or duplicate packet
        }
        if (payload.weaponId().isEmpty()) {
            if (!choice.isStartingPick()) {
                decline(player, choice); // Skip: keep every weapon, take nothing
            }
            return; // the starting pick is mandatory: a run cannot begin unarmed
        }
        WeaponType picked = choice.offers().stream().filter(w -> w.id().equals(payload.weaponId())).findFirst().orElse(null);
        if (picked == null) {
            return; // client claimed a weapon id that wasn't actually offered - ignore
        }
        resolve(player, choice, picked);
    }

    /** Auto-resolves any pending choice whose grace window has expired. Call once per server tick. */
    public static void tickAll(MinecraftServer server) {
        if (pending.isEmpty()) {
            return;
        }
        long now = System.currentTimeMillis();
        List<UUID> expired = new ArrayList<>();
        for (Map.Entry<UUID, PendingChoice> entry : pending.entrySet()) {
            if (now >= entry.getValue().deadlineAtMillis()) {
                expired.add(entry.getKey());
            }
        }
        for (UUID id : expired) {
            ServerPlayer player = server.getPlayerList().getPlayer(id);
            PendingChoice choice = pending.get(id);
            if (player != null && choice != null) {
                if (choice.incoming() != null || !choice.isStartingPick()) {
                    decline(player, choice); // never swap or delete a weapon the player did not choose to change
                } else {
                    resolve(player, choice, choice.offers().get(0)); // starting pick: a run needs a weapon
                }
            } else {
                pending.remove(id);
            }
        }
    }

    /** Debug/test view of the open weapon screen: "id,id|incoming=<id or none>|req=<n>", or "none". */
    public static String describePending(ServerPlayer player) {
        PendingChoice choice = pending.get(player.getUUID());
        if (choice == null) {
            return "none";
        }
        return choice.offers().stream().map(WeaponType::id).collect(Collectors.joining(","))
                + "|incoming=" + (choice.incoming() == null ? "none" : choice.incoming().id())
                + "|req=" + choice.requestId();
    }

    /** Debug/test: answer the open screen exactly as the client would (empty id = Skip). */
    public static void answerPending(ServerPlayer player, String weaponId) {
        PendingChoice choice = pending.get(player.getUUID());
        if (choice != null) {
            onChoiceReceived(player, new ChooseWeaponPayload(choice.isStartingPick(), choice.requestId(), weaponId));
        }
    }

    /** True if this player currently has an unresolved Weapon Choice screen open (server-side view). */
    public static boolean hasPending(ServerPlayer player) {
        return pending.containsKey(player.getUUID());
    }

    /** Drops any pending choice for this player without applying it - used when they leave a run. */
    public static void cancelPending(ServerPlayer player) {
        PendingChoice choice = pending.remove(player.getUUID());
        if (choice != null && !choice.isStartingPick() && choice.slot() != -1) {
            WaveDirector director = WaveDirector.get(choice.slot());
            if (director != null) {
                director.setPaused(false);
            }
        }
    }

    private static void resolve(ServerPlayer player, PendingChoice choice, WeaponType picked) {
        if (choice.incoming() != null) {
            // Replace stage: `picked` is the weapon the player chose to give up.
            pending.remove(player.getUUID());
            int index = Loadout.of(player).indexOf(picked.id());
            if (index >= 0) {
                PlayerWeapon.equipInto(player, choice.incoming(), index);
                player.sendSystemMessage(Component.literal("§a+ " + choice.incoming().displayName()
                        + " §7replaces " + picked.displayName()));
            }
            resumeWaves(choice);
            return;
        }
        pending.remove(player.getUUID());
        if (!PlayerWeapon.equip(player, picked)) {
            openReplace(player, choice, picked); // no free slot: ask what to give up
            return;
        }
        if (choice.isStartingPick()) {
            PlayerWeapon.markPicked(player);
        } else if (choice.slot() != -1) {
            WaveDirector director = WaveDirector.get(choice.slot());
            if (director != null) {
                director.setPaused(false);
            }
        }
        player.sendSystemMessage(Component.literal("§a+ Equipped " + picked.displayName() + " §7- " + picked.description()));
    }

    /** Slots are full: ask which carried weapon to give up, reusing the same offer screen. */
    private static void openReplace(ServerPlayer player, PendingChoice from, WeaponType incoming) {
        int level = from.requestId();
        List<WeaponType> carried = Loadout.of(player).weapons();
        pending.put(player.getUUID(), new PendingChoice(false, level, carried, from.slot(),
                System.currentTimeMillis() + GRACE_WINDOW_MS, incoming));
        player.sendSystemMessage(Component.literal("§eYour weapon slots are full. Pick a weapon to replace with "
                + incoming.displayName() + " §7(or wait to keep everything)."));
        send(player, false, level, carried);
    }

    /** Skip or timeout on a mid-run offer or replace step: keep every weapon and let the run carry on. */
    private static void decline(ServerPlayer player, PendingChoice choice) {
        pending.remove(player.getUUID());
        player.sendSystemMessage(Component.literal(choice.incoming() != null
                ? "§7Kept your weapons. " + choice.incoming().displayName() + " was passed over."
                : "§7Kept your weapons."));
        resumeWaves(choice);
    }

    private static void resumeWaves(PendingChoice choice) {
        if (!choice.isStartingPick() && choice.slot() != -1) {
            WaveDirector director = WaveDirector.get(choice.slot());
            if (director != null) {
                director.setPaused(false);
            }
        }
    }
}
