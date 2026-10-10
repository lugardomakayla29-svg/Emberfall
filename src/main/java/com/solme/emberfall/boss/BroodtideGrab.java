package com.solme.emberfall.boss;

/**
 * The Broodtide's Phases and its Grab as pure arithmetic (docs/PLAN_broodtide.md sections 4 and 8 test 5). The entity side only reads these answers, so each
 * rule is proven without a server. No Minecraft types.
 *
 * GRAB, the plan's rules: effects are Slowness and Mining Fatigue ONLY (no damage, no stun, no disarm); AT MOST ONE impulse per grab; the impulse never
 * carries the player into the body or below the floor; the effects clear when the grab ends. Every number below is a PROPOSAL until a playtest.
 */
public final class BroodtideGrab {
    private BroodtideGrab() {}

    // ---- phases ----

    public enum Phase { ONE, TWO, THREE }

    /** Phase 2 starts when health falls to this fraction. PROPOSAL. */
    public static final double PHASE_TWO_AT = 2.0 / 3.0;
    /** Phase 3 starts when health falls to this fraction. PROPOSAL. */
    public static final double PHASE_THREE_AT = 1.0 / 3.0;

    /** The phase for a health fraction (0..1). Boundaries belong to the HARDER phase, so a boss at exactly 2/3 is already in phase 2. */
    public static Phase phaseFor(double fraction) {
        double f = BroodtideRules.clampedFraction(fraction);
        if (f <= PHASE_THREE_AT) {
            return Phase.THREE;
        }
        if (f <= PHASE_TWO_AT) {
            return Phase.TWO;
        }
        return Phase.ONE;
    }

    /** A phase never goes backwards: healing (Brood-Kin left alive at Flood) must not drop the fight into an earlier phase. */
    public static Phase latch(Phase current, double fraction) {
        Phase now = phaseFor(fraction);
        return now.ordinal() > current.ordinal() ? now : current;
    }

    // ---- grab timing ----

    /** Wind-up before the tentacle lands. The plan's floor for telegraphs is 0.9 s. */
    public static final int WINDUP_TICKS = 20;
    /** How long a landed grab holds its effects. */
    public static final int HOLD_TICKS = 60;
    /** Ticks between the start of one grab and the earliest start of the next, by phase. PROPOSAL: faster as the fight goes on. */
    public static int cooldownTicks(Phase p) {
        return p == Phase.ONE ? 200 : p == Phase.TWO ? 150 : 110;
    }
    /** Players one grab may take at once, by phase. PROPOSAL. */
    public static int maxTargets(Phase p) {
        return p == Phase.THREE ? 2 : 1;
    }

    /** A grab may begin only in Ebb (Flood is the armoured, swelling beat), with the cooldown elapsed and no grab already running. */
    public static boolean mayStart(long fightTick, long lastStartTick, Phase p, boolean grabRunning) {
        if (grabRunning || TideClock.stateAt(fightTick) != TideClock.State.EBB) {
            return false;
        }
        // The wind-up must finish inside the same Ebb, or the tentacle would land during Flood.
        if (TideClock.ticksLeft(fightTick) <= WINDUP_TICKS) {
            return false;
        }
        return lastStartTick < 0 || fightTick - lastStartTick >= cooldownTicks(p);
    }

    // ---- reach and the one impulse ----

    /** Reach of the tentacle from the body centre, in blocks. PROPOSAL. */
    public static final double REACH = 14.0;
    /** The pull stops this far from the body centre: the body is size 6, about 2.6 blocks across at scale, so never closer than this. */
    public static final double STOP_RADIUS = 4.5;
    /** Horizontal speed of the single impulse, in blocks per tick. PROPOSAL. */
    public static final double IMPULSE_SPEED = 0.55;
    /** The impulse never has a downward component: pushing a player into the floor is how a pull becomes a clip. */
    public static final double IMPULSE_MIN_Y = 0.0;
    /** A small upward lift so the player is not dragged along the ground. */
    public static final double IMPULSE_LIFT = 0.12;

    /** True if a player at horizontal distance {@code dist} can be grabbed: inside the reach, and not already inside the stop radius. */
    public static boolean inReach(double dist) {
        return dist <= REACH && dist > STOP_RADIUS;
    }

    /**
     * The single impulse as {dx, dy, dz} for a player at (px, pz) pulled toward a body at (bx, bz). Horizontal only, toward the body, with speed capped so the
     * player lands no closer than {@link #STOP_RADIUS}: the speed is cut to the distance still available when that is smaller. Never a negative Y.
     * A player already at or inside the stop radius gets a zero impulse, so a grab cannot push anyone into the body.
     */
    public static double[] impulse(double bx, double bz, double px, double pz) {
        double dx = bx - px, dz = bz - pz;
        double dist = Math.sqrt(dx * dx + dz * dz);
        double room = dist - STOP_RADIUS;
        if (dist < 1.0e-6 || room <= 0.0) {
            return new double[] {0.0, 0.0, 0.0};
        }
        // The velocity applied once travels about speed / (1 - drag) blocks before it dies away; cap the speed so that travel fits inside the room.
        double travelFactor = 1.0 / (1.0 - GROUND_DRAG);
        double speed = Math.min(IMPULSE_SPEED, room / travelFactor);
        return new double[] {dx / dist * speed, Math.max(IMPULSE_MIN_Y, IMPULSE_LIFT), dz / dist * speed};
    }
    /** The horizontal drag a player's velocity keeps each tick on the ground (vanilla 0.546 on ground: friction 0.6 x 0.91). */
    public static final double GROUND_DRAG = 0.546;

    /** Blocks the impulse carries a player horizontally before it dies away (geometric series of the drag). */
    public static double travel(double speed) {
        return speed / (1.0 - GROUND_DRAG);
    }

    // ---- effects ----

    /** The ONLY effects a grab may apply. A check asserts this exact list so a damage or stun effect cannot be added silently. */
    public static final String[] EFFECTS = {"minecraft:slowness", "minecraft:mining_fatigue"};
    /** Effect amplifier (0 = level I). PROPOSAL: slowness II, fatigue I. */
    public static final int SLOWNESS_AMPLIFIER = 1;
    public static final int FATIGUE_AMPLIFIER = 0;

    /** True if the grab on a player started at {@code startTick} is over at {@code now}: the hold elapsed, or the grab was cancelled. */
    public static boolean ended(long startTick, long now, boolean cancelled) {
        return cancelled || now - startTick >= WINDUP_TICKS + HOLD_TICKS;
    }
}
