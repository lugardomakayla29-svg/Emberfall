package com.solme.emberfall.bot;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.pathfinder.Path;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * One invisible vanilla mob per arena level that exists only to ASK the game's own pathfinder for a route. Vanilla path
 * search needs a {@link Mob} (its size, its pathing rules), and a player has none, so the scout stands at the asking bot's
 * feet, computes, and the bot walks the result itself. One extra entity per level however many bots play.
 *
 * <p>It carries {@link #TAG}, which {@code RunMobTeam.isTeamMob} refuses, so no weapon targets it, no wave counts it and no
 * sweep touches it.
 */
public final class BotScout {
    public static final String TAG = "emberfall_bot_scout";

    private static final Map<ServerLevel, Mob> SCOUTS = new ConcurrentHashMap<>();

    private BotScout() {}

    /** The scout for this level, created on first use. A zombie: 2 blocks tall and 0.6 wide, like the player, so it fits the same gaps. */
    private static Mob scoutFor(ServerLevel level) {
        Mob scout = SCOUTS.get(level);
        if (scout != null && scout.isAlive() && !scout.isRemoved()) {
            return scout;
        }
        Mob fresh = EntityType.ZOMBIE.create(level, EntitySpawnReason.COMMAND);
        fresh.addTag(TAG);
        fresh.setNoAi(true);
        fresh.setSilent(true);
        fresh.setInvisible(true);
        fresh.setInvulnerable(true);
        fresh.setPersistenceRequired();
        fresh.setCanPickUpLoot(false);
        fresh.setNoGravity(true);
        fresh.setPos(0.5, level.getMinY() + 1, 0.5);
        level.addFreshEntity(fresh);
        SCOUTS.put(level, fresh);
        return fresh;
    }

    /**
     * Asks vanilla for a route from (fromX, fromY, fromZ) to {@code target}, giving up beyond {@code range} blocks.
     * Returns the standable points along it (block centres), or an empty list when no route reaches the target.
     * The scout is moved to the start first, because the search begins where the mob stands.
     */
    public static List<BotWalk.Point> route(ServerLevel level, double fromX, double fromY, double fromZ, BlockPos target, int range) {
        Mob scout = scoutFor(level);
        scout.setPos(fromX, fromY, fromZ);
        // Vanilla ground navigation refuses to search (returns null) unless the mob is on the ground, in liquid or riding
        // (GroundPathNavigation.canUpdatePath, read from the 1.21.11 bytecode). The scout is never ticked and has no gravity,
        // so it would never count as grounded; claim it, because it is placed on walkable floor by construction.
        scout.setOnGround(true);
        // createPath(pos, accuracy): the int is the ACCURACY (a node within that many blocks of the target counts as arrived) and
        // the search distance is a fixed 8. Passing the search range there made the start block "arrived" (a one-node path, read
        // from PathNavigation.createPath in the 1.21.11 bytecode). The three-argument form sets both: land within 1 block, look
        // up to `range` blocks away.
        Path path = scout.getNavigation().createPath(target, 1, range);
        List<BotWalk.Point> points = new ArrayList<>();
        if (path == null || !path.canReach()) {
            return points;
        }
        for (int i = 0; i < path.getNodeCount(); i++) {
            var node = path.getNode(i);
            points.add(new BotWalk.Point(node.x + 0.5, node.y, node.z + 0.5));
        }
        return points;
    }

    /** Removes the scout of a level (run end), so nothing lingers. */
    public static void discard(ServerLevel level) {
        Mob scout = SCOUTS.remove(level);
        if (scout != null) {
            scout.discard();
        }
    }
}
