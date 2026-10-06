package com.solme.emberfall.boss;

import com.solme.emberfall.EmberfallMod;
import com.solme.emberfall.entity.DevourerBrain;
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
 * The Devourer's per-slot orchestrator - identical hand-off responsibility
 * as {@link GuardianBossFight} (spawn the boss, pause that slot's
 * {@link WaveDirector} for the fight's duration, resume once the brain is
 * no longer alive, however that happens). Kept as its own class rather
 * than generalizing GuardianBossFight to take an arbitrary brain type: the
 * two fights have different one-time setup (Devourer has no arena
 * carve/tick/restore call, since its whole gimmick is roaming the arena
 * and burrowing through it rather than fighting over a fixed dais - see
 * notes/emberfall/boss-concepts.md for that call), so sharing one generic
 * class would need one of them to carry a no-op branch for the other's
 * concern. Two small, obviously-parallel classes are clearer than one
 * with speculative generality for a boss count of two.
 */
public final class DevourerBossFight {
    private static final Map<Integer, DevourerBossFight> active = new HashMap<>();

    private final int slot;
    private final DevourerBrain brain;

    private DevourerBossFight(int slot, DevourerBrain brain) {
        this.slot = slot;
        this.brain = brain;
    }

    /** Spawns the Devourer rig into the given arena and pauses that slot's Wave Director. */
    public static DevourerBossFight spawn(ServerLevel level, ArenaInstance instance) {
        if (active.containsKey(instance.slot())) {
            return active.get(instance.slot());
        }

        BlockPos pos = pickSpawnPos(instance);
        DevourerBrain brain = new DevourerBrain(ModEntities.DEVOURER_BRAIN, level);
        brain.setPos(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5);
        com.solme.emberfall.shrine.RunModifiers mods = com.solme.emberfall.shrine.RunModifiers.peek(instance.slot());
        if (mods != null) {
            brain.applyCurse(mods.bossStatMultiplier(), mods.bossSpawnMultiplier()); // Boss Curse shrine
        }
        brain.setPartyDamageFactor(com.solme.emberfall.wave.PartyHealth.applyBoss(brain, com.solme.emberfall.world.RunManager.partySize(instance.slot())));
        level.addFreshEntity(brain);
        brain.spawnRig(level);

        DevourerBossFight fight = new DevourerBossFight(instance.slot(), brain);
        active.put(instance.slot(), fight);

        WaveDirector director = WaveDirector.get(instance.slot());
        if (director != null) {
            director.setBossActive(true);
        }
        EmberfallMod.LOGGER.info("Devourer boss fight started for slot {} at {}", instance.slot(), pos);
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
        Iterator<Map.Entry<Integer, DevourerBossFight>> it = active.entrySet().iterator();
        while (it.hasNext()) {
            DevourerBossFight fight = it.next().getValue();
            if (fight.brain.isAlive()) {
                continue;
            }
            it.remove();
            if (fight.brain.wasDefeated()) {
                RunTelemetry.markDevourerDefeated(fight.slot);
            }
            // Belt-and-suspenders, same reasoning as GuardianBossFight: die()
            // already tears the rig down for a real kill, but teardownRig
            // is idempotent, so calling it unconditionally here is always
            // safe for any other way the brain could stop being alive.
            if (fight.brain.level() instanceof ServerLevel brainLevel) {
                fight.brain.teardownRig(brainLevel);
            }
            WaveDirector director = WaveDirector.get(fight.slot);
            if (director != null) {
                director.setBossActive(false);
                if (fight.brain.wasDefeated() && fight.brain.level() instanceof ServerLevel swarmLevel) {
                    director.beginSwarm(swarmLevel); // the last boss is down: the Final Swarm begins
                }
            }
            EmberfallMod.LOGGER.info("Devourer boss fight ended for slot {} - normal waves resumed", fight.slot);
        }
    }
}
