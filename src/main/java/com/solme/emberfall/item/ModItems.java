package com.solme.emberfall.item;

import com.solme.emberfall.EmberfallMod;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.entity.EquipmentSlotGroup;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.item.component.ItemAttributeModifiers;

import java.util.HashMap;
import java.util.Map;

/**
 * Registers one real {@link Item} per {@link WeaponType} in {@link WeaponPool}.
 * Every weapon is a genuine held item with real vanilla ATTACK_DAMAGE/
 * ATTACK_SPEED attribute modifiers (verified against the actual 1.21.11
 * Item$Properties API - `.sword(ToolMaterial, dmg, speed)` bakes in a
 * literal (3.0, -2.4) pair for every vanilla sword tier, confirmed straight
 * from Items.java's own IRON_SWORD/DIAMOND_SWORD registration - so
 * hand-building the same two attribute modifiers directly here, with
 * weapon-specific numbers, gets identical vanilla behavior: correct
 * animations, sweep particles, and (crucially) the
 * getAttackStrengthScale(0) cooldown gate AutoAttackSystem already relies
 * on) - not just flavor text on a plain Item with no real combat stats.
 *
 * RANGED_* weapons still get an ATTACK_SPEED modifier (drives their fire
 * cadence through the same cooldown gate) but deliberately no
 * ATTACK_DAMAGE modifier - they never melee-attack, so a nonzero melee
 * damage stat would be a misleading tooltip number for a weapon that
 * always deals its damage through {@link WeaponType#rangedDamage} instead
 * (see {@link com.solme.emberfall.combat.AutoAttackSystem}).
 */
public final class ModItems {
    private static final Map<String, Item> ITEM_BY_WEAPON_ID = new HashMap<>();
    private static final Map<Item, WeaponType> WEAPON_BY_ITEM = new HashMap<>();

    private ModItems() {}

    /** Particle-only item: its texture is the Pink Slime's own pink, so break particles made from it are pink, not slime green. */
    public static Item PINK_SLIME_GLOB;
    public static Item BROODTIDE_SUMMONER;
    public static Item DEVOURER_SUMMONER;
    public static Item MERCHANT_SUMMONER;
    public static Item RIFT_SHARD;
    public static Item EMBER_TESTER_EGG;

    /** Mob id to its creative-tab egg, in tab order. */
    public static final Map<String, Item> MOB_EGGS = new java.util.LinkedHashMap<>();

    public static void init() {
        for (WeaponType weapon : WeaponPool.ALL) {
            register(weapon);
        }
        ResourceKey<Item> globKey = ResourceKey.create(Registries.ITEM, EmberfallMod.id("pink_slime_glob"));
        PINK_SLIME_GLOB = Registry.register(BuiltInRegistries.ITEM, globKey, new Item(new Item.Properties().setId(globKey)));
        BROODTIDE_SUMMONER = registerSummoner("broodtide_summoner", BossSummonerItem.Boss.BROODTIDE);
        DEVOURER_SUMMONER = registerSummoner("devourer_summoner", BossSummonerItem.Boss.DEVOURER);
        MERCHANT_SUMMONER = registerSummoner("merchant_summoner", BossSummonerItem.Boss.MERCHANT);
        ResourceKey<Item> riftShardKey = ResourceKey.create(Registries.ITEM, EmberfallMod.id("rift_shard"));
        RIFT_SHARD = Registry.register(BuiltInRegistries.ITEM, riftShardKey,
                new RiftShardItem(new Item.Properties().setId(riftShardKey).rarity(Rarity.RARE)));
        ResourceKey<Item> botEggKey = ResourceKey.create(Registries.ITEM, EmberfallMod.id("ember_tester_egg"));
        EMBER_TESTER_EGG = Registry.register(BuiltInRegistries.ITEM, botEggKey,
                new BotEggItem(new Item.Properties().setId(botEggKey).stacksTo(1).rarity(Rarity.EPIC)));
        for (String mobId : com.solme.emberfall.entity.MobSpawner.RECIPES.keySet()) {
            ResourceKey<Item> key = ResourceKey.create(Registries.ITEM, EmberfallMod.id(mobId + "_egg"));
            Item egg = new MobEggItem(new Item.Properties().setId(key), mobId);
            MOB_EGGS.put(mobId, Registry.register(BuiltInRegistries.ITEM, key, egg));
        }
    }

    public static Item itemFor(WeaponType weapon) {
        return ITEM_BY_WEAPON_ID.get(weapon.id());
    }

    public static Item itemFor(String weaponId) {
        return ITEM_BY_WEAPON_ID.get(weaponId);
    }

    /** Reverse lookup: which WeaponType (if any) a held Item represents. Null for non-weapon items. */
    public static WeaponType weaponFor(Item item) {
        return WEAPON_BY_ITEM.get(item);
    }

    private static Item registerSummoner(String id, BossSummonerItem.Boss boss) {
        ResourceKey<Item> key = ResourceKey.create(Registries.ITEM, EmberfallMod.id(id));
        return Registry.register(BuiltInRegistries.ITEM, key,
                new BossSummonerItem(new Item.Properties().setId(key).stacksTo(1).rarity(Rarity.EPIC), boss));
    }

    private static void register(WeaponType weapon) {
        ResourceKey<Item> itemKey = ResourceKey.create(Registries.ITEM, EmberfallMod.id(weapon.id()));

        ItemAttributeModifiers.Builder attrs = ItemAttributeModifiers.builder();
        if (!weapon.isRanged()) {
            attrs.add(Attributes.ATTACK_DAMAGE,
                    new AttributeModifier(EmberfallMod.id("weapon." + weapon.id() + ".attack_damage"),
                            weapon.meleeDamage(), AttributeModifier.Operation.ADD_VALUE),
                    EquipmentSlotGroup.MAINHAND);
            attrs.add(Attributes.ATTACK_SPEED,
                    new AttributeModifier(EmberfallMod.id("weapon." + weapon.id() + ".attack_speed"),
                            weapon.meleeAttackSpeedModifier(), AttributeModifier.Operation.ADD_VALUE),
                    EquipmentSlotGroup.MAINHAND);
        } else {
            attrs.add(Attributes.ATTACK_SPEED,
                    new AttributeModifier(EmberfallMod.id("weapon." + weapon.id() + ".attack_speed"),
                            weapon.rangedAttackSpeedModifier(), AttributeModifier.Operation.ADD_VALUE),
                    EquipmentSlotGroup.MAINHAND);
        }
        attrs.add(Attributes.ENTITY_INTERACTION_RANGE,
                new AttributeModifier(EmberfallMod.id("weapon." + weapon.id() + ".range"),
                        weapon.range() - 3.0, AttributeModifier.Operation.ADD_VALUE),
                EquipmentSlotGroup.MAINHAND);

        Item item = new Item(new Item.Properties()
                .setId(itemKey)
                .stacksTo(1)
                .rarity(Rarity.UNCOMMON)
                .attributes(attrs.build()));

        Item registered = Registry.register(BuiltInRegistries.ITEM, itemKey, item);
        ITEM_BY_WEAPON_ID.put(weapon.id(), registered);
        WEAPON_BY_ITEM.put(registered, weapon);
    }
}
