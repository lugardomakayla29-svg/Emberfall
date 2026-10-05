package com.solme.emberfall.progression;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.UUIDUtil;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.datafix.DataFixTypes;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * The item a run had to move out of the player's way, so a run can never destroy something they own.
 *
 * A run shows the first weapon in hotbar slot {@link #WEAPON_HOTBAR_SLOT}. Whatever stood there is put in this
 * store when the weapon is first shown and handed back when the run ends (death, leaving and disconnecting all
 * end through the same path). The store is persistent on purpose: a crash in the middle of a run must not eat the
 * item, so it is also handed back the next time the player joins with no run active.
 */
public final class DisplacedItems extends SavedData {
    /** Hotbar index the run's weapon is shown in. Fixed, so changing the selected slot never overwrites a second item. */
    public static final int WEAPON_HOTBAR_SLOT = 0;

    private record Entry(ItemStack stack) {
        static final Codec<Entry> CODEC = RecordCodecBuilder.create(i -> i.group(
                ItemStack.OPTIONAL_CODEC.fieldOf("stack").forGetter(Entry::stack)
        ).apply(i, Entry::new));
    }

    public static final SavedDataType<DisplacedItems> TYPE = new SavedDataType<>(
            "emberfall_displaced_items",
            DisplacedItems::new,
            Codec.unboundedMap(UUIDUtil.STRING_CODEC, Entry.CODEC)
                    .xmap(DisplacedItems::new, data -> data.stashed),
            DataFixTypes.LEVEL
    );

    private final Map<UUID, Entry> stashed;

    public DisplacedItems() {
        this(new HashMap<>());
    }

    private DisplacedItems(Map<UUID, Entry> stashed) {
        this.stashed = new HashMap<>(stashed);
    }

    public static DisplacedItems get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(TYPE);
    }

    public boolean has(UUID player) {
        return stashed.containsKey(player);
    }

    /**
     * Records {@code displaced} for this player unless something is already stashed. Called before every weapon
     * write, so a second write in the same run cannot replace the real item with the weapon it just placed.
     */
    public void stash(UUID player, ItemStack displaced) {
        if (stashed.containsKey(player)) {
            return;
        }
        stashed.put(player, new Entry(displaced.copy()));
        setDirty();
    }

    /** Removes and returns the stashed item, or {@code null} if there is none. May be {@link ItemStack#EMPTY}. */
    public ItemStack take(UUID player) {
        Entry entry = stashed.remove(player);
        if (entry == null) {
            return null;
        }
        setDirty();
        return entry.stack();
    }

    /**
     * Hands the player back what the run moved: every run weapon item is removed first, then the stashed item goes
     * back to its hotbar slot. If that slot is taken by something else, it goes to any free slot, and failing that
     * it drops at the player's feet. It is never destroyed.
     */
    public static void restore(ServerPlayer player) {
        var server = player.level().getServer();
        if (server == null) {
            return;
        }
        DisplacedItems store = get(server);
        ItemStack back = store.take(player.getUUID());
        stripWeaponItems(player);
        if (back == null || back.isEmpty()) {
            return;
        }
        var inv = player.getInventory();
        if (inv.getItem(WEAPON_HOTBAR_SLOT).isEmpty()) {
            inv.setItem(WEAPON_HOTBAR_SLOT, back);
        } else {
            inv.placeItemBackInInventory(back);
        }
    }

    /** Removes every emberfall weapon item from the player's inventory, hotbar and offhand included. */
    private static void stripWeaponItems(ServerPlayer player) {
        var inv = player.getInventory();
        for (int i = 0; i < inv.getContainerSize(); i++) {
            if (com.solme.emberfall.item.ModItems.weaponFor(inv.getItem(i).getItem()) != null) {
                inv.setItem(i, ItemStack.EMPTY);
            }
        }
    }
}
