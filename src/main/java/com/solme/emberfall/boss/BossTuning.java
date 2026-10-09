package com.solme.emberfall.boss;

/**
 * The base stat boost both bosses get (owner, 2026-10-09: "Broodtide and Devourer stats as a whole should increase some bit").
 * One number, in one pure place, so a check can prove it and a later balance pass changes a single line. The Boss Curse shrine multiplies ON TOP of
 * this (it is applied after construction), so the two stack and neither is lost.
 *
 * Health and damage rise; movement speed does NOT, because a faster boss is harder to read and dodge, which is a different kind of "harder" than the
 * owner asked for. PROPOSAL: 15%, a visible step that does not change which weapons can win the fight.
 */
public final class BossTuning {
    private BossTuning() {}

    /** Health and damage factor for the base boss. PROPOSAL: 1.15. */
    public static final double BASE_BOOST = 1.15;
    /** The Devourer's health before the boost: its original value, kept so the boost is a visible multiplication of a known number. */
    public static final double DEVOURER_BASE_HEALTH = 260.0;

    /** The Devourer's max health with the boost, rounded to a whole point so the boss bar shows a clean number. */
    public static double devourerHealth() {
        return Math.round(DEVOURER_BASE_HEALTH * BASE_BOOST);
    }

    /** The starting {@code damageScale} of a Devourer before any Boss Curse: the boost alone. */
    public static float devourerDamageScale() {
        return (float) BASE_BOOST;
    }

    /** A Boss Curse multiplier applied on top of the boost: the product, so the curse is never swallowed by the base. */
    public static double withCurse(double curse) {
        return BASE_BOOST * Math.max(1.0, curse);
    }
}
