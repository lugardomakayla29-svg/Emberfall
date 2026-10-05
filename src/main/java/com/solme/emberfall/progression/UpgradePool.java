package com.solme.emberfall.progression;

import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * The full permanent-upgrade roster sold by the currency shop (see
 * {@link UpgradeType}'s doc for why these are "always on" rather than
 * per-run). 3 upgrades, 5 levels each, one per stat family that's actually
 * meaningful to a run: survivability, damage output, and mobility - the
 * same three axes the weapon system's own numbers (see
 * {@link com.solme.emberfall.item.WeaponPool}) already vary along, so a
 * permanent buy always compounds with whatever weapon/Tome build a run
 * happens to roll.
 *
 * v1 balance numbers - an explicit first pass, not a tuned final balance
 * (same honesty as {@link com.solme.emberfall.item.WeaponPool}'s costs).
 */
public final class UpgradePool {
    public static final List<UpgradeType> ALL = List.of(
            new UpgradeType("vitality", "Vitality",
                    "+1 heart (2 max health) per level.",
                    Attributes.MAX_HEALTH, AttributeModifier.Operation.ADD_VALUE,
                    2.0, 5, 75L),

            new UpgradeType("power", "Power",
                    "+6% attack damage per level.",
                    Attributes.ATTACK_DAMAGE, AttributeModifier.Operation.ADD_MULTIPLIED_BASE,
                    0.06, 5, 100L),

            new UpgradeType("swiftness", "Swiftness",
                    "+5% move speed per level.",
                    Attributes.MOVEMENT_SPEED, AttributeModifier.Operation.ADD_MULTIPLIED_BASE,
                    0.05, 5, 90L)
    );

    private static final Map<String, UpgradeType> BY_ID =
            ALL.stream().collect(Collectors.toMap(UpgradeType::id, u -> u));

    private UpgradePool() {}

    public static UpgradeType byId(String id) {
        return BY_ID.get(id);
    }
}
