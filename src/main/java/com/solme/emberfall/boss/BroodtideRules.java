package com.solme.emberfall.boss;

/**
 * The Broodtide body's small rules as pure arithmetic, so each can be proven without a server (docs/PLAN_broodtide.md sections 2 and 5).
 * The entity ({@code BroodtideBody}) calls these; a check mutates them. No Minecraft types.
 */
public final class BroodtideRules {
    private BroodtideRules() {}

    /**
     * The jump delay a rooted body returns. Slime's move control stores this, then divides it by 3 when the mob is aggressive (bytecode of
     * Slime$SlimeMoveControl.tick, offsets 111 to 150), and counts down once a tick. It must stay so large that even after the divide it never reaches
     * zero within a fight: {@link #NEVER_JUMPS_TICKS} (one real hour) is the floor the check enforces.
     */
    public static final int ROOTED_JUMP_DELAY = Integer.MAX_VALUE / 2;
    /** One hour of ticks: the longest any run can last is far below this, so a delay above it, even divided by 3, is "never". */
    public static final long NEVER_JUMPS_TICKS = 20L * 60 * 60;

    /** The delay as the move control sees it when the mob is aggressive. */
    public static long aggressiveDelay(int delay) {
        return delay / 3;
    }

    /** True if a body with this delay can hop within one hour, aggressive or not. */
    public static boolean canJumpWithinAnHour(int delay) {
        return aggressiveDelay(delay) < NEVER_JUMPS_TICKS || delay < NEVER_JUMPS_TICKS;
    }

    /** The horizontal motion a rooted body keeps: always zero. The vertical is kept so it still settles on the ground. */
    public static double rootedHorizontal(double current) {
        return 0.0;
    }

    /** Damage the body takes for a hit of {@code amount} at fight tick {@code tick}: the Tide's armour applied once, never zero for a positive hit. */
    public static float damageTaken(float amount, long tick) {
        return damageTaken(amount, tick, 1.0F);
    }

    /**
     * The same, with the party-scaling damage correction ({@code partyFactor}, PartyHealth.applyBoss: 1.0 for a small party, below 1.0 when the scaled
     * health would pass the attribute ceiling). Both factors are applied once and the result stays above zero for a positive hit.
     */
    public static float damageTaken(float amount, long tick, float partyFactor) {
        if (amount <= 0.0F) {
            return amount;
        }
        float f = Math.max(0.0001F, Math.min(1.0F, partyFactor));
        return (float) (amount * TideClock.armourAt(tick) * f);
    }

    /** Health fraction after a resize, clamped to 0..1, so a size change never heals above max or kills. */
    public static double clampedFraction(double fraction) {
        return Math.max(0.0, Math.min(1.0, fraction));
    }

    /** True if a Brood-Kin may be eaten now: the plan caps live Brood-Kin at {@link #KIN_CAP} (section 3, rule 4), so eating needs a free slot. */
    public static boolean mayDevour(int aliveKin) {
        return aliveKin < KIN_CAP;
    }

    /** At most this many Brood-Kin alive at once (plan section 3, rule 4). */
    public static final int KIN_CAP = 6;
}
