package com.solme.emberfall.tome;

import com.solme.emberfall.EmberfallMod;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;

/**
 * Design doc 7.2: "owning 3+ items sharing a tag unlocks a bonus effect
 * specific to that tag threshold... entirely data-driven (a JSON table of
 * tag -> threshold -> effect) so new synergies can be added without new
 * code." v1 keeps that table as a small Java structure rather than an
 * actual JSON asset (no data-loading pipeline exists yet for this mod) but
 * keeps the same shape - adding a new tag's bonus here is a one-line
 * addition, not a new mechanic.
 *
 * Three bonus styles, matching the three ways a tag can pay off:
 *   - A one-time permanent stat grant (Momentum @3) - applied once, guarded
 *     by checking the modifier is already present so re-checking after
 *     every later pick is always safe to call.
 *   - A live per-hit modifier read straight off the current tag count, with
 *     no separate "granted" bookkeeping at all - Fire/Frost/Poison/
 *     Lightning/Explosive/Lifesteal @3 all boost their tag's on-hit Tome
 *     and {@link CombatStats#recompute} just asks {@code tagCount(...) >= 3}
 *     fresh every time it recomputes (which is on every Tome pick, not
 *     every hit - cheap).
 *   - A retroactive grant applied to already-existing state that this class
 *     doesn't own (Summon @3 buffs the player's current companion entities)
 *     - {@link PlayerCompanions} does the actual attribute work, this class
 *     just decides when to call it.
 */
public final class SynergyEffects {
    private SynergyEffects() {}

    /** Call after every Tome grant to apply any newly-qualifying tag-threshold bonuses. */
    public static void checkAndApply(ServerPlayer player) {
        if (PlayerBuild.tagCount(player, SynergyTag.MOMENTUM) >= 3) {
            grantMomentumBonus(player);
        }
        if (PlayerBuild.tagCount(player, SynergyTag.SUMMON) >= 3) {
            PlayerCompanions.applySynergyBonusToAll(player);
        }
        // FIRE/FROST/POISON/LIGHTNING/EXPLOSIVE/LIFESTEAL @3 need no action here -
        // CombatStats.recompute() checks the live tag count itself.
    }

    private static void grantMomentumBonus(ServerPlayer player) {
        AttributeInstance instance = player.getAttribute(Attributes.MOVEMENT_SPEED);
        if (instance == null) {
            return;
        }
        Identifier bonusId = Identifier.fromNamespaceAndPath(EmberfallMod.MOD_ID, "synergy_momentum3_" + player.getUUID());
        if (instance.hasModifier(bonusId)) {
            return; // already granted this run
        }
        instance.addPermanentModifier(new AttributeModifier(bonusId, 0.10, AttributeModifier.Operation.ADD_MULTIPLIED_BASE));
        player.sendSystemMessage(Component.literal(
                "§b§lSYNERGY! §r§bMomentum x3 - an extra surge of speed kicks in."));
    }

    /** Whether the Fire @3 tag bonus (boosted Ember Touch ignite chance/duration) is currently active. */
    public static boolean fireBonusActive(ServerPlayer player) {
        return PlayerBuild.tagCount(player, SynergyTag.FIRE) >= 3;
    }

    /** Whether the Frost @3 tag bonus (boosted Frostbite Fang chill chance/duration/amplifier) is active. */
    public static boolean frostBonusActive(ServerPlayer player) {
        return PlayerBuild.tagCount(player, SynergyTag.FROST) >= 3;
    }

    /** Whether the Poison @3 tag bonus (boosted Venomous Fang poison chance/duration/amplifier) is active. */
    public static boolean poisonBonusActive(ServerPlayer player) {
        return PlayerBuild.tagCount(player, SynergyTag.POISON) >= 3;
    }

    /** Whether the Lightning @3 tag bonus (boosted Static Discharge chain chance/extra target) is active. */
    public static boolean lightningBonusActive(ServerPlayer player) {
        return PlayerBuild.tagCount(player, SynergyTag.LIGHTNING) >= 3;
    }

    /** Whether the Explosive @3 tag bonus (boosted Volatile Rounds detonate chance/radius/damage) is active. */
    public static boolean explosiveBonusActive(ServerPlayer player) {
        return PlayerBuild.tagCount(player, SynergyTag.EXPLOSIVE) >= 3;
    }

    /** Whether the Lifesteal @3 tag bonus (lifesteal heals also grant a brief Absorption shield) is active. */
    public static boolean lifestealBonusActive(ServerPlayer player) {
        return PlayerBuild.tagCount(player, SynergyTag.LIFESTEAL) >= 3;
    }

    /** Whether the Summon @3 tag bonus (companions get +50% health, +2 attack damage) is active. */
    public static boolean summonBonusActive(ServerPlayer player) {
        return PlayerBuild.tagCount(player, SynergyTag.SUMMON) >= 3;
    }
}
