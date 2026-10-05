package com.solme.emberfall.world;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.phys.AABB;

import java.util.List;
import java.util.Set;

/**
 * Makes every hunting mob of a run go after the player at once, from anywhere in the arena.
 *
 * Measured with 8 mobs placed 12 to 16 blocks from a survival player: only the one wave mob that spawned already
 * aggroed held a target for the first six seconds, and the rest (zombies, skeletons, spiders alike) stood still
 * until the player walked within about 10 blocks. Vanilla target goals only notice a player inside their own
 * search box and only re-check on random ticks, and wave spawn points sit around the rim, so a fresh wave would
 * idle at the edge of the arena. This sweep gives each idle hunter the nearest player of its run once a second.
 *
 * Cost: one entity query per active arena per second, no entities, no packets. It only fills an EMPTY target, so it
 * never overrides a mob's own choice (a minion targeting a decoy, a mob hurt by someone else, a boss ability).
 */
public final class RunMobAggro {
    private static final int SWEEP_EVERY_TICKS = 20;
    /** Same margin the purge uses, so a mob just outside the box still gets a target. */
    private static final double MARGIN = 8.0;

    /**
     * Every emberfall mob that hunts the player. The sweep only fills an EMPTY target, so a mob that steers itself (the
     * Pink Slime leaps at whatever it targets, the Tiki glides to it) is handed a player and then uses it as it likes.
     * Left alone: the Devourer brain (its own state machine) and the Ember Guardian (bosses pick targets themselves).
     */
    static final Set<String> HUNTERS = Set.of(
            "horde_zombie", "horde_skeleton", "horde_spider", "horde_witch", "horde_bomber", "horde_charger", "horde_shieldbearer", "horde_imp", "horde_spitter", "tiki_magma", "pink_slime", "broodling", "devourer_spawn",
            "cinderbrand_reaver", "blightfeather_marksman", "umbral_magus", "corrupted_sentinel",
            "plague_colossus", "boil_ridden_marksman", "broodmother_stalker", "bonecaller_necromancer");

    private RunMobAggro() {}

    public static void tick(MinecraftServer server) {
        if (server.getTickCount() % SWEEP_EVERY_TICKS != 0) {
            return;
        }
        for (ArenaInstance arena : RunManager.activeInstances()) {
            if (!arena.inPlace()) {
                continue;
            }
            ServerLevel level = arena.level();
            List<ServerPlayer> players = level.players().stream()
                    .filter(p -> p.isAlive() && !p.isSpectator() && !p.isCreative()
                            && Integer.valueOf(arena.slot()).equals(RunManager.slotOf(p)))
                    .toList();
            if (players.isEmpty()) {
                continue;
            }
            AABB box = ArenaBoundary.playArea(arena).inflate(MARGIN, 0, MARGIN);
            // Hourglass: decided once per sweep for the whole party, so the per mob cost is one addEffect only while it is active.
            boolean hourglass = false;
            for (ServerPlayer p : players) {
                if (com.solme.emberfall.relic.PlayerRelics.active(p) && com.solme.emberfall.relic.RelicHourglass.slowsFoes(
                        com.solme.emberfall.relic.RelicEffects.stats(p), p.getHealth(), p.getMaxHealth())) {
                    hourglass = true;
                    break;
                }
            }
            for (Mob mob : level.getEntitiesOfClass(Mob.class, box, RunMobAggro::isHunter)) {
                if (hourglass) {
                    mob.addEffect(new net.minecraft.world.effect.MobEffectInstance(net.minecraft.world.effect.MobEffects.SLOWNESS,
                            com.solme.emberfall.relic.RelicHourglass.DURATION_TICKS, com.solme.emberfall.relic.RelicHourglass.AMPLIFIER, false, false, false));
                }
                if (mob.getTarget() != null && mob.getTarget().isAlive()) {
                    continue;
                }
                ServerPlayer nearest = null;
                double best = Double.MAX_VALUE;
                for (ServerPlayer p : players) {
                    double d = p.distanceToSqr(mob);
                    if (d < best) {
                        best = d;
                        nearest = p;
                    }
                }
                mob.setTarget(nearest);
            }
        }
    }

    /** True for an emberfall mob that hunts by vanilla AI. Pure decision, no side effects. */
    static boolean isHunter(Mob mob) {
        var key = BuiltInRegistries.ENTITY_TYPE.getKey(mob.getType());
        return key.getNamespace().equals("emberfall") && HUNTERS.contains(key.getPath()) && mob.isAlive();
    }
}
