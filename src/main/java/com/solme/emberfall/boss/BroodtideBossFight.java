package com.solme.emberfall.boss;

import com.solme.emberfall.EmberfallMod;
import com.solme.emberfall.entity.BroodtideBody;
import com.solme.emberfall.entity.ModEntities;
import com.solme.emberfall.wave.WaveDirector;
import com.solme.emberfall.world.ArenaInstance;
import com.solme.emberfall.world.RunTelemetry;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;

import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

/**
 * The per-slot orchestrator for the Broodtide (the first boss, replacing the Ember Guardian). It follows {@link GuardianBossFight}'s contract exactly:
 * spawn the boss at the arena's boss_spawn marker, pause the slot's WaveDirector for the fight, and when the body is no longer alive resume normal
 * spawning on EVERY exit path. Only a genuine kill ({@link BroodtideBody#wasDefeated}) marks the boss defeated and escalates the run to tier 2; a body
 * discarded by run teardown does neither. Polling isAlive() once a tick is what makes "however it ends" correct without every caller notifying us.
 */
public final class BroodtideBossFight {
    private static final Map<Integer, BroodtideBossFight> active = new HashMap<>();

    private final int slot;
    private final BroodtideBody brain;

    private BroodtideBossFight(int slot, BroodtideBody brain) {
        this.slot = slot;
        this.brain = brain;
    }

    /** Spawns the Broodtide rig into the given arena and pauses that slot's Wave Director. */
    public static BroodtideBossFight spawn(ServerLevel level, ArenaInstance instance) {
        if (active.containsKey(instance.slot())) {
            return active.get(instance.slot());
        }

        BlockPos pos = pickSpawnPos(instance);
        BroodtideBody brain = new BroodtideBody(ModEntities.BROODTIDE, level);
        brain.initBoss();                                  // full size and full health BEFORE the curse and the party scaling read the max health
        brain.setPos(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5);
        com.solme.emberfall.shrine.RunModifiers mods = com.solme.emberfall.shrine.RunModifiers.peek(instance.slot());
        if (mods != null) {
            brain.applyCurse(mods.bossStatMultiplier()); // Boss Curse shrine: before the boss enters the world
        }
        brain.setPartyDamageFactor(com.solme.emberfall.wave.PartyHealth.applyBoss(brain, com.solme.emberfall.world.RunManager.partySize(instance.slot())));
        brain.rememberPartyScaling();                      // so a later phase resize keeps the party's health instead of erasing it
        brain.startFight(instance.slot());
        level.addFreshEntity(brain);

        // The Broodtide fights on the map as it is: no dais or moat is carved (the expedition map is unbreakable).
        BroodtideBossFight fight = new BroodtideBossFight(instance.slot(), brain);
        active.put(instance.slot(), fight);

        WaveDirector director = WaveDirector.get(instance.slot());
        if (director != null) {
            director.setBossActive(true);
        }
        EmberfallMod.LOGGER.info("Broodtide boss fight started for slot {} at {}", instance.slot(), pos);
        return fight;
    }

    private static BlockPos pickSpawnPos(ArenaInstance instance) {
        List<BlockPos> bossMarkers = instance.markers("boss_spawn");
        if (!bossMarkers.isEmpty()) {
            return bossMarkers.get(0);
        }
        List<BlockPos> spawnPoints = instance.markers("spawn_point");
        if (!spawnPoints.isEmpty()) {
            return spawnPoints.get(spawnPoints.size() - 1);
        }
        return instance.origin();
    }

    public static boolean isActive(int slot) {
        return active.containsKey(slot);
    }

    /** Once a tick: detect fights whose brain is no longer alive and resume normal spawning. */
    public static void tickAll(MinecraftServer server) {
        if (active.isEmpty()) {
            return;
        }
        Iterator<Map.Entry<Integer, BroodtideBossFight>> it = active.entrySet().iterator();
        while (it.hasNext()) {
            BroodtideBossFight fight = it.next().getValue();
            if (fight.brain.isAlive()) {
                continue;
            }
            it.remove();
            boolean genuineKill = fight.brain.wasDefeated();
            if (genuineKill) {
                RunTelemetry.markHydraDefeated(fight.slot);
            }
            // The Broodtide has no rig of helper entities to tear down (one real Slime), so there is nothing to orphan on an early exit.
            if (fight.brain.level() instanceof ServerLevel brainLevel) {
                // 2026-09-28 tier escalation: only a genuine kill promotes the run to
                // tier 2 - a rig discarded early by run teardown (leave/disconnect)
                // must not silently hand the player a harder run they never earned.
                if (genuineKill) {
                    WaveDirector escalated = WaveDirector.get(fight.slot);
                    if (escalated != null) {
                        escalated.onHydraDefeated(brainLevel);
                    }
                }
            }
            WaveDirector director = WaveDirector.get(fight.slot);
            if (director != null) {
                director.setBossActive(false);
            }
            EmberfallMod.LOGGER.info("Broodtide boss fight ended for slot {} - normal waves resumed", fight.slot);
        }
    }
}
