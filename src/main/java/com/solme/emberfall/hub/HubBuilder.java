package com.solme.emberfall.hub;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.AABB;

import java.util.ArrayList;
import java.util.List;

/**
 * Cleanup of a hub that an OLD world still holds. Nothing builds a hub any more; this only finds and removes the figures
 * (busts, holograms, hotspots) that an earlier version spawned around a Hearth, each tagged with the Hearth's position.
 */
public final class HubBuilder {
    /** Tag prefix on every hub entity; the suffix is the Hearth position. */
    public static final String TAG_PREFIX = "emberfall_hub_";

    /** Half-width of the old hub's 9x9 footprint (the value HubSiteAnalyzer.RADIUS had when the hub was built). */
    private static final int OLD_HUB_RADIUS = 4;

    private HubBuilder() {}

    /** The entity tag that ties every hub entity to the Hearth at {@code hearth}. */
    public static String tagFor(BlockPos hearth) {
        return TAG_PREFIX + hearth.getX() + "_" + hearth.getY() + "_" + hearth.getZ();
    }

    /** Removes every entity belonging to the Hearth at {@code hearth}. Returns how many were removed. */
    public static int removeEntities(ServerLevel level, BlockPos hearth) {
        String tag = tagFor(hearth);
        // The old hub fits inside the 9x9 footprint and 6 blocks of height; a slightly larger box is safe
        // because only entities carrying this exact tag are touched.
        AABB box = new AABB(hearth).inflate(OLD_HUB_RADIUS + 2, 8, OLD_HUB_RADIUS + 2);
        List<Entity> found = new ArrayList<>(level.getEntities((Entity) null, box, e -> e.getTags().contains(tag)));
        for (Entity e : found) {
            e.discard();
        }
        return found.size();
    }
}
