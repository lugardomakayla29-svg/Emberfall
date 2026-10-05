package com.solme.emberfall.character;

import com.solme.emberfall.EmberfallMod;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;

/**
 * Applies (and cleans up) a Character's stat spread + passive as real
 * attribute modifiers on the player's entity - same
 * remove-then-conditionally-add-back shape as
 * {@link com.solme.emberfall.progression.UpgradeEffects}, so calling
 * {@link #apply} again (e.g. after switching Characters) always ends up
 * correct rather than stacking a second copy alongside the old one.
 *
 * Deliberately run-scoped, not permanent: applied by
 * {@link com.solme.emberfall.world.RunManager#joinPlayer} and removed by
 * {@link com.solme.emberfall.world.RunManager#leavePlayer}, matching how
 * {@link com.solme.emberfall.tome.PlayerBuild}/{@link com.solme.emberfall.tome.CombatStats}
 * already treat every other run-only bonus - a Character's bonus is part
 * of "how this run plays," not a standing account-wide buff the way a
 * bought {@link com.solme.emberfall.progression.UpgradeType} is.
 */
public final class CharacterEffects {
    private CharacterEffects() {}

    /** Applies {@code character}'s stat spread + passive to this player's live attributes. */
    public static void apply(ServerPlayer player, CharacterType character) {
        setModifier(player, Attributes.MAX_HEALTH, maxHealthId(player),
                character.maxHealthMultiplierDelta(), AttributeModifier.Operation.ADD_MULTIPLIED_BASE);
        setModifier(player, Attributes.MOVEMENT_SPEED, speedId(player),
                character.movementSpeedMultiplierDelta(), AttributeModifier.Operation.ADD_MULTIPLIED_BASE);
        AttributeInstance passiveInstance = player.getAttribute(character.passiveAttribute());
        if (passiveInstance != null) {
            passiveInstance.removeModifier(passiveId(player));
            passiveInstance.addTransientModifier(new AttributeModifier(
                    passiveId(player), character.passiveValue(), character.passiveOperation()));
        }
    }

    /** Removes every Character modifier this player might currently have, from any Character. */
    public static void clear(ServerPlayer player) {
        removeIfPresent(player, Attributes.MAX_HEALTH, maxHealthId(player));
        removeIfPresent(player, Attributes.MOVEMENT_SPEED, speedId(player));
        // The passive can live on any of several different attributes
        // depending on which Character it was - clear the id off all of
        // them rather than tracking which one was last active.
        for (var attribute : new net.minecraft.core.Holder[] {
                Attributes.ARMOR, Attributes.ARMOR_TOUGHNESS, Attributes.ATTACK_SPEED, Attributes.KNOCKBACK_RESISTANCE
        }) {
            @SuppressWarnings("unchecked")
            var holder = (net.minecraft.core.Holder<net.minecraft.world.entity.ai.attributes.Attribute>) attribute;
            removeIfPresent(player, holder, passiveId(player));
        }
    }

    private static void setModifier(ServerPlayer player, net.minecraft.core.Holder<net.minecraft.world.entity.ai.attributes.Attribute> attribute,
                                     Identifier id, double value, AttributeModifier.Operation operation) {
        AttributeInstance instance = player.getAttribute(attribute);
        if (instance == null) {
            return;
        }
        instance.removeModifier(id);
        if (value != 0.0) {
            instance.addTransientModifier(new AttributeModifier(id, value, operation));
        }
    }

    private static void removeIfPresent(ServerPlayer player, net.minecraft.core.Holder<net.minecraft.world.entity.ai.attributes.Attribute> attribute, Identifier id) {
        AttributeInstance instance = player.getAttribute(attribute);
        if (instance != null) {
            instance.removeModifier(id);
        }
    }

    private static Identifier maxHealthId(ServerPlayer player) {
        return Identifier.fromNamespaceAndPath(EmberfallMod.MOD_ID, "character_max_health_" + player.getUUID());
    }

    private static Identifier speedId(ServerPlayer player) {
        return Identifier.fromNamespaceAndPath(EmberfallMod.MOD_ID, "character_speed_" + player.getUUID());
    }

    private static Identifier passiveId(ServerPlayer player) {
        return Identifier.fromNamespaceAndPath(EmberfallMod.MOD_ID, "character_passive_" + player.getUUID());
    }
}
