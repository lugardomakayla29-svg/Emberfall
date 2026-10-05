package com.solme.emberfall.relic;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.UUIDUtil;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.datafix.DataFixTypes;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Persistent per-player relic unlocks, kept across runs and restarts. Two parts:
 *  - {@code unlocked}: the gated unlock ids a player has earned (ungated relics need no entry);
 *  - {@code progress}: running counters toward unlock conditions (chests opened, challenges cleared...).
 * Same anchor as {@code WeaponUnlocks}: the overworld's data storage, keyed by player UUID.
 */
public final class RelicUnlocks extends SavedData {
    /** How far each condition must get. Keep in step with the unlockId values used in {@link RelicPool}. */
    public static final Map<String, Integer> GOALS = Map.of(
            "open_25_chests", 25,
            "reach_level_15", 15,
            "clear_3_challenges", 3
    );

    private record Entry(List<String> unlocked, Map<String, Integer> progress) {}

    private static final Codec<Entry> ENTRY_CODEC = RecordCodecBuilder.create(i -> i.group(
            Codec.STRING.listOf().fieldOf("unlocked").forGetter(Entry::unlocked),
            Codec.unboundedMap(Codec.STRING, Codec.INT).fieldOf("progress").forGetter(Entry::progress)
    ).apply(i, Entry::new));

    public static final SavedDataType<RelicUnlocks> TYPE = new SavedDataType<>(
            "emberfall_relic_unlocks",
            RelicUnlocks::new,
            Codec.unboundedMap(UUIDUtil.STRING_CODEC, ENTRY_CODEC).xmap(RelicUnlocks::fromEntries, RelicUnlocks::toEntries),
            DataFixTypes.LEVEL
    );

    private final Map<UUID, Set<String>> unlocked;
    private final Map<UUID, Map<String, Integer>> progress;

    public RelicUnlocks() {
        this.unlocked = new HashMap<>();
        this.progress = new HashMap<>();
    }

    private static RelicUnlocks fromEntries(Map<UUID, Entry> src) {
        RelicUnlocks out = new RelicUnlocks();
        src.forEach((id, e) -> {
            out.unlocked.put(id, new HashSet<>(e.unlocked()));
            out.progress.put(id, new HashMap<>(e.progress()));
        });
        return out;
    }

    private Map<UUID, Entry> toEntries() {
        Set<UUID> ids = new HashSet<>(unlocked.keySet());
        ids.addAll(progress.keySet());
        Map<UUID, Entry> out = new HashMap<>();
        for (UUID id : ids) {
            out.put(id, new Entry(new ArrayList<>(unlocked.getOrDefault(id, Set.of())),
                    new HashMap<>(progress.getOrDefault(id, Map.of()))));
        }
        return out;
    }

    public static RelicUnlocks get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(TYPE);
    }

    /** Unlock ids this player has earned. */
    public Set<String> unlockedIds(UUID player) {
        return unlocked.getOrDefault(player, Set.of());
    }

    public boolean isUnlocked(UUID player, Relic relic) {
        return !relic.isGated() || unlockedIds(player).contains(relic.unlockId());
    }

    public int progressOf(UUID player, String unlockId) {
        return progress.getOrDefault(player, Map.of()).getOrDefault(unlockId, 0);
    }

    /** Permanently unlocks. Returns true only the first time (so the caller announces it once). */
    public boolean unlock(UUID player, String unlockId) {
        boolean fresh = unlocked.computeIfAbsent(player, k -> new HashSet<>()).add(unlockId);
        if (fresh) {
            setDirty();
        }
        return fresh;
    }

    /**
     * Adds progress toward a condition and unlocks it when the goal is reached. Returns the relics this call newly
     * unlocked (empty most of the time) so the caller can announce them.
     */
    public List<Relic> addProgress(UUID player, String unlockId, int amount) {
        Integer goal = GOALS.get(unlockId);
        if (goal == null || amount <= 0 || unlockedIds(player).contains(unlockId)) {
            return List.of();
        }
        Map<String, Integer> mine = progress.computeIfAbsent(player, k -> new HashMap<>());
        int now = Math.min(goal, mine.getOrDefault(unlockId, 0) + amount);
        mine.put(unlockId, now);
        setDirty();
        if (now < goal || !unlock(player, unlockId)) {
            return List.of();
        }
        List<Relic> opened = new ArrayList<>();
        for (Relic r : RelicPool.all()) {
            if (unlockId.equals(r.unlockId())) {
                opened.add(r);
            }
        }
        return opened;
    }

    /** Tells the player, in chat, which relics a progress call just unlocked. One wording for every unlock source. */
    public static void announce(net.minecraft.server.level.ServerPlayer player, List<Relic> unlocked) {
        for (Relic r : unlocked) {
            player.sendSystemMessage(net.minecraft.network.chat.Component.literal("Relic unlocked: ").withStyle(net.minecraft.ChatFormatting.GOLD)
                    .append(net.minecraft.network.chat.Component.literal(r.name()).withStyle(net.minecraft.ChatFormatting.YELLOW))
                    .append(net.minecraft.network.chat.Component.literal("  (" + r.unlockText() + ")").withStyle(net.minecraft.ChatFormatting.DARK_GRAY)), false);
        }
    }

    /** Sets progress to at least {@code value} (for "reach level N", which is a high-water mark, not a sum). */
    public List<Relic> raiseProgress(UUID player, String unlockId, int value) {
        int have = progressOf(player, unlockId);
        return value > have ? addProgress(player, unlockId, value - have) : List.of();
    }
}
