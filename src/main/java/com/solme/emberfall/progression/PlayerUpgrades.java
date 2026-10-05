package com.solme.emberfall.progression;

import com.mojang.serialization.Codec;
import net.minecraft.core.UUIDUtil;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.datafix.DataFixTypes;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Persistent per-player levels for every {@link UpgradeType} the currency
 * shop sells - which upgrade ids each player has bought and how many
 * levels of each, across every run and server restart. Same codec/anchor
 * pattern as {@link MetaProgressionData} / {@link WeaponUnlocks} (a single
 * unboundedMap keyed by player UUID, xmap'd to/from this class, attached
 * to the overworld's DataStorage) - kept as its own class rather than
 * folded into either of those because it's a genuinely separate concern
 * (tiered levels vs a flat balance vs a plain unlock set) with its own
 * codec shape.
 *
 * Stored as {@code Map<UUID, Map<String, Integer>>} (upgrade id -> level)
 * so a missing entry naturally means level 0 with no special-casing.
 */
public final class PlayerUpgrades extends SavedData {
    public static final SavedDataType<PlayerUpgrades> TYPE = new SavedDataType<>(
            "emberfall_player_upgrades",
            PlayerUpgrades::new,
            Codec.unboundedMap(UUIDUtil.STRING_CODEC, Codec.unboundedMap(Codec.STRING, Codec.INT))
                    .xmap(PlayerUpgrades::new, data -> data.levels),
            DataFixTypes.LEVEL
    );

    private final Map<UUID, Map<String, Integer>> levels;

    public PlayerUpgrades() {
        this(new HashMap<>());
    }

    private PlayerUpgrades(Map<UUID, Map<String, Integer>> levels) {
        this.levels = new HashMap<>();
        for (Map.Entry<UUID, Map<String, Integer>> entry : levels.entrySet()) {
            this.levels.put(entry.getKey(), new HashMap<>(entry.getValue()));
        }
    }

    public static PlayerUpgrades get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(TYPE);
    }

    /** This player's current level of one upgrade (0 if never bought). */
    public int getLevel(UUID player, String upgradeId) {
        Map<String, Integer> owned = levels.get(player);
        return owned == null ? 0 : owned.getOrDefault(upgradeId, 0);
    }

    /** Bumps one upgrade up by exactly one level and returns the new level. */
    public int levelUp(UUID player, String upgradeId) {
        Map<String, Integer> owned = levels.computeIfAbsent(player, k -> new HashMap<>());
        int updated = owned.getOrDefault(upgradeId, 0) + 1;
        owned.put(upgradeId, updated);
        setDirty();
        return updated;
    }
}
