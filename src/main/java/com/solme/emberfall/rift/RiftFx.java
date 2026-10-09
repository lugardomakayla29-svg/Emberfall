package com.solme.emberfall.rift;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * The Rift's opening and closing as PURE DATA: an ordered list of timed events built from a {@link RiftShape.Shape}. Nothing here
 * touches Minecraft. A server adapter walks the list and turns each event into particles and sounds (and a chat line); because the
 * list is data, its timing, order, particle count per tick and the fact that it can only ever ask for particles, sounds and chat
 * (never an entity) are all provable without a server.
 *
 * Built to the design's 5 second opening (docs/design/RIFT_EXPEDITION.md). Every time and count below is a PROPOSAL, and the
 * look and sound CANNOT be judged headless: this class only guarantees the schedule, not that it looks or sounds good.
 *
 * Coordinates are shape cells: x to the right, y up, (0, 0) the bottom-left of the box. The adapter places and rotates them.
 */
public final class RiftFx {
    private RiftFx() {}

    /** The opening lasts 5 seconds; at this tick the Rift is OPEN and players may enter. */
    public static final int OPEN_TICK = 100;
    /** Closing is the same animation, faster: it takes a third of the time. */
    public static final int CLOSE_TICKS = 34;

    // The beats of the design, in ticks (20 per second).
    public static final int T_POINT = 0;       // bright point, drone, tremor, chat warning
    public static final int T_CRACK_START = 10; // 0.5 s: the crack starts growing
    public static final int T_CRACK_END = 40;   // 2.0 s: it reaches the whole outline
    public static final int T_FLARE = 40;       // 2.0 s: rim flares, boom, dust ring
    /** The flare is spread over this many ticks (so it ends before the fill starts at T_FILL_START). */
    public static final int FLARE_SPREAD = 8;
    public static final int T_FILL_START = 50;  // 2.5 s: the fill floods in
    public static final int T_FILL_END = 80;    // 4.0 s: the fill is complete
    public static final int T_PULSE = 80;       // 4.0 s: the push, then the idle hum

    /** What an event asks the adapter to do. There is deliberately NO kind that spawns an entity. */
    public enum Kind {
        /** One particle at a cell. */
        PARTICLE,
        /** A sound at the Rift's centre. */
        SOUND,
        /** A chat line to everyone in range. */
        CHAT,
        /** A gentle outward push on entities near the Rift (no damage); the adapter applies it. */
        PUSH
    }

    /** One timed event. {@code key} is a plain name (a particle id, a sound id, a chat key), {@code x, y} are cell coordinates. */
    public static final class Event {
        public final int tick;
        public final Kind kind;
        public final String key;
        public final int x;
        public final int y;
        /** Sounds: volume and pitch. Particles: colour as 0xRRGGBB (0 when the particle has no colour). Others: 0. */
        public final float a;
        public final float b;
        /** PARTICLE events: how many particles the adapter spawns for this cell (the density). Other kinds: 0. */
        public final int count;

        Event(int tick, Kind kind, String key, int x, int y, float a, float b) {
            this(tick, kind, key, x, y, a, b, 0);
        }

        public Event(int tick, Kind kind, String key, int x, int y, float a, float b, int count) {
            this.tick = tick;
            this.kind = kind;
            this.key = key;
            this.x = x;
            this.y = y;
            this.a = a;
            this.b = b;
            this.count = count;
        }

        @Override
        public String toString() {
            return tick + ":" + kind + ":" + key + "@" + x + "," + y;
        }
    }

    // Particles the adapter spawns per cell, by role. PROPOSAL: sized so the busiest tick stays well inside RiftRules.BUDGET_PER_TICK.
    public static final int DENSITY_POINT = 24;
    public static final int DENSITY_CRACK = 6;
    public static final int DENSITY_FLARE = 7;
    public static final int DENSITY_DUST = 48;
    public static final int DENSITY_FILL = 5;
    public static final int DENSITY_SATELLITE = 8;
    /**
     * Warm dust under each rim particle. {@code end_rod}, {@code electric_spark} and {@code dust_plume} are vanilla particles that carry NO colour
     * (measured: 87% of the opening's particles), so the warm rim colour was silently dropped and the rim showed plain white. A dust particle does
     * take a colour, so one thin dust layer under each rim particle gives the rim its warm tint while the white streak above it is unchanged.
     * PROPOSAL: 1 per rim cell per beat. At seeds 0..4 this adds 274 particles to 1212, which is +22.6%, inside the +25% cap Koda set.
     */
    public static final int DENSITY_RIM_DUST = 1;

    // Colours, 0xRRGGBB. PROPOSAL, from the picture's pink-lilac fill and warm white-orange rim.
    public static final int RIM_WARM = 0xFFB070;
    public static final int RIM_HOT = 0xFFE9C8;
    public static final int FILL_LILAC = 0xC79BFF;
    public static final int FILL_PINK = 0xFF9BD0;
    /** The warm white-orange of the rim dust, 0xRRGGBB. The design asks for a warm white-orange rim (RIFT_EXPEDITION.md); it reuses RIM_WARM, so the rim has ONE warm colour. */
    public static final int RIM_DUST = RIM_WARM;

    /**
     * The opening, for a shape: every event from tick 0 to {@link #OPEN_TICK}, sorted by tick. Deterministic: the same shape gives the
     * same list. Never empty for a non-empty shape.
     */
    public static List<Event> opening(RiftShape.Shape shape) {
        List<Event> out = new ArrayList<>();
        if (shape.cells().isEmpty()) {
            return out;
        }
        int[] c = centre(shape);

        // t=0: a bright point at the centre, a low drone, a chat warning.
        out.add(new Event(T_POINT, Kind.PARTICLE, "end_rod", c[0], c[1], RIM_HOT, 0, DENSITY_POINT));
        out.add(new Event(T_POINT, Kind.SOUND, "rift_drone", c[0], c[1], 0.8f, 0.5f));
        out.add(new Event(T_POINT, Kind.CHAT, "rift_tearing", 0, 0, 0, 0));

        // t=10..40: the crack grows OUTWARD along the rim, nearest cells first, each rim cell lit exactly once, a crackle every few.
        // Only the BODY's rim cracks and flares: satellites float clear of the tear and appear later, at the fill beat.
        List<int[]> rim = new ArrayList<>();
        for (int[] r : shape.rim()) {
            if (shape.isBody(r[0], r[1])) {
                rim.add(r);
            }
        }
        rim.sort(Comparator.comparingDouble((int[] r) -> dist(r, c)).thenComparingInt(r -> r[0]).thenComparingInt(r -> r[1]));
        int n = rim.size();
        for (int i = 0; i < n; i++) {
            int tick = T_CRACK_START + (int) ((long) i * (T_CRACK_END - T_CRACK_START) / n);
            int[] r = rim.get(i);
            out.add(new Event(tick, Kind.PARTICLE, "electric_spark", r[0], r[1], RIM_WARM, 0, DENSITY_CRACK));
            out.add(new Event(tick, Kind.PARTICLE, "rim_dust", r[0], r[1], RIM_DUST, 0, DENSITY_RIM_DUST));
            if (i % 4 == 0) {
                out.add(new Event(tick, Kind.SOUND, "rift_crackle", r[0], r[1], 0.5f, 1.0f + (i % 3) * 0.15f));
            }
        }

        // t=40: the rim flares white-orange on every rim cell at once, a deep boom, a dust ring.
        // The flare sweeps over FLARE_SPREAD ticks (in the same outward order as the crack) so no single tick carries the whole rim:
        // the peak is bounded by the shape of the schedule, not by luck.
        for (int i = 0; i < n; i++) {
            int[] r = rim.get(i);
            int tick = T_FLARE + (int) ((long) i * FLARE_SPREAD / n);
            out.add(new Event(tick, Kind.PARTICLE, "end_rod", r[0], r[1], RIM_HOT, 0, DENSITY_FLARE));
            out.add(new Event(tick, Kind.PARTICLE, "rim_dust", r[0], r[1], RIM_DUST, 0, DENSITY_RIM_DUST));
        }
        out.add(new Event(T_FLARE, Kind.SOUND, "rift_boom", c[0], c[1], 1.0f, 0.5f));
        out.add(new Event(T_FLARE, Kind.PARTICLE, "dust_ring", c[0], 0, RIM_WARM, 0, DENSITY_DUST));

        // t=50..80: the fill floods in from the centre outward, every non-rim body cell lit exactly once.
        List<int[]> fill = new ArrayList<>();
        for (int[] cell : shape.cells()) {
            if (shape.isBody(cell[0], cell[1]) && !isRim(rim, cell)) {
                fill.add(cell);
            }
        }
        fill.sort(Comparator.comparingDouble((int[] r) -> dist(r, c)).thenComparingInt(r -> r[0]).thenComparingInt(r -> r[1]));
        int m = fill.size();
        for (int i = 0; i < m; i++) {
            int tick = T_FILL_START + (int) ((long) i * (T_FILL_END - T_FILL_START) / Math.max(1, m));
            int[] f = fill.get(i);
            out.add(new Event(tick, Kind.PARTICLE, "glow", f[0], f[1], (i % 2 == 0) ? FILL_LILAC : FILL_PINK, 0, DENSITY_FILL));
        }
        // Satellites pop out as the fill arrives.
        for (int[] cell : shape.cells()) {
            if (shape.isSatellite(cell[0], cell[1])) {
                out.add(new Event(T_FILL_START + 10, Kind.PARTICLE, "end_rod", cell[0], cell[1], RIM_HOT, 0, DENSITY_SATELLITE));
            }
        }

        // t=80: the push, then the idle hum begins. t=100: open.
        out.add(new Event(T_PULSE, Kind.PUSH, "rift_push", c[0], c[1], 0, 0));
        out.add(new Event(T_PULSE, Kind.SOUND, "rift_hum", c[0], c[1], 0.6f, 0.8f));
        out.add(new Event(OPEN_TICK, Kind.CHAT, "rift_open", 0, 0, 0, 0));

        out.sort(Comparator.comparingInt((Event e) -> e.tick));
        return out;
    }

    /**
     * The closing: the same schedule, played backwards and in {@link #CLOSE_TICKS}. Built from the opening so the two cannot drift:
     * each opening event at tick t happens at {@code (OPEN_TICK - t) * CLOSE_TICKS / OPEN_TICK}, so the last thing to appear is the
     * first to go. Chat keys are swapped for the closing line, and the push is dropped.
     */
    public static List<Event> closing(RiftShape.Shape shape) {
        List<Event> out = new ArrayList<>();
        for (Event e : opening(shape)) {
            if (e.kind == Kind.PUSH) {
                continue;
            }
            if (e.kind == Kind.CHAT) {
                if (e.key.equals("rift_tearing")) {
                    out.add(new Event(CLOSE_TICKS, Kind.CHAT, "rift_collapsed", 0, 0, 0, 0));
                }
                continue;
            }
            int tick = (int) ((long) (OPEN_TICK - e.tick) * CLOSE_TICKS / OPEN_TICK);
            out.add(new Event(tick, e.kind, e.key, e.x, e.y, e.a, e.b, e.count));
        }
        out.add(new Event(0, Kind.CHAT, "rift_closing", 0, 0, 0, 0));
        out.sort(Comparator.comparingInt((Event e) -> e.tick));
        return out;
    }

    /** Events at exactly this tick, in list order. */
    public static List<Event> at(List<Event> events, int tick) {
        List<Event> out = new ArrayList<>();
        for (Event e : events) {
            if (e.tick == tick) {
                out.add(e);
            }
        }
        return out;
    }

    /** How many PARTICLES (not events: each event carries a density) are spawned at this tick. The per-tick budget is checked against this. */
    public static int particlesAt(List<Event> events, int tick) {
        int n = 0;
        for (Event e : events) {
            if (e.tick == tick && e.kind == Kind.PARTICLE) {
                n += e.count;
            }
        }
        return n;
    }

    /** The busiest tick's particle count over the whole list. */
    public static int peakParticles(List<Event> events) {
        int peak = 0;
        for (Event e : events) {
            peak = Math.max(peak, particlesAt(events, e.tick));
        }
        return peak;
    }

    static int[] centre(RiftShape.Shape s) {
        long sx = 0;
        long sy = 0;
        int n = 0;
        for (int x = 0; x < s.width; x++) {
            for (int y = 0; y < s.height; y++) {
                if (s.isBody(x, y)) {
                    sx += x;
                    sy += y;
                    n++;
                }
            }
        }
        if (n == 0) {
            return new int[] {s.width / 2, s.height / 2};
        }
        return new int[] {(int) Math.round((double) sx / n), (int) Math.round((double) sy / n)};
    }

    private static double dist(int[] a, int[] c) {
        double dx = a[0] - c[0];
        double dy = a[1] - c[1];
        return Math.sqrt(dx * dx + dy * dy);
    }

    private static boolean isRim(List<int[]> rim, int[] cell) {
        for (int[] r : rim) {
            if (r[0] == cell[0] && r[1] == cell[1]) {
                return true;
            }
        }
        return false;
    }
}
