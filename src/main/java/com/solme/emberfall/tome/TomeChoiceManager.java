package com.solme.emberfall.tome;

import com.solme.emberfall.network.BanishTomePayload;
import com.solme.emberfall.network.ChooseTomePayload;
import com.solme.emberfall.network.OpenTomeChoicePayload;
import com.solme.emberfall.network.RerollTomePayload;
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
 * Design doc 7.4 / 11: orchestrates the level-up -> 3-offer Tome Choice ->
 * apply-and-resume flow. Pauses that run's Wave Director for the duration
 * (4.1 step 6: "time does not pause the wave director for long - a very
 * brief grace window at most"), and force-resolves with the first offer if
 * the player doesn't respond within that window, so a stuck/AFK client
 * can't halt a run's danger forever.
 *
 * Reroll/Banish (see {@link PlayerTomeCharges}) and Skip all resend the
 * SAME screen type in place rather than opening a separate flow: a reroll
 * or banish replaces {@code offers} and resets the deadline (spending a
 * charge deliberately buys the player a fresh full grace window, not a
 * shrinking one), while Skip resolves immediately with no Tome granted -
 * see {@link #resolve}.
 */
public final class TomeChoiceManager {
    private static final long GRACE_WINDOW_MS = 8000; // "very brief grace window at most" per 4.1 step 6

    private static final Map<UUID, PendingChoice> pending = new HashMap<>();

    private TomeChoiceManager() {}

    private record PendingChoice(int level, List<Tome> offers, int slot, long deadlineAtMillis) {}

    public static void openChoice(ServerPlayer player, int newLevel) {
        List<Tome> offers = TomeOfferGenerator.generateOffers(player);
        if (offers.isEmpty()) {
            boolean slotsFull = PlayerBuild.distinctOwned(player) >= PlayerBuild.allowedTomeSlots(player);
            player.sendSystemMessage(Component.literal(slotsFull
                    ? "§7(Your tome slots are full and every Tome you hold is maxed. Buy more slots in the shop.)"
                    : "§7(Your build is maxed out - no Tome offers available.)"));
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

        pending.put(player.getUUID(), new PendingChoice(newLevel, offers, slotValue, System.currentTimeMillis() + GRACE_WINDOW_MS));
        sendOffer(player, newLevel, offers);
    }

    public static void onChoiceReceived(ServerPlayer player, ChooseTomePayload payload) {
        PendingChoice choice = pending.get(player.getUUID());
        if (choice == null || choice.level() != payload.forLevel()) {
            return; // stale or duplicate packet - ignore rather than trust a mismatched client claim
        }
        if (payload.choiceIndex() == -1) {
            resolve(player, choice, null); // Skip - free, grants nothing
            return;
        }
        if (payload.choiceIndex() < 0 || payload.choiceIndex() >= choice.offers().size()) {
            return;
        }
        resolve(player, choice, choice.offers().get(payload.choiceIndex()));
    }

    /** Spends a reroll charge (if any remain) and replaces the current offer set in place. */
    public static void onRerollReceived(ServerPlayer player, RerollTomePayload payload) {
        PendingChoice choice = pending.get(player.getUUID());
        if (choice == null || choice.level() != payload.forLevel()) {
            return;
        }
        // A free charge (bought with Silver) is always spent first; only without one does a reroll cost gold.
        boolean useCharge = PlayerTomeCharges.hasFreeReroll(player);
        int goldPrice = useCharge ? 0 : PlayerTomeCharges.nextGoldRerollPrice(player);
        if (!useCharge && com.solme.emberfall.pickup.PickupSystem.gold(player) < goldPrice) {
            player.sendSystemMessage(Component.literal("§7(A reroll costs " + goldPrice + " gold; you have "
                    + com.solme.emberfall.pickup.PickupSystem.gold(player) + ".)"));
            return;
        }
        List<Tome> freshOffers = TomeOfferGenerator.generateOffers(player);
        if (freshOffers.isEmpty()) {
            // Pool exhausted: nothing new to show, so nothing is charged. Keep the current offers on screen.
            player.sendSystemMessage(Component.literal("§7(No other Tomes to roll into, so that reroll was free.)"));
            return;
        }
        // Charge only now that a genuinely new set exists, so a dead reroll never costs anything.
        if (useCharge) {
            PlayerTomeCharges.useReroll(player);
        } else if (com.solme.emberfall.pickup.PickupSystem.spendGold(player, goldPrice)) {
            PlayerTomeCharges.recordGoldReroll(player);
        } else {
            return; // gold changed between the check and the spend (e.g. spent elsewhere): do nothing
        }
        pending.put(player.getUUID(),
                new PendingChoice(choice.level(), freshOffers, choice.slot(), System.currentTimeMillis() + GRACE_WINDOW_MS));
        sendOffer(player, choice.level(), freshOffers);
    }

    /** Spends a banish charge (if any remain) to permanently remove one offered Tome for the rest of this run, then refreshes the offer set. */
    public static void onBanishReceived(ServerPlayer player, BanishTomePayload payload) {
        PendingChoice choice = pending.get(player.getUUID());
        if (choice == null || choice.level() != payload.forLevel()) {
            return;
        }
        boolean isCurrentOffer = choice.offers().stream().anyMatch(t -> t.id().equals(payload.tomeId()));
        if (!isCurrentOffer) {
            return; // ignore a banish request for a Tome that isn't even currently offered
        }
        if (!PlayerTomeCharges.useBanish(player, payload.tomeId())) {
            player.sendSystemMessage(Component.literal("§7(No Tome banishes remaining this run.)"));
            return;
        }
        String banishedName = choice.offers().stream()
                .filter(t -> t.id().equals(payload.tomeId()))
                .findFirst()
                .map(Tome::displayName)
                .orElse(payload.tomeId());
        List<Tome> freshOffers = TomeOfferGenerator.generateOffers(player);
        if (freshOffers.isEmpty()) {
            freshOffers = choice.offers().stream().filter(t -> !t.id().equals(payload.tomeId())).collect(Collectors.toList());
        }
        pending.put(player.getUUID(),
                new PendingChoice(choice.level(), freshOffers, choice.slot(), System.currentTimeMillis() + GRACE_WINDOW_MS));
        player.sendSystemMessage(Component.literal("§c- Banished " + banishedName + " for the rest of this run."));
        sendOffer(player, choice.level(), freshOffers);
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
                resolve(player, choice, choice.offers().get(0));
            } else {
                pending.remove(id); // player disconnected mid-choice - drop it, nothing to apply to
            }
        }
    }

    /** True if this player currently has an unresolved Tome Choice screen open (server-side view). */
    public static boolean hasPending(ServerPlayer player) {
        return pending.containsKey(player.getUUID());
    }

    /** Debug/test-only: the currently-offered Tome ids for this player's pending choice, or an empty list if none. */
    public static List<String> currentOfferIds(ServerPlayer player) {
        PendingChoice choice = pending.get(player.getUUID());
        if (choice == null) {
            return List.of();
        }
        return choice.offers().stream().map(Tome::id).collect(Collectors.toList());
    }

    /** Drops any pending choice for this player without applying it - used when they leave a run. */
    public static void cancelPending(ServerPlayer player) {
        PendingChoice choice = pending.remove(player.getUUID());
        if (choice != null && choice.slot() != -1) {
            WaveDirector director = WaveDirector.get(choice.slot());
            if (director != null) {
                director.setPaused(false);
            }
        }
    }

    /** @param picked null means Skip - resolve with no Tome granted. */
    private static void resolve(ServerPlayer player, PendingChoice choice, Tome picked) {
        pending.remove(player.getUUID());

        if (picked != null && !PlayerBuild.canTake(player, picked.id())) {
            // The offer was valid when it was made, but the player's slots changed before they chose.
            player.sendSystemMessage(Component.literal("§cNo free tome slot for " + picked.displayName() + "."));
            picked = null;
        }
        if (picked != null) {
            int stackIndex = PlayerBuild.grant(player, picked.id());
            picked.onApply().apply(player, stackIndex);
            SynergyEffects.checkAndApply(player);
            CombatStats.recompute(player);
        }

        if (choice.slot() != -1) {
            WaveDirector director = WaveDirector.get(choice.slot());
            if (director != null) {
                director.setPaused(false);
            }
        }

        if (picked != null) {
            player.sendSystemMessage(Component.literal("§a+ " + picked.displayName() + " §7- " + picked.description()));
            com.solme.emberfall.pickup.Cue.play((net.minecraft.server.level.ServerLevel) player.level(), "tome_picked",
                    net.minecraft.sounds.SoundEvents.ENCHANTMENT_TABLE_USE, net.minecraft.sounds.SoundSource.PLAYERS,
                    player.position(), 0.8F, 1.2F);
        } else {
            player.sendSystemMessage(Component.literal("§7Skipped this Tome offer."));
        }
    }

    private static void sendOffer(ServerPlayer player, int newLevel, List<Tome> offers) {
        List<OpenTomeChoicePayload.OfferInfo> offerInfos = offers.stream()
                .map(t -> new OpenTomeChoicePayload.OfferInfo(
                        t.id(), t.displayName(), t.description(), t.category().name(), tagsToString(t)))
                .collect(Collectors.toList());
        ServerPlayNetworking.send(player, new OpenTomeChoicePayload(
                newLevel, offerInfos, PlayerTomeCharges.rerollsRemaining(player), PlayerTomeCharges.banishesRemaining(player),
                PlayerTomeCharges.nextGoldRerollPrice(player), com.solme.emberfall.pickup.PickupSystem.gold(player)));
    }

    private static String tagsToString(Tome tome) {
        if (tome.tags().isEmpty()) {
            return "";
        }
        return tome.tags().stream().map(Enum::name).collect(Collectors.joining(", "));
    }
}
