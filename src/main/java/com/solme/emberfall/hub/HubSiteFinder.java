package com.solme.emberfall.hub;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;

import java.util.ArrayList;
import java.util.List;

/**
 * Finds the nearest valid hub site around a point, by scanning outward in growing square rings.
 *
 * Measured on real generated terrain (seed emberfall-hubtest-1, 6 spread areas, 5,766 sites each): a
 * 9x9 strictly-flat footprint passes at only about 0.5% of sites, roughly one per 185 candidates. So
 * the nearest valid site is usually tens of blocks away, not next to the player, and this class exists
 * to find it rather than just say no.
 *
 * Cost control: every candidate runs {@link HubSiteAnalyzer#analyse}, which reads a few hundred blocks.
 * Doing the whole search in one tick would freeze the server, so the search is an incremental job:
 * {@link #step} examines a bounded number of candidates and reports whether it is finished. The caller
 * ticks it until it returns a result or gives up at {@link #MAX_RADIUS}.
 */
public final class HubSiteFinder {
    /** Distance between candidate centres. Smaller finds closer sites but costs more; 3 keeps it dense. */
    public static final int GRID = 3;
    /** Give up beyond this many blocks from the origin. */
    public static final int MAX_RADIUS = 160;
    /** Candidates examined per {@link #step}; keeps each tick well under the 50ms budget. */
    public static final int PER_STEP = 6;

    private final ServerLevel level;
    private final int radius;
    private final int margin;
    private final int maxRelief;
    private final int originX;
    private final int originZ;
    private final List<int[]> queue = new ArrayList<>();
    private int cursor;
    private int ringsBuilt;
    private boolean done;
    private BlockPos found;

    public HubSiteFinder(ServerLevel level, BlockPos origin) {
        this(level, origin, HubSiteAnalyzer.RADIUS, HubSiteAnalyzer.MARGIN, HubSiteAnalyzer.MAX_RELIEF);
    }

    /** Finder with an explicit footprint, used to measure how footprint size trades against search distance. */
    public HubSiteFinder(ServerLevel level, BlockPos origin, int radius, int margin, int maxRelief) {
        this.radius = radius;
        this.margin = margin;
        this.maxRelief = maxRelief;
        this.level = level;
        this.originX = origin.getX();
        this.originZ = origin.getZ();
    }

    /** True once a site was found or the search radius was exhausted. */
    public boolean isDone() {
        return done;
    }

    /** The ground position of the nearest valid site, or null if none exists within {@link #MAX_RADIUS}. */
    public BlockPos result() {
        return found;
    }

    /** Examines up to {@link #PER_STEP} candidates. Safe to call after the search is done (does nothing). */
    public void step() {
        int budget = PER_STEP;
        while (!done && budget > 0) {
            if (cursor >= queue.size()) {
                if (!buildNextRing()) {
                    done = true;
                    return;
                }
                continue;
            }
            int[] c = queue.get(cursor++);
            budget--;
            HubSiteAnalyzer.Result r = HubSiteAnalyzer.analyse(level, originX + c[0], originZ + c[1], radius, margin, maxRelief);
            if (r.ok()) {
                found = r.ground();
                done = true;
                return;
            }
        }
    }

    /**
     * Queues the next square ring of candidate offsets, nearest ring first, each ring sorted by true
     * distance so a corner is never preferred over a nearer edge cell. Returns false when past the radius.
     */
    private boolean buildNextRing() {
        int ring = ringsBuilt++;
        int d = ring * GRID;
        if (d > MAX_RADIUS) {
            return false;
        }
        queue.clear();
        cursor = 0;
        if (ring == 0) {
            queue.add(new int[] {0, 0});
            return true;
        }
        for (int i = -d; i <= d; i += GRID) {
            queue.add(new int[] {i, -d});
            queue.add(new int[] {i, d});
            if (i != -d && i != d) {
                queue.add(new int[] {-d, i});
                queue.add(new int[] {d, i});
            }
        }
        queue.sort((a, b) -> Long.compare((long) a[0] * a[0] + (long) a[1] * a[1],
                (long) b[0] * b[0] + (long) b[1] * b[1]));
        return true;
    }
}
