package com.solme.emberfall.item;

import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.server.level.ServerPlayer;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Run-scoped weapon bookkeeping. Which weapon a player is actually using is
 * deliberately NOT tracked here as its own source of truth - it's read
 * straight off {@code player}'s held mainhand item (see
 * {@link com.solme.emberfall.combat.AutoAttackSystem#weaponOf}), exactly
 * the same "trust vanilla state, don't duplicate it" approach the rest of
 * the combat system already uses for attack-speed cooldown. What genuinely
 * needs run-scoped state is:
 *   - whether this run's starting-weapon pick has already happened (so
 *     {@link com.solme.emberfall.world.RunManager#joinPlayer} doesn't
 *     reopen the picker on, say, a reconnect-rescue teleport);
 *   - the MELEE_DUAL/RANGED_AOE moveset's "every 4th hit/shot" streak
 *     counter, which is pure combat-cadence state, not a Tome/build stat.
 */
public final class PlayerWeapon {
    private static final Map<UUID, Boolean> pickedThisRun = new ConcurrentHashMap<>();
    private static final Map<UUID, Integer> streak = new ConcurrentHashMap<>();
    /** Steady Hand's 3rd-copy "re-arm on kill" flag - deliberately separate from {@code
     *  streak} rather than reusing it to fake a "streak already at 4": a genuine drought
     *  (no target in range) legitimately resets the hit/shot streak every such tick (see
     *  {@link com.solme.emberfall.combat.AutoAttackSystem#tickPlayer}'s no-target branch),
     *  and the near-universal gap between landing the killing blow and a new target
     *  actually being in range would wipe a re-arm value stored in that same counter
     *  before it could ever be spent - confirmed live: the streak read 1, not 3, on the
     *  very next target after a re-arming kill, because 1-2 target-less ticks reset it to
     *  0 first. This flag is untouched by that reset, so it survives the gap. */
    private static final Map<UUID, Boolean> steadyHandArmed = new ConcurrentHashMap<>();
    /** Grave Anchor's 3rd-copy "kill snaps the pile down harder" payoff: which mobs (by
     *  UUID, resolved back through {@code ServerLevel#getEntity} rather than held as
     *  direct references - a piled mob can die/despawn independently and a raw Mob
     *  reference would then be stale) were pulled into the player's most recent Gather,
     *  including the anchor target itself, plus when that record stops counting as "still
     *  piled" - same survives-a-gap reasoning as {@code steadyHandArmed}, but here the
     *  window is a real gameplay duration (the root's own length) rather than "until
     *  consumed", so it's a game-time expiry instead of a one-shot flag. */
    private static final Map<UUID, PileRecord> graveAnchorPile = new ConcurrentHashMap<>();

    record PileRecord(java.util.List<UUID> members, long expiryGameTime) {}

    private PlayerWeapon() {}

    public static void reset(ServerPlayer player) {
        Loadout.discard(player); // a fresh run always starts with an empty loadout
        pickedThisRun.put(player.getUUID(), Boolean.FALSE);
        streak.put(player.getUUID(), 0);
        steadyHandArmed.remove(player.getUUID());
        graveAnchorPile.remove(player.getUUID());
    }

    public static void clear(ServerPlayer player) {
        Loadout.discard(player);
        pickedThisRun.remove(player.getUUID());
        streak.remove(player.getUUID());
        steadyHandArmed.remove(player.getUUID());
        graveAnchorPile.remove(player.getUUID());
    }

    /** Records this player's just-triggered Gather pile (anchor target + everyone pulled
     *  in) as "currently piled" until {@code expiryGameTime} - Grave Anchor tier 3 reads
     *  this to know whether a kill landed on a mob that's part of an active pile. */
    public static void recordGraveAnchorPile(ServerPlayer player, java.util.List<UUID> members, long expiryGameTime) {
        PileRecord record = new PileRecord(java.util.List.copyOf(members), expiryGameTime);
        Loadout.Slot slot = activeSlot(player);
        if (slot != null) {
            slot.pile = record;
        } else {
            graveAnchorPile.put(player.getUUID(), record);
        }
    }

    /** Returns the still-active pile's member UUIDs, or an empty list if there is none or
     *  it has expired (root duration ran out - the pile has scattered by now). */
    public static java.util.List<UUID> getGraveAnchorPile(ServerPlayer player, long nowGameTime) {
        Loadout.Slot slot = activeSlot(player);
        PileRecord rec = slot != null ? slot.pile : graveAnchorPile.get(player.getUUID());
        if (rec == null || nowGameTime > rec.expiryGameTime()) {
            return java.util.List.of();
        }
        return rec.members();
    }

    /** Arms Steady Hand's 3rd-copy re-arm payoff - the player's next landed Broadsword hit
     *  will be forced Empowered regardless of streak, surviving any target-less gap. */
    public static void armSteadyHand(ServerPlayer player) {
        Loadout.Slot slot = activeSlot(player);
        if (slot != null) {
            slot.steadyHandArmed = true;
        } else {
            steadyHandArmed.put(player.getUUID(), Boolean.TRUE);
        }
    }

    /** Consumes the re-arm flag if set, returning whether it was armed. One-shot: calling
     *  this clears it, so a second call before the next arm returns false. */
    public static boolean consumeSteadyHandArmed(ServerPlayer player) {
        Loadout.Slot slot = activeSlot(player);
        if (slot != null) {
            boolean was = slot.steadyHandArmed;
            slot.steadyHandArmed = false;
            return was;
        }
        return steadyHandArmed.remove(player.getUUID()) != null;
    }

    /** Debug/test-only, non-consuming: reads whether the flag is set without clearing it -
     *  see EmberfallCommands#debugStreak's sibling debug command. */
    public static boolean peekSteadyHandArmed(ServerPlayer player) {
        Loadout.Slot slot = activeSlot(player);
        return slot != null ? slot.steadyHandArmed : steadyHandArmed.containsKey(player.getUUID());
    }

    public static boolean hasPickedThisRun(ServerPlayer player) {
        return pickedThisRun.getOrDefault(player.getUUID(), false);
    }

    public static void markPicked(ServerPlayer player) {
        pickedThisRun.put(player.getUUID(), Boolean.TRUE);
    }

    /** Increments this player's hit/shot streak and returns the new count. */
    public static int incrementStreak(ServerPlayer player) {
        Loadout.Slot slot = activeSlot(player);
        if (slot != null) {
            return ++slot.streak;
        }
        return streak.merge(player.getUUID(), 1, Integer::sum);
    }

    /** Debug/test-only introspection: reads the current streak without mutating it - see
     *  EmberfallCommands#debugStreak, same "raw debug override" spirit as granttome/selectweapon. */
    public static int peekStreak(ServerPlayer player) {
        Loadout.Slot slot = activeSlot(player);
        return slot != null ? slot.streak : streak.getOrDefault(player.getUUID(), 0);
    }

    /** Resets the streak to 0 - called on a miss/no-target tick so a drought breaks the combo. */
    public static void resetStreak(ServerPlayer player) {
        Loadout.Slot slot = activeSlot(player);
        if (slot != null) {
            slot.streak = 0;
        } else {
            streak.put(player.getUUID(), 0);
        }
    }

    /** The slot currently being dispatched, or null before the player has any weapon. */
    private static Loadout.Slot activeSlot(ServerPlayer player) {
        Loadout loadout = Loadout.peek(player);
        return loadout == null ? null : loadout.activeSlot();
    }


    /**
     * Puts a fresh copy of {@code weapon}'s item directly into the
     * player's mainhand, replacing whatever was there. Weapons are unique
     * (stacksTo(1)) and not meant to be a real inventory-managed resource
     * (design doc: picked from a screen, not looted off the ground), so
     * overwriting the hand slot outright - rather than adding to the
     * inventory and asking the player to manually switch - is the correct
     * "this is now your weapon" semantics, matching how a Tome pick
     * applies instantly with no player action needed.
     */
    /**
     * True when {@code weaponId} is a weapon this player is currently running. Single point of
     * truth for "does this player own weapon X" so weapon-gated Tomes (see
     * {@link com.solme.emberfall.tome.Tome#requiresWeaponId()}) never read the hand directly:
     * once the multi-slot loadout exists this is the only method that has to change.
     */
    public static boolean isRunning(ServerPlayer player, String weaponId) {
        Loadout loadout = Loadout.peek(player);
        if (loadout != null && !loadout.isEmpty()) {
            return loadout.has(weaponId);
        }
        WeaponType held = ModItems.weaponFor(player.getMainHandItem().getItem());
        return held != null && held.id().equals(weaponId);
    }

    /**
     * Gives the player {@code weapon}. It takes the first free slot the player has unlocked. If every unlocked
     * slot is taken (or the weapon is already owned), nothing changes and false is returned, so the caller can
     * open the replace choice instead of silently throwing a weapon away.
     */
    public static boolean equip(ServerPlayer player, WeaponType weapon) {
        Loadout loadout = Loadout.of(player);
        int allowed = allowedWeaponSlots(player);
        if (loadout.add(weapon, allowed) < 0) {
            return false;
        }
        syncHand(player);
        return true;
    }

    /** Replaces the weapon in {@code slotIndex} with {@code weapon}, wiping that slot's combo state. */
    public static void equipInto(ServerPlayer player, WeaponType weapon, int slotIndex) {
        Loadout loadout = Loadout.of(player);
        if (slotIndex < 0 || slotIndex >= loadout.size() || loadout.has(weapon.id())) {
            return;
        }
        loadout.replace(slotIndex, weapon);
        syncHand(player);
    }

    /** True when the player's loadout has room for one more weapon (used to decide whether a level-up weapon offer is worth showing). */
    public static boolean hasFreeWeaponSlot(ServerPlayer player) {
        Loadout loadout = Loadout.peek(player);
        int have = loadout == null ? 0 : loadout.weapons().size();
        return have < Math.min(allowedWeaponSlots(player), Loadout.MAX_SLOTS);
    }

    /** How many weapon slots this player has unlocked (1 to 4). */
    public static int allowedWeaponSlots(ServerPlayer player) {
        var server = player.level().getServer();
        return server == null ? com.solme.emberfall.progression.SlotUnlocks.BASE_SLOTS
                : com.solme.emberfall.progression.SlotUnlocks.get(server)
                        .slots(player.getUUID(), com.solme.emberfall.progression.SlotUnlocks.Kind.WEAPON);
    }

    /**
     * Shows the first slot's weapon in a fixed hotbar slot. The item that stood there is stashed first (see
     * DisplacedItems) and returned when the run ends. The player's selected slot is never changed.
     */
    private static void syncHand(ServerPlayer player) {
        Loadout loadout = Loadout.peek(player);
        if (loadout == null || loadout.isEmpty()) {
            return;
        }
        var inv = player.getInventory();
        int hotbar = com.solme.emberfall.progression.DisplacedItems.WEAPON_HOTBAR_SLOT;
        // Whatever stood in that slot is kept for the end of the run, so starting a run never destroys an item.
        ItemStack current = inv.getItem(hotbar);
        if (ModItems.weaponFor(current.getItem()) == null) {
            var server = player.level().getServer();
            if (server != null) {
                com.solme.emberfall.progression.DisplacedItems.get(server).stash(player.getUUID(), current);
            }
        }
        inv.setItem(hotbar, new ItemStack(ModItems.itemFor(loadout.slot(0).weapon())));
    }
}
