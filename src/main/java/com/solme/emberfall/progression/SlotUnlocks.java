package com.solme.emberfall.progression;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.UUIDUtil;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.datafix.DataFixTypes;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Persistent per-player weapon and tome slot unlocks, bought in the shop with Silver. Everyone starts with
 * {@link #BASE_SLOTS} of each and can buy up to {@link #MAX_SLOTS}. Prices rise with every slot bought, so the
 * last slot is a real goal rather than an afterthought.
 *
 * Same storage pattern as {@link WeaponUnlocks} and {@link MetaProgressionData}: one map keyed by player UUID,
 * attached to the overworld's data storage, so it survives restarts and applies in every world the server hosts.
 */
public final class SlotUnlocks extends SavedData {
    public static final int BASE_SLOTS = 1;
    public static final int MAX_SLOTS = 4;

    /** Silver price of the 2nd, 3rd and 4th weapon slot (index 0 is the 2nd slot). */
    private static final long[] WEAPON_SLOT_PRICES = {100, 300, 700};
    /** Silver price of the 2nd, 3rd and 4th tome slot. */
    private static final long[] TOME_SLOT_PRICES = {75, 250, 600};

    public enum Kind { WEAPON, TOME }

    /** What one player has bought, as extra slots on top of {@link #BASE_SLOTS}. */
    private record Owned(int weapon, int tome) {
        static final Codec<Owned> CODEC = RecordCodecBuilder.create(i -> i.group(
                Codec.INT.fieldOf("weapon").forGetter(Owned::weapon),
                Codec.INT.fieldOf("tome").forGetter(Owned::tome)
        ).apply(i, Owned::new));
    }

    public static final SavedDataType<SlotUnlocks> TYPE = new SavedDataType<>(
            "emberfall_slot_unlocks",
            SlotUnlocks::new,
            Codec.unboundedMap(UUIDUtil.STRING_CODEC, Owned.CODEC)
                    .xmap(SlotUnlocks::new, data -> data.bought),
            DataFixTypes.LEVEL
    );

    private final Map<UUID, Owned> bought;

    public SlotUnlocks() {
        this(new HashMap<>());
    }

    private SlotUnlocks(Map<UUID, Owned> bought) {
        this.bought = new HashMap<>(bought);
    }

    public static SlotUnlocks get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(TYPE);
    }

    /** How many slots of this kind the player may use right now (1 to 4). */
    public int slots(UUID player, Kind kind) {
        Owned o = bought.getOrDefault(player, new Owned(0, 0));
        int extra = kind == Kind.WEAPON ? o.weapon() : o.tome();
        return Math.min(MAX_SLOTS, BASE_SLOTS + Math.max(0, extra));
    }

    /** Silver price of the next slot of this kind, or -1 when every slot is already bought. */
    public long nextPrice(UUID player, Kind kind) {
        int have = slots(player, kind);
        if (have >= MAX_SLOTS) {
            return -1;
        }
        long[] table = kind == Kind.WEAPON ? WEAPON_SLOT_PRICES : TOME_SLOT_PRICES;
        return table[have - BASE_SLOTS];
    }

    /**
     * Buys the next slot: charges Silver and records the unlock as one step. Returns the price paid, or -1 if
     * everything is already bought, or -2 if the player cannot afford it. Nothing is charged on failure.
     */
    public long buyNext(MinecraftServer server, UUID player, Kind kind) {
        long price = nextPrice(player, kind);
        if (price < 0) {
            return -1;
        }
        MetaProgressionData currency = MetaProgressionData.get(server);
        if (currency.getBalance(player) < price) {
            return -2;
        }
        currency.addCurrency(player, -price);
        Owned o = bought.getOrDefault(player, new Owned(0, 0));
        bought.put(player, kind == Kind.WEAPON
                ? new Owned(o.weapon() + 1, o.tome())
                : new Owned(o.weapon(), o.tome() + 1));
        setDirty();
        return price;
    }
}
