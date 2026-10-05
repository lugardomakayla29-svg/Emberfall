package com.solme.emberfall.relic;

import com.solme.emberfall.EmberfallMod;
import net.minecraft.core.Holder;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;

/**
 * Applies the parts of a player's relics that live on the player ENTITY (max health, movement speed). Everything else
 * is read on demand through {@link #stats}. Each attribute uses ONE fixed-id transient modifier per player that is
 * replaced on every recompute, so it can never stack by accident, never reaches the player's saved NBT, and
 * {@link #detach} removes it exactly (the same approach {@code CharacterEffects} uses).
 */
public final class RelicEffects {
    private RelicEffects() {}

    private static Identifier healthId(ServerPlayer p) {
        return Identifier.fromNamespaceAndPath(EmberfallMod.MOD_ID, "relic_max_health_" + p.getUUID());
    }

    private static Identifier speedId(ServerPlayer p) {
        return Identifier.fromNamespaceAndPath(EmberfallMod.MOD_ID, "relic_speed_" + p.getUUID());
    }

    private static Identifier attackSpeedId(ServerPlayer p) {
        return Identifier.fromNamespaceAndPath(EmberfallMod.MOD_ID, "relic_attack_speed_" + p.getUUID());
    }

    /** This player's relic numbers, derived fresh from their current stacks. */
    public static RelicStats stats(ServerPlayer player) {
        return new RelicStats(PlayerRelics.all(player));
    }

    /** Re-derives the entity-side effects after any stack change. */
    public static void recompute(ServerPlayer player) {
        RelicStats s = stats(player);
        float healthBefore = player.getHealth();
        double oldMax = player.getMaxHealth();
        set(player, Attributes.MAX_HEALTH, healthId(player), s.bonusMaxHealth(), AttributeModifier.Operation.ADD_VALUE);
        set(player, Attributes.MOVEMENT_SPEED, speedId(player), s.speedMultiplierDelta(), AttributeModifier.Operation.ADD_MULTIPLIED_BASE);
        set(player, Attributes.ATTACK_SPEED, attackSpeedId(player), RelicTempo.attackSpeedBonus(s), AttributeModifier.Operation.ADD_MULTIPLIED_BASE);
        double newMax = player.getMaxHealth();
        // Gaining max health also gives that health now (otherwise the new hearts arrive empty); losing it clamps.
        if (newMax > oldMax) {
            player.setHealth((float) Math.min(newMax, healthBefore + (newMax - oldMax)));
        } else if (player.getHealth() > newMax) {
            player.setHealth((float) newMax);
        }
    }

    /** Removes every entity-side effect. Safe to call when nothing is attached. */
    public static void detach(ServerPlayer player) {
        remove(player, Attributes.MAX_HEALTH, healthId(player));
        remove(player, Attributes.MOVEMENT_SPEED, speedId(player));
        remove(player, Attributes.ATTACK_SPEED, attackSpeedId(player));
        if (player.getHealth() > player.getMaxHealth()) {
            player.setHealth(player.getMaxHealth());
        }
    }

    private static void set(ServerPlayer player, Holder<Attribute> attribute, Identifier id, double amount, AttributeModifier.Operation op) {
        AttributeInstance instance = player.getAttribute(attribute);
        if (instance == null) {
            return;
        }
        instance.removeModifier(id);
        if (amount != 0.0) {
            instance.addTransientModifier(new AttributeModifier(id, amount, op));
        }
    }

    private static void remove(ServerPlayer player, Holder<Attribute> attribute, Identifier id) {
        AttributeInstance instance = player.getAttribute(attribute);
        if (instance != null) {
            instance.removeModifier(id);
        }
    }
}
