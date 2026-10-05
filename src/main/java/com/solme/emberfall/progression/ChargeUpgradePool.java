package com.solme.emberfall.progression;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * The 2 charge-granting upgrades sold alongside {@link UpgradePool}'s
 * attribute upgrades: buying these permanently raises how many Tome
 * reroll/banish charges every future run starts with (design goal: avoid
 * pure filler level-ups when the offer pool is dry or unwanted - the
 * player can spend meta-currency, earned across runs, to buy the ability
 * to skip past bad luck instead of just eating it every run).
 *
 * Reroll is intentionally cheaper and goes higher (3 levels) than Banish
 * (2 levels, steeper cost): rerolling is a mild "try again" that can still
 * reoffer the same unwanted Tome, while banishing permanently deletes a
 * Tome from the player's entire run - a strictly stronger effect, priced
 * accordingly.
 */
public final class ChargeUpgradePool {
    public static final List<ChargeUpgradeType> ALL = List.of(
            new ChargeUpgradeType("reroll_mastery", "Reroll Mastery",
                    "+1 Tome reroll per level, every run.",
                    ChargeUpgradeType.ChargeKind.REROLL, 1, 3, 50L),

            new ChargeUpgradeType("banishers_mark", "Banisher's Mark",
                    "+1 Tome banish per level, every run (permanently removes a Tome from that run's offers).",
                    ChargeUpgradeType.ChargeKind.BANISH, 1, 2, 80L)
    );

    private static final Map<String, ChargeUpgradeType> BY_ID =
            ALL.stream().collect(Collectors.toMap(ChargeUpgradeType::id, u -> u));

    private ChargeUpgradePool() {}

    public static ChargeUpgradeType byId(String id) {
        return BY_ID.get(id);
    }
}
