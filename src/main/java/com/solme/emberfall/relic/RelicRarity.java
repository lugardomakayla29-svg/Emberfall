package com.solme.emberfall.relic;

/**
 * The four relic tiers. Base weights are the chance of each tier at Luck 0 (they sum to 100). Luck moves weight
 * from the low tiers toward the high ones but never removes a tier, so a legendary can always drop.
 * The colour is the one used for names, chest glow and the Testificate banner, so a player reads rarity at a glance.
 */
public enum RelicRarity {
    COMMON("Common", 0x9D9D9D, 60.0),
    UNCOMMON("Uncommon", 0x4FA8FF, 28.0),
    RARE("Rare", 0xB266FF, 10.0),
    LEGENDARY("Legendary", 0xFFC247, 2.0);

    private final String label;
    private final int rgb;
    private final double baseWeight;

    RelicRarity(String label, int rgb, double baseWeight) {
        this.label = label;
        this.rgb = rgb;
        this.baseWeight = baseWeight;
    }

    public String label() { return label; }
    public int rgb() { return rgb; }
    public double baseWeight() { return baseWeight; }

    /** The next tier up, or itself for the top tier. */
    public RelicRarity higher() {
        return this == LEGENDARY ? LEGENDARY : values()[ordinal() + 1];
    }
}
