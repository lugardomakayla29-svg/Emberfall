package com.solme.emberfall.leveling;

import com.solme.emberfall.EmberfallMod;
import com.solme.emberfall.item.WeaponChoiceManager;
import com.solme.emberfall.tome.TomeChoiceManager;
import com.solme.emberfall.world.RunManager;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Design doc 4.1 step 5 / 10.1: in-run XP reuses vanilla's own
 * ExperienceOrbEntity + Player.experienceLevel wholesale rather than a
 * parallel currency - HordeZombie already drops XP orbs for free (it's a
 * bare vanilla Zombie subclass, and hostiles drop XP on death by default
 * as long as the kill is player-attributed, which AutoAttackSystem's use
 * of vanilla's own player.attack() already guarantees), and picking one up
 * already runs the level-up math through vanilla's own
 * giveExperiencePoints(). What's genuinely custom here is:
 *   1. In-run XP must NOT persist (10.1) - snapshot + zero the player's
 *      real vanilla XP on entering a run, restore it on leaving.
 *   2. Detecting the level-up moment itself (nothing fires an event for
 *      this in vanilla - Player.experienceLevel is a bare field mutated by
 *      giveExperiencePoints with no callback) to react to it.
 * The actual level-up reward is handed off to either
 * {@link TomeChoiceManager} (normal levels) or {@link WeaponChoiceManager}
 * (every WEAPON_OFFER_INTERVAL-th level, design doc: weapons can also be
 * "found/earned mid-run as drops or level-up offers") - this class's job
 * ends at "a level-up happened, for this player, to this level."
 *
 * Multi-level jumps in one tick (confirmed reachable and live-reproduced:
 * an AoE kill absorbing several XP orbs in the same tick easily crosses 2+
 * level thresholds before this class's once-per-tick poll ever runs) must
 * NOT be collapsed into a single reward. Both {@link TomeChoiceManager} and
 * {@link WeaponChoiceManager} hold only one pending choice per player -
 * calling either of them again for a higher level while an earlier one is
 * still unresolved silently overwrites and permanently drops that earlier
 * choice (reproduced live: a 3-level jump in one command granted exactly 1
 * Tome instead of 3). So level-ups are queued per player and drained one at
 * a time, only advancing once the previous choice has actually resolved
 * (checked via {@code hasPending} on both managers) - never more than one
 * open choice per player at once, and none silently skipped.
 */
public final class LevelingHandler {
    private static final int WEAPON_OFFER_INTERVAL = 5;

    private static final Map<UUID, int[]> preRunSnapshot = new HashMap<>(); // [level, totalExperience]
    private static final Map<UUID, Integer> lastKnownLevel = new HashMap<>();
    private static final Map<UUID, Deque<Integer>> queuedLevelUps = new HashMap<>();

    private LevelingHandler() {}

    /** Snapshots the player's real XP, then zeroes it for a clean in-run start. */
    public static void enterRun(ServerPlayer player) {
        UUID id = player.getUUID();
        preRunSnapshot.put(id, new int[]{player.experienceLevel, player.totalExperience});
        player.totalExperience = 0;
        player.setExperienceLevels(0);
        player.setExperiencePoints(0);
        lastKnownLevel.put(id, 0);
        queuedLevelUps.remove(id); // defensive - a fresh run must never inherit a leftover queue
    }

    /** Restores the player's pre-run XP snapshot and stops tracking their level. */
    public static void exitRun(ServerPlayer player) {
        UUID id = player.getUUID();
        lastKnownLevel.remove(id);
        queuedLevelUps.remove(id); // any not-yet-offered level-ups from this run are void, not carried over
        int[] saved = preRunSnapshot.remove(id);
        if (saved != null) {
            player.totalExperience = saved[1];
            player.setExperienceLevels(saved[0]);
            player.setExperiencePoints(0);
        }
    }

    /** Polls every currently-tracked in-run player for a level increase, and drains one queued reward per player per tick at most. Call once per server tick. */
    public static void tickAll(MinecraftServer server) {
        if (lastKnownLevel.isEmpty()) {
            return;
        }
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            UUID id = player.getUUID();
            Integer last = lastKnownLevel.get(id);
            if (last == null || RunManager.slotOf(player) == null) {
                continue; // not an in-run player we're tracking
            }
            int current = player.experienceLevel;
            if (current > last) {
                Deque<Integer> queue = queuedLevelUps.computeIfAbsent(id, k -> new ArrayDeque<>());
                for (int lvl = last + 1; lvl <= current; lvl++) {
                    queue.add(lvl); // every level earns its Tome pick
                    if (lvl % WEAPON_OFFER_INTERVAL == 0) {
                        queue.add(-lvl); // a milestone ALSO earns a weapon offer (negative = weapon entry)
                    }
                }
                lastKnownLevel.put(id, current);
                // "Reach level 15" is a high-water mark across runs, so it raises rather than adds.
                com.solme.emberfall.relic.RelicUnlocks.announce(player,
                        com.solme.emberfall.relic.RelicUnlocks.get(player.level().getServer()).raiseProgress(id, "reach_level_15", current));
            }
            advanceQueue(player);
        }
    }

    /** Offers the next queued level-up's reward, but only once the previous choice (if any) has actually resolved. */
    private static void advanceQueue(ServerPlayer player) {
        Deque<Integer> queue = queuedLevelUps.get(player.getUUID());
        if (queue == null || queue.isEmpty()) {
            return;
        }
        if (TomeChoiceManager.hasPending(player) || WeaponChoiceManager.hasPending(player)) {
            return; // still waiting on an earlier level's choice to resolve - don't overwrite it
        }
        onLevelUp(player, queue.poll());
    }

    private static void onLevelUp(ServerPlayer player, int newLevel) {
        if (newLevel < 0) {
            // The weapon half of a milestone level. It is a bonus, so it never replaces the Tome pick.
            WeaponChoiceManager.openMidRunChoice(player, -newLevel);
            return;
        }
        EmberfallMod.LOGGER.info("{} reached level {}", player.getGameProfile().name(), newLevel);
        TomeChoiceManager.openChoice(player, newLevel);
    }
}
