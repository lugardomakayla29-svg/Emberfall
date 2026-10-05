package com.solme.emberfall.progression;

import com.mojang.serialization.Codec;
import com.solme.emberfall.item.WeaponPool;
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
 * Persistent per-player weapon unlocks - which weapon ids (design doc:
 * "buy them with the meta-currency shop", one of the weapon-system's
 * combined acquisition methods) each player has permanently bought/earned,
 * across every run and server restart. Every weapon with
 * {@link com.solme.emberfall.item.WeaponType#unlockedByDefault()} is
 * implicitly unlocked for everyone and never needs to be stored - only the
 * non-default ones a player actually bought get persisted here.
 *
 * Same codec/anchor pattern as {@link MetaProgressionData} (a single
 * unboundedMap keyed by player UUID, xmap'd to/from this class, attached
 * to the overworld's DataStorage) - kept as its own class rather than
 * folded into MetaProgressionData because it's a genuinely separate
 * concern (unlock set vs currency balance) with its own codec shape.
 */
public final class WeaponUnlocks extends SavedData {
    public static final SavedDataType<WeaponUnlocks> TYPE = new SavedDataType<>(
            "emberfall_weapon_unlocks",
            WeaponUnlocks::new,
            Codec.unboundedMap(UUIDUtil.STRING_CODEC, Codec.STRING.listOf())
                    .xmap(WeaponUnlocks::fromLists, WeaponUnlocks::toLists),
            DataFixTypes.LEVEL
    );

    private final Map<UUID, Set<String>> unlocked;

    public WeaponUnlocks() {
        this(new HashMap<>());
    }

    private WeaponUnlocks(Map<UUID, Set<String>> unlocked) {
        this.unlocked = new HashMap<>(unlocked);
    }

    private static WeaponUnlocks fromLists(Map<UUID, List<String>> src) {
        Map<UUID, Set<String>> out = new HashMap<>();
        src.forEach((id, list) -> out.put(id, new HashSet<>(list)));
        return new WeaponUnlocks(out);
    }

    private Map<UUID, List<String>> toLists() {
        Map<UUID, List<String>> out = new HashMap<>();
        unlocked.forEach((id, set) -> out.put(id, new ArrayList<>(set)));
        return out;
    }

    public static WeaponUnlocks get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(TYPE);
    }

    /** True if this player can use this weapon id right now - default-unlocked or bought. */
    public boolean isUnlocked(UUID player, String weaponId) {
        var weapon = WeaponPool.byId(weaponId);
        if (weapon != null && weapon.unlockedByDefault()) {
            return true;
        }
        Set<String> owned = unlocked.get(player);
        return owned != null && owned.contains(weaponId);
    }

    public Set<String> boughtWeapons(UUID player) {
        return unlocked.getOrDefault(player, Set.of());
    }

    /** Permanently unlocks a weapon id for this player. Idempotent. */
    public void unlock(UUID player, String weaponId) {
        unlocked.computeIfAbsent(player, k -> new HashSet<>()).add(weaponId);
        setDirty();
    }
}
