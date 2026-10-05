package com.solme.emberfall.combat;

import com.solme.emberfall.EmberfallMod;
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;

/**
 * Registers Emberfall's own {@link net.minecraft.world.effect.MobEffect}s -
 * currently just "sunder" for the War Halberd (see {@link SunderMobEffect}).
 * Follows the exact same {@code Registry.register(BuiltInRegistries.X, key, ...)}
 * pattern already established by {@code ModEntities}/{@code ModItems}.
 */
public final class ModEffects {
    /**
     * -3.0 ARMOR and -1.0 ARMOR_TOUGHNESS per amplifier level (flat
     * ADD_VALUE, same operation vanilla WEAKNESS uses on ATTACK_DAMAGE -
     * verified against MobEffects.WEAKNESS's own bytecode). Amplifier is
     * scaled automatically by vanilla's own AttributeTemplate the same way
     * every other leveled effect (Strength II, Weakness II, ...) already
     * is - not something this mod re-implements.
     */
    public static final Holder<MobEffect> SUNDER = register("sunder",
            new SunderMobEffect(MobEffectCategory.HARMFUL, 0x5C4A3A)
                    .addAttributeModifier(Attributes.ARMOR, EmberfallMod.id("effect.sunder"), -3.0,
                            AttributeModifier.Operation.ADD_VALUE)
                    .addAttributeModifier(Attributes.ARMOR_TOUGHNESS, EmberfallMod.id("effect.sunder_toughness"), -1.0,
                            AttributeModifier.Operation.ADD_VALUE));

    private ModEffects() {}

    private static Holder<MobEffect> register(String path, MobEffect effect) {
        ResourceKey<MobEffect> key = ResourceKey.create(Registries.MOB_EFFECT, EmberfallMod.id(path));
        return Registry.registerForHolder(BuiltInRegistries.MOB_EFFECT, key, effect);
    }

    /** No-op call site purely to force this class (and its static SUNDER field) to class-load at startup. */
    public static void init() {}
}
