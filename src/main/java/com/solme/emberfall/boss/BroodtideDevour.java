package com.solme.emberfall.boss;

/**
 * The Broodtide's Devour as pure arithmetic (docs/PLAN_broodtide.md section 3). The entity side only reads these answers, so each rule is proven without a server.
 * No Minecraft types.
 *
 * <p>The beat: in EBB an arm reaches a horde mob (the wind-up is a telegraph), pulls it into the body and HIDES it there; in FLOOD the body swells and SPITS every swallowed
 * mob out, each returning as a Brood-Kin. The same mob comes back (no new entity type), so the Brood-Kin cap is the cap on mobs that were eaten and are still alive.
 *
 * <p>Every number is a PROPOSAL until a playtest. The mechanics (hide and return with AI, aggro and team intact) were measured live in Prototype A.
 */
public final class BroodtideDevour {
    private BroodtideDevour() {}

    /** The reach beat before the arm lands. Equal to the Grab's wind-up: the plan's floor for a telegraph is 0.9 s. */
    public static final int WINDUP_TICKS = BroodtideGrab.WINDUP_TICKS;
    /** At most this many Brood-Kin alive at once, counting those currently swallowed. The boss cannot flood the arena. */
    public static final int BROOD_KIN_CAP = 6;
    /** How far from the body an arm can reach a mob to eat. Matches the longest arm reach in the arm plan. */
    public static final double REACH = BroodtideArmPlan.HUNT_REACH;
    /** A mob swallowed this long ago may be spat out; a Flood arriving sooner waits for it, so a mob is never spat the instant it is eaten. */
    public static final int MIN_HIDDEN_TICKS = 30;
    /** A swallowed mob is spat out no later than this, even with no Flood (a safety bound so a mob can never stay hidden forever). */
    public static final int MAX_HIDDEN_TICKS = TideClock.CYCLE_TICKS;
    /** Ticks between the start of one eat and the earliest start of the next, by phase. PROPOSAL: faster as the fight goes on. */
    public static int cooldownTicks(BroodtideGrab.Phase p) {
        return p == BroodtideGrab.Phase.ONE ? 120 : p == BroodtideGrab.Phase.TWO ? 90 : 60;
    }
    /** Brood-Kin hit points are the eaten mob's own, times this. PROPOSAL. */
    public static final double KIN_HEALTH_FACTOR = 1.5;
    /** What the boss heals for each Brood-Kin still alive when a Flood ends, as a fraction of its maximum health. Small and visible. PROPOSAL. */
    public static final double HEAL_PER_KIN = 0.005;
    /** Killing a Brood-Kin within this many ticks of its spit-out refunds a little to the killer. PROPOSAL. */
    public static final int REFUND_WINDOW_TICKS = 60;

    /** An eat may begin only in Ebb, with the cooldown elapsed, a free Brood-Kin slot, and no eat already running. The wind-up must end inside the same Ebb. */
    public static boolean mayStartEat(long fightTick, long lastEatStart, BroodtideGrab.Phase p, boolean eatRunning, int kinAlive) {
        if (eatRunning || kinAlive >= BROOD_KIN_CAP) {
            return false;
        }
        if (TideClock.stateAt(fightTick) != TideClock.State.EBB) {
            return false;
        }
        if (lastEatStart >= 0 && fightTick - lastEatStart < cooldownTicks(p)) {
            return false;
        }
        return TideClock.ticksLeft(fightTick) > WINDUP_TICKS;
    }

    /**
     * The entity-type ids the Devour may eat in v1: ONLY the two types Prototype A proved come back from hiding with their AI, aggro and team intact (the plan, section 3
     * item 7). Anything else stays uneaten until it has been proven the same way; do not widen this list without a live check for the new type.
     */
    public static final java.util.Set<String> EDIBLE_TYPES = java.util.Set.of("emberfall:horde_zombie", "emberfall:horde_spitter");

    /** Whether an entity-type id is on the v1 allow-list. A null or unknown id is never edible. */
    public static boolean isEdibleType(String typeId) {
        return typeId != null && EDIBLE_TYPES.contains(typeId);
    }

    /** Whether a mob may be chosen to eat: alive, a horde mob (not a boss, an arm, the Testificate or another Brood-Kin), and within reach of the body. */
    public static boolean isEdible(boolean alive, boolean isHorde, boolean alreadyKin, boolean alreadySwallowed, double distanceToBody) {
        return alive && isHorde && !alreadyKin && !alreadySwallowed && distanceToBody <= REACH && distanceToBody >= 0.0;
    }

    /**
     * The pull: where a swallowed mob moves to as the arm retracts. It slides from where it was caught to the body's centre over the wind-up, easing out, and never ends
     * below the floor or outside the body. {@code t} is 0 at the catch and 1 when it arrives; the result is the fraction of the way (0..1).
     */
    public static double pullFraction(double t) {
        double c = Math.max(0.0, Math.min(1.0, t));
        double inv = 1.0 - c;
        return 1.0 - inv * inv;
    }

    /** True when a swallowed mob should be spat out now: a Flood has begun after it has been hidden long enough, or it has been hidden for the maximum. */
    public static boolean shouldSpit(long fightTick, long swallowedAt) {
        long hidden = fightTick - swallowedAt;
        if (hidden < 0) {
            return false;
        }
        if (hidden >= MAX_HIDDEN_TICKS) {
            return true;
        }
        return hidden >= MIN_HIDDEN_TICKS && TideClock.stateAt(fightTick) == TideClock.State.FLOOD;
    }

    /** The Brood-Kin's maximum health: the eaten mob's own times {@link #KIN_HEALTH_FACTOR}, never below the mob's own. */
    public static double kinHealth(double mobMaxHealth) {
        return Math.max(mobMaxHealth, mobMaxHealth * KIN_HEALTH_FACTOR);
    }

    /** The boss's heal when a Flood ends with {@code kinAlive} Brood-Kin still alive, capped so the heal can never undo a phase. Returns hit points. */
    public static double floodHeal(int kinAlive, double bossMaxHealth) {
        int n = Math.max(0, Math.min(kinAlive, BROOD_KIN_CAP));
        return n * HEAL_PER_KIN * Math.max(0.0, bossMaxHealth);
    }

    /** True when a kill is early enough to refund the killer: within {@link #REFUND_WINDOW_TICKS} of the spit-out. */
    public static boolean refundsKill(long ticksSinceSpit) {
        return ticksSinceSpit >= 0 && ticksSinceSpit <= REFUND_WINDOW_TICKS;
    }
}
