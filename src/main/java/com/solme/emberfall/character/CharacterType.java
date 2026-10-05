package com.solme.emberfall.character;

import net.minecraft.core.Holder;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;

/**
 * One playable Character (design doc Section 5): a fixed, unique starting
 * weapon (not freely pickable by anyone playing a different Character -
 * this is what replaced {@link com.solme.emberfall.item.WeaponChoiceManager}'s
 * old "pick any unlocked weapon" starting screen), a starting stat spread
 * (HP/speed bias), and a separate passive trait on a third attribute so
 * the stat spread and the passive read as two distinct numbers rather
 * than the same two attributes doing double duty.
 *
 * The roster is one Character per {@link com.solme.emberfall.item.WeaponType} (8 now; it began as 5,
 * before Gravechain, Spectral Sickles and Ashen Beacon were added).
 * The paragraph below records the original 5-character scope cut:
 *
 * Originally 5 Characters, one per existing {@link com.solme.emberfall.item.WeaponType}
 * - not yet the design doc's full 8-12 launch roster. That's a deliberate,
 * honest scope cut for this pass, not an oversight: a genuinely
 * *different* 6th+ Character needs its own new weapon (Section 5: "a
 * starting weapon unique, not available to other characters"), and
 * authoring new weapons is separate, bigger work than wiring up the
 * Character system itself. Mapping the 5 that already exist 1:1 onto 5
 * Characters proves the whole system end-to-end - selection persists,
 * locks in a weapon, applies a real stat spread and passive - without
 * also taking on new-weapon-design risk in the same change.
 *
 * All three modifiers use real, already-mechanically-meaningful vanilla
 * attributes (MAX_HEALTH, MOVEMENT_SPEED, plus one of ARMOR/
 * ARMOR_TOUGHNESS/ATTACK_SPEED/KNOCKBACK_RESISTANCE per character) - never
 * a cosmetic-only stat with no actual effect anywhere in the codebase
 * (e.g. LUCK, which nothing here reads), per the "no half-baked features"
 * standing instruction. Same {@code Holder<Attribute>}/Operation/value
 * shape as {@link com.solme.emberfall.progression.UpgradeType} on purpose
 * - this is the same kind of attribute-modifier bonus, just applied for
 * the run's duration instead of permanently.
 *
 * Unlock conditions are design doc Section 10.2's quest/advancement layer,
 * which doesn't exist yet - so, like {@link com.solme.emberfall.item.WeaponPool#defaultUnlocked()}
 * before the currency shop existed, every Character ships unlocked by
 * default for now. {@link CharacterPool#defaultUnlocked()} is the one
 * seam later Advancement-gating hooks into.
 */
public record CharacterType(
        String id,
        String displayName,
        String description,
        String weaponId,
        double maxHealthMultiplierDelta,
        double movementSpeedMultiplierDelta,
        Holder<Attribute> passiveAttribute,
        AttributeModifier.Operation passiveOperation,
        double passiveValue,
        String passiveDescription,
        boolean unlockedByDefault
) {
}
