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
 * Design doc 2.3 / 10.1: persistent per-player meta-currency. Attached to
 * the overworld's DimensionDataStorage - the one dimension that's never
 * fully unloaded, so it's the right anchor for data that isn't specific to
 * any one level (2.3: "store via a custom PersistentState/SavedData-
 * equivalent attached to the server, keyed by player UUID").
 *
 * Deliberately the smallest possible slice of Section 10's meta-game: just
 * the currency balance itself, earned on run end (see
 * {@link RunRewardCalculator}). Unlockable characters, the Tome-pool
 * unlock-shop, and the Advancement-based quest layer (10.2-10.3) are
 * separate, later work - this only proves currency can be earned and
 * survives a server restart.
 *
 * DataFixTypes.LEVEL is passed for the required fourth SavedDataType
 * argument as the conventional generic bucket for mod-owned data with no
 * vanilla-registered fixer of its own (there is no "NONE"/"CUSTOM"
 * constant in this API) - a fresh custom codec at version 0 has nothing
 * for it to fix, so this is inert in practice. Verified empirically (built
 * and round-tripped through a real save/reload) rather than assumed.
 */
public final class MetaProgressionData extends SavedData {
    public static final SavedDataType<MetaProgressionData> TYPE = new SavedDataType<>(
            "emberfall_meta_progression",
            MetaProgressionData::new,
            Codec.unboundedMap(UUIDUtil.STRING_CODEC, Codec.LONG)
                    .xmap(MetaProgressionData::new, data -> data.balances),
            DataFixTypes.LEVEL
    );

    private final Map<UUID, Long> balances;

    public MetaProgressionData() {
        this(new HashMap<>());
    }

    private MetaProgressionData(Map<UUID, Long> balances) {
        this.balances = new HashMap<>(balances);
    }

    public static MetaProgressionData get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(TYPE);
    }

    public long getBalance(UUID player) {
        return balances.getOrDefault(player, 0L);
    }

    /** Adds (or, if negative, spends) currency and returns the new balance. */
    public long addCurrency(UUID player, long amount) {
        long updated = getBalance(player) + amount;
        balances.put(player, updated);
        setDirty();
        return updated;
    }
}
