package com.solme.emberfall.combat;

import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;

/**
 * War Halberd's "Sunder": a harmful effect that weakens the target's own
 * ARMOR/ARMOR_TOUGHNESS attributes (verified pattern straight from vanilla's
 * own {@code MobEffects.WEAKNESS}, which is a plain {@code MobEffect} with
 * an {@code addAttributeModifier} call targeting ATTACK_DAMAGE - Sunder is
 * the same idea aimed at defense instead). {@link MobEffect}'s constructor
 * is `protected` (accessible in-package by vanilla's own MobEffects.java,
 * same as e.g. PoisonMobEffect/HungerMobEffect subclass it) so this mod
 * needs its own trivial subclass to construct one at all - there is no
 * public factory/builder for it in 1.21.11.
 */
public final class SunderMobEffect extends MobEffect {
    public SunderMobEffect(MobEffectCategory category, int color) {
        super(category, color);
    }
}
