package com.solme.emberfall.rift;

import com.solme.emberfall.hub.GateRules;

/**
 * The Rift's rules as PURE numbers (no Minecraft types), so each one is provable without a server. The Rift replaces the Ember Hearth
 * hub: a portal a player OPENS with a Rift Shard anywhere, instead of a 9x9 hub they must build on flat ground.
 *
 * The party and gate rules are the proven {@link GateRules} ones and are reused, never copied, so the Rift and the old gate cannot drift:
 * a deliberate click or step-in, a 3 s countdown that a move cancels, a return BESIDE the portal, and a lockout after a run.
 */
public final class RiftRules {
    private RiftRules() {}

    /** Ticks a Rift stays open with nobody having entered it: 10 minutes. After that it closes and the ground is restored. */
    public static final int IDLE_TICKS = 20 * 60 * 10;
    /** Ticks between the run ending and the Rift closing, so the returning party is not standing in a collapsing portal. */
    public static final int CLOSE_DELAY_TICKS = 20 * 5;
    /** The largest party one Rift can send (the same cap as the Expedition Gate). */
    public static final int MAX_PARTY = 10;
    /** Shards one Rift costs. */
    public static final int SHARDS_PER_RIFT = 1;
    /** Distance (blocks) a player may be from the Rift to step in or click it: the gate's reach, reused. */
    public static final double REACH = GateRules.REACH;
    /** Countdown seconds, reused from the gate. */
    public static final int COUNTDOWN_SECONDS = GateRules.COUNTDOWN_SECONDS;
    /** Lockout ticks after a run, reused from the gate. */
    public static final int LOCKOUT_TICKS = GateRules.LOCKOUT_TICKS;

    /** True while an open Rift nobody entered must close: it has been open {@code openTicks} and holds {@code waiting} players. */
    public static boolean idleExpired(long openTicks, int waiting) {
        return waiting <= 0 && openTicks >= IDLE_TICKS;
    }

    /** True once the run has ended long enough ago that the Rift may close. A negative value means the run has not ended. */
    public static boolean closeDue(long sinceRunEndTicks) {
        return sinceRunEndTicks >= CLOSE_DELAY_TICKS;
    }

    /** Whether one more player may join the party: the party already holds {@code size} bodies. */
    public static boolean canJoin(int size) {
        return size >= 0 && size < MAX_PARTY;
    }

    /** Whether a player may step in: close enough, not locked out after a run, and the party has room. */
    public static boolean canEnter(double distance, long sinceRunEndTicks, int partySize) {
        return distance <= REACH && !GateRules.lockedOut(sinceRunEndTicks) && canJoin(partySize);
    }

    /** The reason a step-in is refused, or null if it is allowed. Checked in this order so the message is the first thing that is wrong. */
    public static String refusal(double distance, long sinceRunEndTicks, int partySize) {
        if (distance > REACH) {
            return "too far from the Rift";
        }
        if (GateRules.lockedOut(sinceRunEndTicks)) {
            return "the Rift is still settling";
        }
        if (!canJoin(partySize)) {
            return "the party is full";
        }
        return null;
    }

    /** Whether the player has a shard to open a Rift with. */
    public static boolean canOpen(int shardsHeld) {
        return shardsHeld >= SHARDS_PER_RIFT;
    }

    // ---- Natural event (step 2). Every number below is a PROPOSAL: the design gives the idea, not the values. ----------------

    /** Ticks between natural-Rift rolls for one player: once a minute. */
    public static final int NATURAL_CHECK_TICKS = 20 * 60;
    /** Each roll succeeds with chance 1 in this many, so a Rift tears open near a given player about once per 30 minutes of play. */
    public static final int NATURAL_ODDS = 30;
    /** After a Rift opens near a player, no natural roll counts for them for this long (20 minutes), so two never come back to back. */
    public static final int NATURAL_COOLDOWN_TICKS = 20 * 60 * 20;
    /** No two Rifts may be closer than this many blocks. */
    public static final double MIN_RIFT_SPACING = 48.0;
    /** A natural Rift opens at least this far from the player, so it is seen opening and not on top of them, and no farther than the render range, or the player would not see it open. */
    public static final double NATURAL_MIN_DISTANCE = 16.0;
    public static final double NATURAL_MAX_DISTANCE = 32.0;

    /** True when a natural roll is due: {@code sinceLastRollTicks} has reached the interval. A negative value means no roll has happened yet. */
    public static boolean naturalRollDue(long sinceLastRollTicks) {
        return sinceLastRollTicks >= NATURAL_CHECK_TICKS;
    }

    /** True when a roll lands: {@code roll} is a uniform integer in [0, NATURAL_ODDS) and 0 is the winning face. */
    public static boolean naturalRollWins(int roll) {
        return roll == 0;
    }

    /** True while the player is still cooling off after a Rift opened near them. A negative value means none has opened. */
    public static boolean inNaturalCooldown(long sinceLastRiftTicks) {
        return sinceLastRiftTicks >= 0 && sinceLastRiftTicks < NATURAL_COOLDOWN_TICKS;
    }

    /** True when a spot is far enough from the nearest existing Rift. Pass Double.POSITIVE_INFINITY when there is none. */
    public static boolean spacingOk(double distanceToNearestRift) {
        return distanceToNearestRift >= MIN_RIFT_SPACING;
    }

    /** True when the chosen spot is a legal distance from the player the natural Rift is meant for. */
    public static boolean naturalDistanceOk(double distanceToPlayer) {
        return distanceToPlayer >= NATURAL_MIN_DISTANCE && distanceToPlayer <= NATURAL_MAX_DISTANCE;
    }

    /** Whether a Rift may open at a spot: not inside an active run's arena, spaced from other Rifts, and with open air to draw in. */
    public static boolean placementOk(boolean insideActiveRun, double distanceToNearestRift, boolean hasOpenAir) {
        return !insideActiveRun && spacingOk(distanceToNearestRift) && hasOpenAir;
    }

    /** The reason a spot is refused, or null if it is allowed. Same order as {@link #placementOk}, so the first wrong thing is named. */
    public static String placementRefusal(boolean insideActiveRun, double distanceToNearestRift, boolean hasOpenAir) {
        if (insideActiveRun) {
            return "inside an active run";
        }
        if (!spacingOk(distanceToNearestRift)) {
            return "too close to another Rift";
        }
        if (!hasOpenAir) {
            return "no open air";
        }
        return null;
    }

    // ---- Particle budget (step 2). A Rift is drawn with particles only, so the cost is the particle count. -----------------

    /** Players farther than this (blocks) from a Rift get no particles from it: vanilla only sends particles within 32 blocks anyway. */
    public static final double RENDER_RANGE = 32.0;
    /** The most particles one Rift may spawn in one tick, however many players are near. */
    public static final int BUDGET_PER_TICK = 160;
    /** The most particles an idle, open Rift spends per tick when a player is in range (it hums, it does not blaze). */
    public static final int IDLE_PER_TICK = 40;

    /** True when a player at this distance should be shown the Rift. Inclusive at the range. */
    public static boolean inRenderRange(double distance) {
        return distance >= 0 && distance <= RENDER_RANGE;
    }

    /**
     * How many particles the Rift may spawn this tick: 0 when nobody is in range (an idle Rift in an empty world costs nothing), otherwise
     * the wanted count clamped to the per-tick budget. It never exceeds {@link #BUDGET_PER_TICK}, whatever is asked, and never goes negative.
     */
    public static int particlesThisTick(int wanted, int playersInRange) {
        if (playersInRange <= 0 || wanted <= 0) {
            return 0;
        }
        return Math.min(wanted, BUDGET_PER_TICK);
    }

    /** The idle hum's share: {@link #IDLE_PER_TICK} when someone is in range, else 0, and always inside the budget. */
    public static int idleParticles(int playersInRange) {
        return particlesThisTick(IDLE_PER_TICK, playersInRange);
    }
}
