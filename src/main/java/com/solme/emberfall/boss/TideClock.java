package com.solme.emberfall.boss;

/**
 * The Broodtide's Tide as pure arithmetic (docs/PLAN_broodtide.md section 2 and test 4): the boss alternates EBB (the body can be hit freely) and
 * FLOOD (the body is armoured, never immune). The state is a FUNCTION of the tick the fight has run, not a counter that is stepped, so it cannot
 * drift: tick 100000 gives the same answer however many ticks were skipped on the way. No Minecraft types, so it is proven without a server.
 *
 * Numbers are PROPOSALS from the plan: Ebb 14 s, Flood 9 s. The plan marks the Ebb length "wait for DPS", so only these constants change when the
 * damage table is measured. The armour factor is the plan's 0.35 and is applied to damage taken (a hit of 10 does 3.5 in Flood), it is never 0.
 */
public final class TideClock {
    private TideClock() {}

    public enum State { EBB, FLOOD }

    /** Ebb lasts this many ticks: 14 s. PROPOSAL. */
    public static final int EBB_TICKS = 14 * 20;
    /** Flood lasts this many ticks: 9 s. PROPOSAL. */
    public static final int FLOOD_TICKS = 9 * 20;
    /** One full Tide: Ebb then Flood. */
    public static final int CYCLE_TICKS = EBB_TICKS + FLOOD_TICKS;
    /** Damage multiplier in Flood. The plan's value; exactly this, and never 0 (no silent immunity). */
    public static final double FLOOD_ARMOUR = 0.35;
    /** Damage multiplier in Ebb. */
    public static final double EBB_ARMOUR = 1.0;

    /** The state at {@code tick} ticks into the fight. The fight starts in Ebb at tick 0. A negative tick is treated as 0. */
    public static State stateAt(long tick) {
        long t = Math.max(0L, tick);
        return (t % CYCLE_TICKS) < EBB_TICKS ? State.EBB : State.FLOOD;
    }

    /** The damage multiplier at {@code tick}: {@link #FLOOD_ARMOUR} in Flood, {@link #EBB_ARMOUR} in Ebb. */
    public static double armourAt(long tick) {
        return stateAt(tick) == State.FLOOD ? FLOOD_ARMOUR : EBB_ARMOUR;
    }

    /** How many ticks remain in the current state, counting this tick: 1 on the last tick of a state, so the next call changes state. */
    public static int ticksLeft(long tick) {
        long t = Math.max(0L, tick);
        long into = t % CYCLE_TICKS;
        return (int) (into < EBB_TICKS ? EBB_TICKS - into : CYCLE_TICKS - into);
    }

    /** True exactly on the first tick of a new state (never on tick 0, the fight's own start). This is the beat the renderer and sound key off. */
    public static boolean changesAt(long tick) {
        if (tick <= 0L) {
            return false;
        }
        return stateAt(tick) != stateAt(tick - 1);
    }

    /** The tick of the {@code n}th state change (n = 1 is the first Ebb to Flood). Lets a test place changes without stepping. */
    public static long changeTick(int n) {
        if (n <= 0) {
            return 0L;
        }
        long full = (n - 1) / 2;
        boolean toFlood = (n % 2) == 1;
        return full * CYCLE_TICKS + (toFlood ? EBB_TICKS : CYCLE_TICKS);
    }
}
