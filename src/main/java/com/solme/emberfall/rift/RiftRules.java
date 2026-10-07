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
}
