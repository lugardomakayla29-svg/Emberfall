package com.solme.emberfall.progression;

import net.minecraft.core.Holder;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;

/**
 * One permanent meta-progression stat upgrade (design doc: the currency
 * shop sells "weapons... or permanent stat upgrades" - the second half of
 * that sentence, alongside {@link com.solme.emberfall.item.WeaponType}'s
 * weapon-side entries). Unlike a Tome (owned only for the current run,
 * wiped on leave - see {@link com.solme.emberfall.tome.PlayerBuild}) or a
 * weapon unlock (a one-time unlock gate you still have to pick/equip),
 * every level of one of these is a standing attribute bonus that's simply
 * always on, in every run and in the overworld, the moment it's bought -
 * see {@link UpgradeEffects}.
 *
 * Tiered rather than one-shot so the shop has real depth to spend currency
 * on repeatedly: each level adds another flat {@code perLevelValue} to the
 * same attribute, up to {@code maxLevel}. Cost climbs linearly per level
 * ({@code baseCost * level}) so early levels are cheap and later ones are
 * a real investment - the same "explicit first-pass balance, not a tuned
 * final number" honesty as {@link com.solme.emberfall.item.WeaponPool}'s
 * weapon costs.
 */
public record UpgradeType(
        String id,
        String displayName,
        String description,
        Holder<Attribute> attribute,
        AttributeModifier.Operation operation,
        double perLevelValue,
        int maxLevel,
        long baseCostPerLevel
) {
    /** Total meta-currency cost to go from {@code currentLevel} to {@code currentLevel + 1}. */
    public long costForNextLevel(int currentLevel) {
        return baseCostPerLevel * (currentLevel + 1L);
    }

    /** This upgrade's total attribute bonus at a given level (0 = none bought yet). */
    public double totalValueAtLevel(int level) {
        return perLevelValue * level;
    }
}
