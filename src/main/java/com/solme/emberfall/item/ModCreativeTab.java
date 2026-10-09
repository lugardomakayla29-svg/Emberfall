package com.solme.emberfall.item;

import com.solme.emberfall.EmberfallMod;
import com.solme.emberfall.block.ModBlocks;
import net.fabricmc.fabric.api.itemgroup.v1.FabricItemGroup;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

/**
 * The Emberfall creative tab: every obtainable item. Weapons are listed from {@link WeaponPool#ALL}, so a weapon
 * added to the pool later shows up here without touching this class. Every spawnable mob has an egg, built from
 * {@link com.solme.emberfall.entity.MobSpawner}. The Marker block is an internal helper and is deliberately left out.
 * The two bosses need a run's arena, so they have Summoner items (operators, inside a run) instead of eggs; shrines are admin commands.
 */
public final class ModCreativeTab {
    private ModCreativeTab() {}

    public static final ResourceKey<CreativeModeTab> KEY =
            ResourceKey.create(Registries.CREATIVE_MODE_TAB, EmberfallMod.id("emberfall"));

    public static void init() {
        CreativeModeTab tab = FabricItemGroup.builder()
                .title(Component.translatable("itemGroup.emberfall.emberfall"))
                .icon(() -> new ItemStack(ModItems.RIFT_SHARD))
                .displayItems((parameters, output) -> {
                    output.accept(ModBlocks.CHARACTER_TABLE.asItem());
                    output.accept(ModBlocks.CHEST_PAID.asItem());
                    output.accept(ModBlocks.CHEST_FREE.asItem());
                    output.accept(ModBlocks.CHEST_GOLD.asItem());
                    for (WeaponType weapon : WeaponPool.ALL) {
                        Item item = ModItems.itemFor(weapon);
                        if (item != null) {
                            output.accept(item);
                        }
                    }
                    for (Item egg : ModItems.MOB_EGGS.values()) {
                        output.accept(egg);
                    }
                    output.accept(ModItems.GUARDIAN_SUMMONER);
                    output.accept(ModItems.DEVOURER_SUMMONER);
                    output.accept(ModItems.MERCHANT_SUMMONER);
                    output.accept(ModItems.RIFT_SHARD);
                    output.accept(ModItems.EMBER_TESTER_EGG);
                })
                .build();
        Registry.register(BuiltInRegistries.CREATIVE_MODE_TAB, KEY, tab);
    }
}
