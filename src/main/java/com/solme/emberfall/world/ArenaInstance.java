package com.solme.emberfall.world;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.levelgen.structure.BoundingBox;

import java.util.List;
import java.util.Map;

/**
 * A single live run area. Produced either by {@link RunManager#pasteArena} (a hand-built
 * structure pasted into the emberfall:expedition dimension - kept for the operator debug
 * workflow) or by {@link RunManager#startOnMap} (the permanent static expedition map, the real
 * player-facing mode).
 *
 * Carries the {@link ServerLevel} it lives in, so nothing downstream has to assume a
 * particular dimension, and a {@link BlockJournal} so every block the run touches can be
 * put back exactly when the run ends. Markers are grouped by type (e.g. "spawn_point",
 * "shrine", "boss_spawn", "player_entry").
 */
public final class ArenaInstance {
    private final int slot;
    private final ServerLevel level;
    private final BlockPos origin;
    private final BoundingBox bounds;
    private final Map<String, List<BlockPos>> markers;
    private final BlockJournal journal;
    private final boolean inPlace;
    private final boolean map;

    public ArenaInstance(int slot, ServerLevel level, BlockPos origin, BoundingBox bounds,
                         Map<String, List<BlockPos>> markers, boolean inPlace) {
        this(slot, level, origin, bounds, markers, inPlace, false);
    }

    public ArenaInstance(int slot, ServerLevel level, BlockPos origin, BoundingBox bounds,
                         Map<String, List<BlockPos>> markers, boolean inPlace, boolean map) {
        this.slot = slot;
        this.level = level;
        this.origin = origin;
        this.bounds = bounds;
        this.markers = markers;
        this.journal = new BlockJournal(level);
        this.inPlace = inPlace;
        this.map = map;
    }

    public int slot() {
        return slot;
    }

    public ServerLevel level() {
        return level;
    }

    public BlockPos origin() {
        return origin;
    }

    public BoundingBox bounds() {
        return bounds;
    }

    public Map<String, List<BlockPos>> markers() {
        return markers;
    }

    public List<BlockPos> markers(String type) {
        return markers.getOrDefault(type, List.of());
    }

    public BlockJournal journal() {
        return journal;
    }

    /** True for a natural-terrain run in the player's own world; false for a pasted structure in the expedition dimension. */
    public boolean inPlace() {
        return inPlace;
    }

    /**
     * True for a run on the static expedition map (expedition dimension, circular). It counts as in-place for the journal
     * and the mob systems (the map must survive the run, only the run's own block changes are reverted), but the player is
     * teleported in, and the circle guard, not the rectangular one, keeps them inside.
     */
    public boolean isMap() {
        return map;
    }
}
