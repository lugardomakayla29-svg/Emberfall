package com.solme.emberfall.world;

import com.solme.emberfall.EmberfallMod;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Records the ORIGINAL contents of every block a run touches, so an expedition
 * fought in the player's real Overworld can be undone exactly.
 *
 * In-place expeditions must never permanently alter the player's world. Anything
 * in the mod that writes a block (shrine props, the Hydra's acid dais, the
 * Umbral Magus cauldron) goes through {@link #set} instead of
 * {@code level.setBlockAndUpdate}. The first write to a position snapshots the
 * block state AND its block entity NBT (so a chest, sign, spawner, etc. comes
 * back with its contents); later writes to the same position do not overwrite
 * that snapshot. {@link #restoreAll} writes everything back, in reverse
 * insertion order, so stacked edits unwind correctly.
 *
 * One journal per run slot. Not thread-safe: only used from the server thread.
 */
public final class BlockJournal {

    private record Snapshot(BlockState state, CompoundTag blockEntity) {}

    private final ServerLevel level;
    private final Map<BlockPos, Snapshot> originals = new LinkedHashMap<>();
    /** Crash-safety file: rewritten whenever the journal grows. Null for journals that are never persisted. */
    private Path backingFile;
    private boolean dirty;

    public BlockJournal(ServerLevel level) {
        this.level = level;
    }

    /** Directory (inside the world save) holding one journal file per live run. */
    public static Path journalDir(net.minecraft.server.MinecraftServer server) {
        return server.getWorldPath(net.minecraft.world.level.storage.LevelResource.ROOT)
                .resolve("data").resolve("emberfall_journals");
    }

    /** Makes this journal crash-safe by mirroring it to {@code emberfall_journals/<name>.dat}. */
    public void persistTo(Path dir, String name) {
        this.backingFile = dir.resolve(name + ".dat");
    }

    /** Cells that are never snapshotted (the Ember Hearth's own cell: restoring it would resurrect the hub). */
    private final java.util.Set<BlockPos> excluded = new java.util.HashSet<>();

    /** Marks {@code pos} as never to be recorded or restored by this journal. */
    public void exclude(BlockPos pos) {
        excluded.add(pos.immutable());
    }

    /** Snapshots {@code pos} (once) without changing it. */
    public void remember(BlockPos pos) {
        BlockPos key = pos.immutable();
        if (originals.containsKey(key) || excluded.contains(key)) {
            return;
        }
        BlockState state = level.getBlockState(key);
        BlockEntity be = level.getBlockEntity(key);
        CompoundTag tag = be != null ? be.saveWithFullMetadata(level.registryAccess()) : null;
        originals.put(key, new Snapshot(state, tag));
        dirty = true;
    }

    /** Journaled replacement for {@code level.setBlockAndUpdate}. */
    public void set(BlockPos pos, BlockState newState) {
        remember(pos);
        // Writing a block sends neighbour updates, and vanilla will happily delete a dependent
        // neighbour in response: the top half of tall grass or a flower when its lower half or its
        // soil changes, a torch or sign when its support goes, a door half, gravel above, vines, etc.
        // Those deletions never pass through this journal, so snapshot all 6 neighbours first (plus
        // the cell two above, for 2-tall plants whose upper half sits above an 'above' write).
        // Cells that end up unchanged restore as a harmless no-op. Found by a real regression run:
        // 3 grass blocks near a shrine were lost with only the shrine cells journaled.
        for (net.minecraft.core.Direction dir : net.minecraft.core.Direction.values()) {
            remember(pos.relative(dir));
        }
        remember(pos.above(2));
        flushIfDirty(); // originals are on disk BEFORE the world is changed
        level.setBlockAndUpdate(pos, newState);
    }

    /** True if this journal has recorded {@code pos}. */
    public boolean touched(BlockPos pos) {
        return originals.containsKey(pos);
    }

    public int size() {
        return originals.size();
    }

    /**
     * Restores a single position to its recorded original state (no-op if it was never recorded)
     * and forgets it. Used by transient effects, e.g. the Hydra dais when its fight ends.
     */
    public void restore(BlockPos pos) {
        Snapshot snap = originals.remove(pos);
        if (snap != null) {
            apply(pos, snap);
            dirty = true;
            flushIfDirty();
        }
    }

    /** Restores every recorded block to exactly what it was, then clears the journal. */
    public void restoreAll() {
        List<Map.Entry<BlockPos, Snapshot>> entries = new ArrayList<>(originals.entrySet());
        for (int i = entries.size() - 1; i >= 0; i--) {
            apply(entries.get(i).getKey(), entries.get(i).getValue());
        }
        originals.clear();
        dirty = true;
        flushIfDirty();
    }

    /** Writes the journal to disk if it changed. An empty journal deletes its file. */
    private void flushIfDirty() {
        if (!dirty || backingFile == null) {
            return;
        }
        try {
            if (originals.isEmpty()) {
                Files.deleteIfExists(backingFile);
            } else {
                Files.createDirectories(backingFile.getParent());
                CompoundTag root = new CompoundTag();
                root.putString("dimension", level.dimension().identifier().toString());
                ListTag list = new ListTag();
                for (Map.Entry<BlockPos, Snapshot> e : originals.entrySet()) {
                    CompoundTag c = new CompoundTag();
                    c.putInt("x", e.getKey().getX());
                    c.putInt("y", e.getKey().getY());
                    c.putInt("z", e.getKey().getZ());
                    c.put("state", NbtUtils.writeBlockState(e.getValue().state()));
                    if (e.getValue().blockEntity() != null) {
                        c.put("be", e.getValue().blockEntity());
                    }
                    list.add(c);
                }
                root.put("blocks", list);
                Path tmp = backingFile.resolveSibling(backingFile.getFileName() + ".tmp");
                NbtIo.writeCompressed(root, tmp);
                Files.move(tmp, backingFile, java.nio.file.StandardCopyOption.REPLACE_EXISTING,
                        java.nio.file.StandardCopyOption.ATOMIC_MOVE);
            }
            dirty = false;
        } catch (IOException ex) {
            EmberfallMod.LOGGER.error("Failed to persist block journal {}: {}", backingFile, ex.toString());
        }
    }

    /**
     * Startup recovery: any journal file still on disk belongs to a run that never got to clean up
     * (crash, kill -9, power loss). Put every recorded block back, then delete the file.
     * Returns how many blocks were restored.
     */
    public static int recoverLeftovers(net.minecraft.server.MinecraftServer server) {
        Path dir = journalDir(server);
        if (!Files.isDirectory(dir)) {
            return 0;
        }
        int restored = 0;
        try (var files = Files.list(dir)) {
            for (Path file : (Iterable<Path>) files.filter(f -> f.toString().endsWith(".dat"))::iterator) {
                restored += Math.max(0, restoreFile(server, file));
            }
        } catch (IOException ex) {
            EmberfallMod.LOGGER.error("Could not scan journal directory {}: {}", dir, ex.toString());
        }
        if (restored > 0) {
            EmberfallMod.LOGGER.warn("Recovered {} block(s) left over from an interrupted expedition", restored);
        }
        return restored;
    }

    /**
     * Restores every block recorded in one journal file, then deletes the file. Shared by startup
     * recovery of crashed runs and by removal of a persistent hub. Returns how many blocks were
     * restored, or -1 if the file was unreadable or its dimension is not loaded (file is kept).
     */
    public static int restoreFile(net.minecraft.server.MinecraftServer server, Path file) {
        try {
            CompoundTag root = NbtIo.readCompressed(file, NbtAccounter.unlimitedHeap());
            String dimStr = root.getStringOr("dimension", "minecraft:overworld");
            var dimId = net.minecraft.resources.Identifier.tryParse(dimStr);
            var key = net.minecraft.resources.ResourceKey.create(Registries.DIMENSION, dimId);
            ServerLevel lvl = server.getLevel(key);
            if (lvl == null) {
                EmberfallMod.LOGGER.warn("Journal {} references unloaded dimension {}, leaving it for later", file, dimStr);
                return -1;
            }
            int restored = 0;
            ListTag list = root.getListOrEmpty("blocks");
            for (int i = list.size() - 1; i >= 0; i--) {
                CompoundTag c = list.getCompoundOrEmpty(i);
                BlockPos pos = new BlockPos(c.getIntOr("x", 0), c.getIntOr("y", 0), c.getIntOr("z", 0));
                var state = NbtUtils.readBlockState(lvl.holderLookup(Registries.BLOCK), c.getCompoundOrEmpty("state"));
                lvl.removeBlockEntity(pos);
                lvl.setBlockAndUpdate(pos, state);
                var beTag = c.getCompound("be");
                if (beTag.isPresent()) {
                    var be = BlockEntity.loadStatic(pos, state, beTag.get(), lvl.registryAccess());
                    if (be != null) {
                        lvl.setBlockEntity(be);
                    }
                }
                restored++;
            }
            Files.deleteIfExists(file);
            return restored;
        } catch (Exception ex) {
            EmberfallMod.LOGGER.error("Could not recover journal {}: {}", file, ex.toString());
            return -1;
        }
    }

    /**
     * Directory for PERSISTENT hub journals. Deliberately separate from {@link #journalDir}: everything
     * in that one is treated as crash debris and rolled back at startup, whereas a hub is meant to
     * survive restarts and is only rolled back when its Hearth is broken.
     */
    public static Path hubJournalDir(net.minecraft.server.MinecraftServer server) {
        return server.getWorldPath(net.minecraft.world.level.storage.LevelResource.ROOT)
                .resolve("data").resolve("emberfall_hub_journals");
    }

    private void apply(BlockPos pos, Snapshot snap) {
        // Neighbour snapshots taken in set() are usually unchanged. Leave those completely alone:
        // clearing and rewriting a matching cell would needlessly fire neighbour updates and, for a
        // container, tear down and rebuild its block entity. A block entity's contents are only
        // compared when the cell has one, so a chest a run never touched is never disturbed.
        BlockState current = level.getBlockState(pos);
        if (current == snap.state() && snap.blockEntity() == null && level.getBlockEntity(pos) == null) {
            return;
        }
        if (current == snap.state() && snap.blockEntity() != null && level.getBlockEntity(pos) != null
                && snap.blockEntity().equals(level.getBlockEntity(pos).saveWithFullMetadata(level.registryAccess()))) {
            return;
        }
        // Clear any block entity first so a container's contents can't spill or duplicate on replacement.
        level.removeBlockEntity(pos);
        level.setBlock(pos, Blocks.AIR.defaultBlockState(), 2 | 16 | 32);
        level.setBlockAndUpdate(pos, snap.state());
        if (snap.blockEntity() != null) {
            BlockEntity fresh = BlockEntity.loadStatic(pos, snap.state(), snap.blockEntity(), level.registryAccess());
            if (fresh != null) {
                level.setBlockEntity(fresh);
            }
        }
    }
}
