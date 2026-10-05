package com.solme.emberfall.progression;

/**
 * A permanent meta-progression upgrade that grants extra per-run Tome
 * Choice charges (reroll/banish - see {@link com.solme.emberfall.tome.PlayerTomeCharges})
 * rather than a live attribute modifier like {@link UpgradeType}. Kept as
 * its own small record instead of shoehorning a "no attribute" case into
 * UpgradeType: the two are genuinely different shapes (a standing stat
 * bonus that's always active vs. a per-run resource pool that's re-granted
 * fresh at the start of every run), and UpgradeType's attribute/operation
 * fields would be meaningless dead weight here.
 *
 * Reuses {@link PlayerUpgrades}' existing generic {@code id -> level}
 * storage (it's already keyed by arbitrary string id with no attribute
 * assumptions) and the currency shop's existing {@code OpenShopPayload.UpgradeEntry}
 * card shape (id/displayName/description/level/maxLevel/nextCost) - so
 * these show up as ordinary upgrade rows in the shop with zero client-side
 * changes, see {@link ShopManager}.
 */
public record ChargeUpgradeType(
        String id,
        String displayName,
        String description,
        ChargeKind kind,
        int perLevelCharges,
        int maxLevel,
        long baseCostPerLevel
) {
    public enum ChargeKind { REROLL, BANISH }

    /** Total meta-currency cost to go from {@code currentLevel} to {@code currentLevel + 1}. */
    public long costForNextLevel(int currentLevel) {
        return baseCostPerLevel * (currentLevel + 1L);
    }

    /** Total per-run charges granted at a given owned level (0 = none bought yet). */
    public int totalChargesAtLevel(int level) {
        return perLevelCharges * level;
    }
}
