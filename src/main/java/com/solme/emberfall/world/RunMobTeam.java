package com.solme.emberfall.world;

import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;

import java.util.List;

/**
 * Every mob inside a run's play area is on ONE team: the expedition. They may only ever fight the player.
 *
 * Measured before this existed (bot/summon_friendly_test.js, player pinned 40 blocks away so only mobs could be
 * involved): the Umbral Magus's vanilla-typed summons (Thrall = zombie/skeleton/husk, Colossus = wither skeleton)
 * picked Horde Skeletons as targets, and the bystanders' health fell from 200 to 8.5. Vanilla monsters do retaliate
 * against whoever hurt them, so one stray hit starts a mob-vs-mob fight that never ends.
 *
 * Two independent guards, so neither an AI quirk nor a stray projectile can start one:
 *  1. TARGETING: once a second, any team mob whose target is another team mob loses it and takes the nearest run
 *     player instead (so it is never left idle). One entity query per active arena per second, no packets.
 *  2. DAMAGE: damage whose responsible entity is a team mob and whose victim is a team mob is cancelled outright.
 *     Damage from a player, or with no responsible mob (fall, fire the player lit), is untouched, so the player's
 *     weapons, tomes and hazards all still work.
 *
 * "Team mob" = a Mob standing inside an active in-place arena that is not a player and not tamed. That covers the
 * mod's own types and the vanilla-typed summons alike, without a name list that would rot.
 */
public final class RunMobTeam {
    private static final int SWEEP_EVERY_TICKS = 20;
    private static final double MARGIN = 8.0;

    /** Debug counters, read by /emberfall debugsummons: mob-on-mob hits cancelled, and targets cleared. */
    public static volatile int vetoedHits = 0;
    public static volatile int clearedTargets = 0;

    private RunMobTeam() {}

    public static void init() {
        ServerLivingEntityEvents.ALLOW_DAMAGE.register((victim, source, amount) -> {
            if (!(victim instanceof Mob) || !(source.getEntity() instanceof Mob attacker)) {
                return true; // player damage, environment, or a non-mob victim: not our business
            }
            if (attacker == victim) {
                return true;
            }
            boolean friendly = isTeamMob(attacker) && isTeamMob(victim);
            if (friendly) {
                vetoedHits++;
            }
            return !friendly;
        });
    }

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
            AABB box = ArenaBoundary.playArea(arena).inflate(MARGIN, 0, MARGIN);
            for (Mob mob : level.getEntitiesOfClass(Mob.class, box, RunMobTeam::isTeamMob)) {
                LivingEntity target = mob.getTarget();
                if (target == null || target instanceof Player || !isTeamMob(target)) {
                    continue;
                }
                clearedTargets++;
                mob.setTarget(nearest(players, mob)); // null when nobody is in the run: it just goes idle
            }
        }
    }

    private static ServerPlayer nearest(List<ServerPlayer> players, Mob mob) {
        ServerPlayer best = null;
        double bestDist = Double.MAX_VALUE;
        for (ServerPlayer p : players) {
            double d = p.distanceToSqr(mob);
            if (d < bestDist) {
                bestDist = d;
                best = p;
            }
        }
        return best;
    }

    /** True for a mob that belongs to the expedition: inside an active in-place arena, not a player's pet. Pure decision. */
    public static boolean isTeamMob(LivingEntity e) {
        if (!(e instanceof Mob mob) || !mob.isAlive() || e instanceof Player) {
            return false;
        }
        if (mob.getTags().contains(com.solme.emberfall.bot.BotScout.TAG)) {
            return false; // the EmberTester's path scout: scenery for the game, never an enemy, never counted, never swept
        }
        if (mob instanceof net.minecraft.world.entity.TamableAnimal tame && tame.isTame()) {
            return false;
        }
        if (!(mob.level() instanceof ServerLevel)) {
            return false;
        }
        for (ArenaInstance arena : RunManager.activeInstances()) {
            if (arena.inPlace() && arena.level() == mob.level()
                    && ArenaBoundary.playArea(arena).inflate(MARGIN, 4.0, MARGIN).contains(mob.position())) {
                return true;
            }
        }
        return false;
    }
}
