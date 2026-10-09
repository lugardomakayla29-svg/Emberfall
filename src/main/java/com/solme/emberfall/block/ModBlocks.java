package com.solme.emberfall.block;

import com.solme.emberfall.EmberfallMod;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockBehaviour;

public final class ModBlocks {
    private static final String HEARTH_TEXTURE =
            "eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvYmQ4YzczZmVlNTA4NGUzZTQzN2QyOWVmNThhZDg5ZjcyZmE0MzI3MjJjOTNmZThlOWZiMjk0MjU0MjliMGJkYyJ9fX0=";

    /** minecraft-heads.com "Brazier" (Other Illumination, Medieval, Metal). Lazy so authlib loads only when needed. */
    private static final java.util.function.Supplier<net.minecraft.world.item.component.ResolvableProfile> HEARTH_PROFILE = () -> {
        com.google.common.collect.HashMultimap<String, com.mojang.authlib.properties.Property> backing = com.google.common.collect.HashMultimap.create();
        backing.put("textures", new com.mojang.authlib.properties.Property("textures", HEARTH_TEXTURE));
        return net.minecraft.world.item.component.ResolvableProfile.createResolved(new com.mojang.authlib.GameProfile(
                java.util.UUID.nameUUIDFromBytes("emberfall:ember_hearth".getBytes(java.nio.charset.StandardCharsets.UTF_8)),
                "ember_hearth", new com.mojang.authlib.properties.PropertyMap(backing)));
    };

    /** minecraft-heads.com "Mage" (already used for the hub's Battlemage bust): decodes to a 200 PNG. */
    private static final String TABLE_TEXTURE = "eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvNzg1YmE1MjkxZjljZWZjNWIzNDU1NGY5NjY4ZjcxNWQzZGE4ZDI1ZWY2NjgyMTY1ZGVmZDBmYTBjZTRiYThhYyJ9fX0=";

    private static final java.util.function.Supplier<net.minecraft.world.item.component.ResolvableProfile> TABLE_PROFILE = () -> {
        com.google.common.collect.HashMultimap<String, com.mojang.authlib.properties.Property> backing = com.google.common.collect.HashMultimap.create();
        backing.put("textures", new com.mojang.authlib.properties.Property("textures", TABLE_TEXTURE));
        return net.minecraft.world.item.component.ResolvableProfile.createResolved(new com.mojang.authlib.GameProfile(
                java.util.UUID.nameUUIDFromBytes("emberfall:character_table".getBytes(java.nio.charset.StandardCharsets.UTF_8)),
                "character_table", new com.mojang.authlib.properties.PropertyMap(backing)));
    };

    public static final Block MARKER = register("marker");
    /** The Ember Hearth: placed by the player, right-clicked to build the hub. Breakable and obtainable. */
    public static final Block EMBER_HEARTH = registerHearth("ember_hearth");
    /** The Character Table: right-click to pick a character (lore on hover). Breakable and craftable. */
    public static final Block CHARACTER_TABLE = registerTable("character_table");

    /**
     * The three chests (paid, free, gold). Unbreakable and loot-less: a chest can never be mined, carried off or duplicated.
     * The items exist only so the creative tab and /give work for testing; in a run they are placed by ChestManager.
     */
    public static final Block CHEST_PAID = registerChest("ember_chest_paid", com.solme.emberfall.relic.ChestOpening.Kind.PAID, 7);
    public static final Block CHEST_FREE = registerChest("ember_chest_free", com.solme.emberfall.relic.ChestOpening.Kind.FREE, 10);
    public static final Block CHEST_GOLD = registerChest("ember_chest_gold", com.solme.emberfall.relic.ChestOpening.Kind.GOLD, 13);

    private ModBlocks() {}

    private static Block registerChest(String path, com.solme.emberfall.relic.ChestOpening.Kind kind, int light) {
        ResourceKey<Block> blockKey = ResourceKey.create(Registries.BLOCK, EmberfallMod.id(path));
        ResourceKey<Item> itemKey = ResourceKey.create(Registries.ITEM, EmberfallMod.id(path));
        Block block = new EmberChestBlock(BlockBehaviour.Properties.of()
                .setId(blockKey)
                .noOcclusion()
                .strength(-1.0F, 3600000.0F)
                .lightLevel(state -> state.getValue(EmberChestBlock.OPENED) ? 0 : light)
                .noLootTable(), kind);
        Block registered = Registry.register(BuiltInRegistries.BLOCK, blockKey, block);
        Registry.register(BuiltInRegistries.ITEM, itemKey, new BlockItem(registered, new Item.Properties().setId(itemKey)));
        return registered;
    }

    private static Block register(String path) {
        ResourceKey<Block> blockKey = ResourceKey.create(Registries.BLOCK, EmberfallMod.id(path));
        ResourceKey<Item> itemKey = ResourceKey.create(Registries.ITEM, EmberfallMod.id(path));

        Block block = new MarkerBlock(BlockBehaviour.Properties.of()
                .setId(blockKey)
                .noOcclusion()
                .noCollision()
                .strength(-1.0F, 3600000.0F)
                .noLootTable());

        Block registered = Registry.register(BuiltInRegistries.BLOCK, blockKey, block);
        Registry.register(
                BuiltInRegistries.ITEM,
                itemKey,
                new BlockItem(registered, new Item.Properties().setId(itemKey))
        );
        return registered;
    }

    private static Block registerHearth(String path) {
        ResourceKey<Block> blockKey = ResourceKey.create(Registries.BLOCK, EmberfallMod.id(path));
        ResourceKey<Item> itemKey = ResourceKey.create(Registries.ITEM, EmberfallMod.id(path));

        Block block = new com.solme.emberfall.hub.HearthBlock(BlockBehaviour.Properties.of()
                .setId(blockKey)
                .strength(3.0F, 6.0F)
                .lightLevel(state -> 12)
                .requiresCorrectToolForDrops());

        Block registered = Registry.register(BuiltInRegistries.BLOCK, blockKey, block);
        // The Hearth item is drawn as a player head (minecraft-heads.com "Brazier", verified HTTP 200 PNG), so
        // the profile rides on the item as a DEFAULT component: every Hearth, however it is obtained (recipe,
        // /give, creative tab), carries it without any per-stack setup.
        Registry.register(
                BuiltInRegistries.ITEM,
                itemKey,
                new BlockItem(registered, new Item.Properties().setId(itemKey)
                        .component(net.minecraft.core.component.DataComponents.PROFILE, HEARTH_PROFILE.get()))
        );
        return registered;
    }

    private static Block registerTable(String path) {
        ResourceKey<Block> blockKey = ResourceKey.create(Registries.BLOCK, EmberfallMod.id(path));
        ResourceKey<Item> itemKey = ResourceKey.create(Registries.ITEM, EmberfallMod.id(path));
        Block block = new CharacterSelectBlock(BlockBehaviour.Properties.of()
                .setId(blockKey)
                .strength(2.5F, 6.0F));
        Block registered = Registry.register(BuiltInRegistries.BLOCK, blockKey, block);
        Registry.register(
                BuiltInRegistries.ITEM,
                itemKey,
                new BlockItem(registered, new Item.Properties().setId(itemKey)
                        .component(net.minecraft.core.component.DataComponents.PROFILE, TABLE_PROFILE.get()))
        );
        return registered;
    }

    public static void init() {
        // classload trigger
    }
}
