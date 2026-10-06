package com.solme.emberfall.boss;

import com.solme.emberfall.EmberfallMod;
import com.solme.emberfall.entity.EmberGuardian;
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
 * Design doc 4.3: "Triggering boss spawns at configured time marks,
 * replacing normal wave spawning for the duration of the boss fight."
 * This is the per-slot orchestrator that owns that hand-off: spawning the
 * one boss this milestone proves ({@link EmberGuardian}, replacing the Hydra),
 * pausing the slot's {@link WaveDirector} for the fight's duration, and
 * resuming normal horde spawning once the brain is no longer alive -
 * whether that's a real kill or the rig being discarded early by
 * {@code RunManager.teardownArena} on run end. Polling brain.isAlive()
 * once a tick (rather than requiring every caller that might end the
 * fight to remember to notify this class) is what makes that "however it
 * ends" behavior correct for free.
 */
public final class GuardianBossFight {
    private static final Map<Integer, GuardianBossFight> active = new HashMap<>();

    private final int slot;
    private final EmberGuardian brain;

    private GuardianBossFight(int slot, EmberGuardian brain) {
        this.slot = slot;
        this.brain = brain;
    }

    /** Spawns the Ember Guardian rig into the given arena and pauses that slot's Wave Director. */
    public static GuardianBossFight spawn(ServerLevel level, ArenaInstance instance) {
        if (active.containsKey(instance.slot())) {
            return active.get(instance.slot());
        }

        BlockPos pos = pickSpawnPos(instance);
        EmberGuardian brain = new EmberGuardian(ModEntities.EMBER_GUARDIAN, level);
        brain.setPos(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5);
        com.solme.emberfall.shrine.RunModifiers mods = com.solme.emberfall.shrine.RunModifiers.peek(instance.slot());
        if (mods != null) {
            brain.applyCurse(mods.bossStatMultiplier()); // Boss Curse shrine: before the boss enters the world
        }
        brain.setPartyDamageFactor(com.solme.emberfall.wave.PartyHealth.applyBoss(brain, com.solme.emberfall.world.RunManager.partySize(instance.slot())));
        level.addFreshEntity(brain);
        brain.spawnRig(level);

        // The Ember Guardian fights on the map as it is: no dais or moat is carved (the expedition map is unbreakable).
        GuardianBossFight fight = new GuardianBossFight(instance.slot(), brain);
        active.put(instance.slot(), fight);

        WaveDirector director = WaveDirector.get(instance.slot());
        if (director != null) {
            director.setBossActive(true);
        }
        EmberfallMod.LOGGER.info("Ember Guardian boss fight started for slot {} at {}", instance.slot(), pos);
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
        Iterator<Map.Entry<Integer, GuardianBossFight>> it = active.entrySet().iterator();
        while (it.hasNext()) {
            GuardianBossFight fight = it.next().getValue();
            if (fight.brain.isAlive()) {
                continue;
            }
            it.remove();
            boolean genuineKill = fight.brain.wasDefeated();
            if (genuineKill) {
                RunTelemetry.markHydraDefeated(fight.slot);
            }
            // Belt-and-suspenders: die() already tears the rig down for a
            // real kill, but this brain can in principle stop being
            // alive some other way (e.g. despawn slipping through some
            // future edge case, chunk unload). Without this, that path
            // would leave orphaned, no-longer-redirect-protected anchors
            // and heads wandering the arena forever. teardownRig is
            // idempotent (no-ops on an already-cleared rig), so calling
            // it unconditionally here is always safe.
            if (fight.brain.level() instanceof ServerLevel brainLevel) {
                fight.brain.teardownRig(brainLevel);
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
            EmberfallMod.LOGGER.info("Ember Guardian boss fight ended for slot {} - normal waves resumed", fight.slot);
        }
    }
}
