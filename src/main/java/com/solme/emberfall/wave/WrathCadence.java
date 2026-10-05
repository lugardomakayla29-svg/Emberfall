package com.solme.emberfall.wave;

/** When each Wrath beat fires, as PURE numbers (no Minecraft types) so the cadence is provable without a server. */
public final class WrathCadence {
    private WrathCadence() {}

    public enum Beat { SCREAM, TERRIFY, THRASH, COMBUST }

    /** Game ticks between two of each beat. All multiples of 20, because the director only ticks the swarm once a second. */
    public static final int SCREAM_EVERY = 120, TERRIFY_EVERY = 160, THRASH_EVERY = 40, COMBUST_EVERY = 200;
    public static final int FIRE_SECONDS = 4;
    public static final double THRASH_SHOVE = 0.6;

    public static int every(Beat beat) {
        return switch (beat) {
            case SCREAM -> SCREAM_EVERY;
            case TERRIFY -> TERRIFY_EVERY;
            case THRASH -> THRASH_EVERY;
            case COMBUST -> COMBUST_EVERY;
        };
    }

    /** Whether this beat fires on this swarm tick. Nothing fires below the cap, or on tick 0. */
    public static boolean due(Beat beat, int tenths, long tick) {
        return FinalSwarm.wrath(tenths) && tick > 0 && tick % every(beat) == 0;
    }
}
