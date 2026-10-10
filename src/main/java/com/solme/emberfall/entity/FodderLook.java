package com.solme.emberfall.entity;

import net.minecraft.core.component.DataComponents;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.DyedItemColor;

/**
 * What the three fodder types with custom heads wear and how big they are (owner design, 2026-10-10): the worn armour matches the head so the head does not
 * look pasted on, and each type has its own size, with a veteran always larger than the normal one of its own type.
 * <ul>
 *   <li>Shieldbearer: a full set of leather dyed the rust of the Undead Knight helmet.</li>
 *   <li>Charger: ONE piece, a leather chestplate dyed the red of the Elite Zombie head. Bigger.</li>
 *   <li>Spitter: a full chainmail set (the grey of its metal jaw). Smaller.</li>
 * </ul>
 * The armour is for looks only: the armour POINTS it would add are cancelled by a modifier, so the damage these mobs take is exactly what it was before.
 */
public final class FodderLook {
    private FodderLook() {}

    /** Mean colour of the Undead Knight helmet overlay, sampled from the head skin's pixels. */
    public static final int RUST = rgb(102, 73, 80);
    /** Mean colour of the red parts of the Elite Zombie head skin. */
    public static final int CHARGER_RED = rgb(119, 43, 43);

    /** Display scale per type: {normal, veteran}. */
    public static final double[] SHIELDBEARER_SCALE = {1.00, 1.15};
    public static final double[] CHARGER_SCALE = {1.15, 1.30};
    public static final double[] SPITTER_SCALE = {0.85, 0.95};

    private static final Identifier SCALE_ID = Identifier.fromNamespaceAndPath("emberfall", "fodder_look_scale");
    private static final Identifier ARMOR_CANCEL_ID = Identifier.fromNamespaceAndPath("emberfall", "fodder_look_armor_cancel");
    private static final Identifier TOUGHNESS_CANCEL_ID = Identifier.fromNamespaceAndPath("emberfall", "fodder_look_toughness_cancel");

    public static int rgb(int r, int g, int b) {
        return (r << 16) | (g << 8) | b;
    }

    public static double scaleFor(double[] pair, boolean veteran) {
        return veteran ? pair[1] : pair[0];
    }

    public static ItemStack dyed(net.minecraft.world.item.Item item, int color) {
        ItemStack stack = new ItemStack(item);
        stack.set(DataComponents.DYED_COLOR, new DyedItemColor(color));
        return stack;
    }

    private static void wearPiece(Mob mob, EquipmentSlot slot, ItemStack stack) {
        mob.setItemSlot(slot, stack);
        mob.setDropChance(slot, 0.0F);   // worn for looks, never loot
    }

    /** Full leather set in one colour. */
    public static void dressShieldbearer(Mob mob) {
        wearPiece(mob, EquipmentSlot.CHEST, dyed(Items.LEATHER_CHESTPLATE, RUST));
        wearPiece(mob, EquipmentSlot.LEGS, dyed(Items.LEATHER_LEGGINGS, RUST));
        wearPiece(mob, EquipmentSlot.FEET, dyed(Items.LEATHER_BOOTS, RUST));
        cancelArmorPoints(mob);
    }

    /** One piece: the chestplate. */
    public static void dressCharger(Mob mob) {
        wearPiece(mob, EquipmentSlot.CHEST, dyed(Items.LEATHER_CHESTPLATE, CHARGER_RED));
        cancelArmorPoints(mob);
    }

    /** Full chainmail set (its own grey, undyed). The head slot keeps the custom skull. */
    public static void dressSpitter(Mob mob) {
        wearPiece(mob, EquipmentSlot.CHEST, new ItemStack(Items.CHAINMAIL_CHESTPLATE));
        wearPiece(mob, EquipmentSlot.LEGS, new ItemStack(Items.CHAINMAIL_LEGGINGS));
        wearPiece(mob, EquipmentSlot.FEET, new ItemStack(Items.CHAINMAIL_BOOTS));
        cancelArmorPoints(mob);
    }

    /**
     * Worn armour adds its points to the ARMOR attribute, but only on the mob's next tick, so the attribute cannot be measured right after dressing.
     * The points are read from the worn pieces themselves instead (their own attribute modifiers, available at once) and exactly that much is taken away.
     * The mob's armour therefore stays at its base value and the damage it takes is unchanged. Toughness is cancelled the same way.
     */
    private static void cancelArmorPoints(Mob mob) {
        cancel(mob, Attributes.ARMOR, ARMOR_CANCEL_ID);
        cancel(mob, Attributes.ARMOR_TOUGHNESS, TOUGHNESS_CANCEL_ID);
    }

    /** The total a set of worn pieces adds to one attribute, read from the items. */
    public static double wornTotal(Mob mob, net.minecraft.core.Holder<net.minecraft.world.entity.ai.attributes.Attribute> attribute) {
        double[] total = {0.0};
        for (EquipmentSlot slot : new EquipmentSlot[] {EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET}) {
            mob.getItemBySlot(slot).forEachModifier(slot, (holder, modifier) -> {
                if (holder.equals(attribute) && modifier.operation() == AttributeModifier.Operation.ADD_VALUE) {
                    total[0] += modifier.amount();
                }
            });
        }
        return total[0];
    }

    private static void cancel(Mob mob, net.minecraft.core.Holder<net.minecraft.world.entity.ai.attributes.Attribute> attribute, Identifier id) {
        AttributeInstance instance = mob.getAttribute(attribute);
        if (instance == null) {
            return;
        }
        instance.removeModifier(id);
        double worn = wornTotal(mob, attribute);
        if (worn > 0.0) {
            instance.addPermanentModifier(new AttributeModifier(id, -worn, AttributeModifier.Operation.ADD_VALUE));
        }
    }

    /** Sets the mob's size. Safe to call again when a mob becomes a veteran (it replaces the old modifier). */
    public static void applyScale(Mob mob, double[] pair, boolean veteran) {
        AttributeInstance scale = mob.getAttribute(Attributes.SCALE);
        if (scale == null) {
            return;
        }
        scale.removeModifier(SCALE_ID);
        double target = scaleFor(pair, veteran);
        scale.addPermanentModifier(new AttributeModifier(SCALE_ID, target - scale.getBaseValue(), AttributeModifier.Operation.ADD_VALUE));
    }
}
