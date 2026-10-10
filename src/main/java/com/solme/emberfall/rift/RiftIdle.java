package com.solme.emberfall.rift;

import java.util.ArrayList;
import java.util.List;

/**
 * What an OPEN Rift looks like, every tick, for as long as it stays open. PURE DATA (no Minecraft types, no entities), the same
 * way {@link RiftFx} is for the 5 second opening. {@link RiftFx#opening} stops at {@link RiftFx#OPEN_TICK}; before this class existed
 * nothing drew an open Rift at all, so after the opening it was invisible (owner's real-client report, 2026-10-09).
 *
 * The look, from the owner's picture: a jagged pink-lilac tear with a warm white-orange rim. It must MOVE, so the tear is never the same
 * two ticks in a row:
 * <ul>
 *   <li>RIM: every rim cell glows all the time (a warm dust layer plus a white spark that blinks on and off per cell).</li>
 *   <li>FILL: pink and lilac dust drifts through the body. Which cells are lit changes each pulse, and a slow wave sweeps up the tear.</li>
 *   <li>STREAKS: now and then a rim cell throws a short bright streak sideways, like heat leaving a crack.</li>
 *   <li>SATELLITES: the floating shards shimmer in turn.</li>
 * </ul>
 * Everything is a function of (shape, tick): the same inputs give the same list, so it is provable without a server. The number of particles
 * per tick never exceeds {@link #BUDGET} whatever the shape, so a field of Rifts cannot flood the network.
 *
 * Tested headless. How it LOOKS on a real client is not established: every number below is a PROPOSAL.
 */
public final class RiftIdle {
    private RiftIdle() {}

    /** Hard cap on particles per Rift per tick (RiftRules.BUDGET_PER_TICK is 160 for the busiest opening tick; idle stays far under it). */
    public static final int BUDGET = 70;
    /** The fill pulses on this beat (ticks). Each pulse lights a different third of the body cells. */
    public static final int FILL_BEAT = 4;
    /** A rim cell blinks with this period (ticks); cells are offset so the rim shimmers instead of flashing together. */
    public static final int RIM_PERIOD = 10;
    /** A streak leaves the rim on this beat (ticks), from one cell chosen by tick. */
    public static final int STREAK_BEAT = 6;
    /** One full sweep of the wave up the tear takes this many ticks. */
    public static final int WAVE_PERIOD = 60;
    /** The wave lights every body cell within this many rows of its current height, so it reads as a band, not a single row. */
    public static final int WAVE_BAND = 1;

    /** 1 in this many body cells glows on every tick (a different set each tick). PROPOSAL, unseen. */
    public static final int GLOW_SHARE = 5;

    public static final int DENSITY_RIM = 1;
    public static final int DENSITY_FILL = 1;
    public static final int DENSITY_STREAK = 3;
    public static final int DENSITY_SATELLITE = 2;

    /** Particle keys: the adapter ({@code RiftStage.particle}) maps each to a vanilla particle. */
    public static final String RIM_DUST = "rim_dust";
    public static final String RIM_SPARK = "end_rod";
    public static final String FILL_DUST = "fill_dust";
    public static final String STREAK = "electric_spark";
    public static final String SATELLITE = "end_rod";

    /**
     * The events for ONE tick of an open Rift. {@code tick} is ticks since it opened (any value, it wraps). Only PARTICLE events are ever
     * returned: an idle Rift makes no sound here (the hum is the opening's job and a looping hum is the adapter's), no chat, no push.
     */
    public static List<RiftFx.Event> at(RiftShape.Shape shape, int tick) {
        List<RiftFx.Event> out = new ArrayList<>();
        List<int[]> cells = shape.cells();
        if (cells.isEmpty()) {
            return out;
        }
        List<int[]> rim = shape.rim();
        int height = Math.max(1, shape.height);

        // Rim: warm dust on every rim cell each beat, and a white spark that blinks (a different phase per cell).
        for (int i = 0; i < rim.size(); i++) {
            int[] r = rim.get(i);
            if (!shape.isBody(r[0], r[1])) {
                continue;
            }
            if ((tick + i * 3) % RIM_PERIOD < 2) {
                out.add(new RiftFx.Event(tick, RiftFx.Kind.PARTICLE, RIM_DUST, r[0], r[1], RiftFx.RIM_WARM, 0, DENSITY_RIM));
            }
            if ((tick + i * 7) % RIM_PERIOD == 0) {
                out.add(new RiftFx.Event(tick, RiftFx.Kind.PARTICLE, RIM_SPARK, r[0], r[1], RiftFx.RIM_HOT, 0, DENSITY_RIM));
            }
        }

        // Base glow: EVERY tick a different sixth of the body cells light up, so the tear never drops to its rim alone between beats. Without
        // it the picture pulsed (about 30 particles on the beat tick, 5 to 9 on the three ticks between), which reads as flicker, not as a living tear.
        int glow = 0;
        for (int i = 0; i < cells.size(); i++) {
            int[] c = cells.get(i);
            if (shape.isBody(c[0], c[1]) && (i + tick) % GLOW_SHARE == 0) {
                out.add(new RiftFx.Event(tick, RiftFx.Kind.PARTICLE, FILL_DUST, c[0], c[1], ((i + tick) / GLOW_SHARE) % 2 == 0 ? RiftFx.FILL_PINK : RiftFx.FILL_LILAC, 0, DENSITY_FILL));
                glow++;
            }
        }

        // Fill: on each beat a third of the body cells light up, rotating, plus the cells the wave is passing right now.
        if (tick % FILL_BEAT == 0) {
            int pulse = tick / FILL_BEAT;
            int wave = (int) ((long) (tick % WAVE_PERIOD) * height / WAVE_PERIOD);
            for (int i = 0; i < cells.size(); i++) {
                int[] c = cells.get(i);
                if (!shape.isBody(c[0], c[1])) {
                    continue;
                }
                boolean lit = (i + pulse) % 3 == 0 || Math.abs(c[1] - wave) <= WAVE_BAND;
                if (lit) {
                    int colour = ((i + pulse) % 2 == 0) ? RiftFx.FILL_LILAC : RiftFx.FILL_PINK;
                    out.add(new RiftFx.Event(tick, RiftFx.Kind.PARTICLE, FILL_DUST, c[0], c[1], colour, 0, DENSITY_FILL));
                }
            }
        }

        // Streaks: one rim cell throws a bright streak on the beat; which one changes every time.
        if (tick % STREAK_BEAT == 0 && !rim.isEmpty()) {
            int[] r = rim.get(((tick / STREAK_BEAT) * 5 + 1) % rim.size());
            if (shape.isBody(r[0], r[1])) {
                out.add(new RiftFx.Event(tick, RiftFx.Kind.PARTICLE, STREAK, r[0], r[1], RiftFx.RIM_WARM, 0, DENSITY_STREAK));
            }
        }

        // Satellites: the floating shards shimmer one at a time.
        int sats = 0;
        for (int[] c : cells) {
            if (shape.isSatellite(c[0], c[1])) {
                if ((tick / 5 + sats) % 4 == 0) {
                    out.add(new RiftFx.Event(tick, RiftFx.Kind.PARTICLE, SATELLITE, c[0], c[1], RiftFx.RIM_HOT, 0, DENSITY_SATELLITE));
                }
                sats++;
            }
        }
        return clamp(out);
    }

    /**
     * Never more than {@link #BUDGET} particles in one tick. Streaks and satellites are the rare, MOVING parts, so they are kept first; the
     * rim and fill (which are everywhere) give way to them. Order inside each group is unchanged, so the result is deterministic.
     */
    public static List<RiftFx.Event> clamp(List<RiftFx.Event> events) {
        List<RiftFx.Event> rare = new ArrayList<>();
        List<RiftFx.Event> common = new ArrayList<>();
        for (RiftFx.Event e : events) {
            if (STREAK.equals(e.key) || (SATELLITE.equals(e.key) && e.count == DENSITY_SATELLITE)) {
                rare.add(e);
            } else {
                common.add(e);
            }
        }
        int total = 0;
        List<RiftFx.Event> kept = new ArrayList<>();
        for (List<RiftFx.Event> group : List.of(rare, common)) {
            for (RiftFx.Event e : group) {
                int n = Math.max(1, e.count);
                if (total + n > BUDGET) {
                    continue;
                }
                total += n;
                kept.add(e);
            }
        }
        return kept;
    }

    /** Particles in a list (what the adapter will actually spawn). */
    public static int particleCount(List<RiftFx.Event> events) {
        int n = 0;
        for (RiftFx.Event e : events) {
            if (e.kind == RiftFx.Kind.PARTICLE) {
                n += Math.max(1, e.count);
            }
        }
        return n;
    }
}
