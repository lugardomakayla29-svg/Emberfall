package com.solme.emberfall.boss;

/**
 * The base stat boost both bosses get (owner, 2026-10-09: "35% increase across all stats including hp for both bosses, more menacing").
 * One number, in one pure place, so a check can prove it and a later balance pass changes a single line. The Boss Curse shrine multiplies ON TOP of
 * this (it is applied after construction), so the two stack and neither is lost.
 *
 * Health, damage AND movement speed all rise by the same factor, because the owner said "all stats". A faster boss is harder to dodge, so speed is the stat
 * most worth watching in a playtest. OWNER DECISION: 35%.
 */
public final class BossTuning {
    private BossTuning() {}

    /** The factor for every stat of the base boss. OWNER DECISION: 1.35. */
    public static final double BASE_BOOST = 1.35;
    /** The Devourer's movement speed before the boost: its original value. */
    public static final double DEVOURER_BASE_SPEED = 0.32;
    /** The Devourer's health before the boost: its original value, kept so the boost is a visible multiplication of a known number. */
    public static final double DEVOURER_BASE_HEALTH = 260.0;

    /**
     * The Devourer's max health. OWNER DECISION 2026-10-09: 666, a chosen number, NOT 260 x BASE_BOOST (that would be 351).
     * Its speed and damage still use BASE_BOOST; only health is set by hand.
     */
    public static final double DEVOURER_HEALTH = 666.0;

    /** The Devourer's max health: {@link #DEVOURER_HEALTH}, a whole number so the boss bar reads cleanly. */
    public static double devourerHealth() {
        return DEVOURER_HEALTH;
    }

    /** Broodtide's stats BEFORE the boost. They are the numbers of the Ember Guardian it replaced (600 HP, speed 0.22, 10 contact damage); that class is gone. */
    public static final double BROODTIDE_BASE_HEALTH = 600.0;
    public static final double BROODTIDE_BASE_SPEED = 0.22;
    public static final double BROODTIDE_BASE_DAMAGE = 10.0;

    /** Broodtide's max health with the boost: 810. */
    public static double broodtideHealth() {
        return Math.round(BROODTIDE_BASE_HEALTH * BASE_BOOST);
    }

    /** Broodtide's contact damage with the boost: 13.5. */
    public static double broodtideDamage() {
        return BROODTIDE_BASE_DAMAGE * BASE_BOOST;
    }

    /** Broodtide's movement speed with the boost. The body is rooted so it never walks; kept so the rule "all stats" has no exception. */
    public static double broodtideSpeed() {
        return BROODTIDE_BASE_SPEED * BASE_BOOST;
    }

    /** The Devourer's movement speed with the boost. */
    public static double devourerSpeed() {
        return DEVOURER_BASE_SPEED * BASE_BOOST;
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
