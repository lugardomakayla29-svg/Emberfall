package com.solme.emberfall.character;

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
 * Persistent per-player Character selection - which {@link CharacterType}
 * id each player has chosen with {@code /character select}, across every
 * run and server restart. Same codec/anchor pattern as
 * {@link com.solme.emberfall.progression.WeaponUnlocks} (a single
 * unboundedMap keyed by player UUID, attached to the overworld's
 * DataStorage), but a flat one-value-per-player map rather than a set,
 * since a player has exactly one active Character at a time (not a set of
 * owned ones - see {@link CharacterPool#defaultUnlocked()} for which ids
 * are even choosable).
 */
public final class PlayerCharacterSelection extends SavedData {
    public static final SavedDataType<PlayerCharacterSelection> TYPE = new SavedDataType<>(
            "emberfall_player_character_selection",
            PlayerCharacterSelection::new,
            Codec.unboundedMap(UUIDUtil.STRING_CODEC, Codec.STRING)
                    .xmap(PlayerCharacterSelection::new, data -> data.selected),
            DataFixTypes.LEVEL
    );

    private final Map<UUID, String> selected;

    public PlayerCharacterSelection() {
        this(new HashMap<>());
    }

    private PlayerCharacterSelection(Map<UUID, String> selected) {
        this.selected = new HashMap<>(selected);
    }

    public static PlayerCharacterSelection get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(TYPE);
    }

    /** This player's selected Character, or {@link CharacterPool#fallback()} if they never picked one. */
    public CharacterType selectedOrFallback(UUID player) {
        String id = selected.get(player);
        CharacterType found = id == null ? null : CharacterPool.byId(id);
        return found != null ? found : CharacterPool.fallback();
    }

    public boolean hasSelected(UUID player) {
        return selected.containsKey(player);
    }

    public void select(UUID player, String characterId) {
        selected.put(player, characterId);
        setDirty();
    }
}
