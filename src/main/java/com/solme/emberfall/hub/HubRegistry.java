package com.solme.emberfall.hub;

import com.mojang.serialization.Codec;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.datafix.DataFixTypes;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Persistent record of which Ember Hearths have a built hub, as "dimension|x,y,z" keys. The hub's
 * blocks and entities are recoverable without it (blocks via the hub journal, entities via their
 * tag), so this only answers "is a hub already built at this Hearth?" and lets startup find hubs
 * whose Hearth block no longer exists. Same codec/anchor pattern as PlayerCharacterSelection.
 */
public final class HubRegistry extends SavedData {
    public static final SavedDataType<HubRegistry> TYPE = new SavedDataType<>(
            "emberfall_hub_registry",
            HubRegistry::new,
            Codec.STRING.listOf().xmap(HubRegistry::new, data -> new ArrayList<>(data.keys)),
            DataFixTypes.LEVEL
    );

    private final Set<String> keys;

    public HubRegistry() {
        this.keys = new LinkedHashSet<>();
    }

    private HubRegistry(List<String> keys) {
        this.keys = new LinkedHashSet<>(keys);
    }

    public static HubRegistry get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(TYPE);
    }

    static String key(ServerLevel level, BlockPos pos) {
        return level.dimension().identifier() + "|" + pos.getX() + "," + pos.getY() + "," + pos.getZ();
    }

    /** File-name-safe journal name for the hub at {@code pos}. */
    static String journalName(ServerLevel level, BlockPos pos) {
        return ("hub_" + level.dimension().identifier() + "_" + pos.getX() + "_" + pos.getY() + "_" + pos.getZ())
                .replace(':', '_').replace('/', '_');
    }

    public boolean isBuilt(ServerLevel level, BlockPos pos) {
        return keys.contains(key(level, pos));
    }

    public void add(ServerLevel level, BlockPos pos) {
        if (keys.add(key(level, pos))) {
            setDirty();
        }
    }

    public void remove(ServerLevel level, BlockPos pos) {
        if (keys.remove(key(level, pos))) {
            setDirty();
        }
    }

    public List<String> all() {
        return List.copyOf(keys);
    }
}
