package com.solme.emberfall.world;

import java.util.HashMap;
import java.util.Map;

/**
 * Design doc 10.1: meta-currency is earned based on run performance (time
 * survived, bosses defeated). This is the minimal per-slot bookkeeping
 * needed to compute that reward at run-end time - deliberately separate
 * from {@link ArenaInstance} (which describes the pasted structure, not
 * the fight happening inside it) and from {@link RunManager}'s
 * player-membership tracking.
 *
 * Hydra and Devourer defeats are tracked as two separate flags (not one
 * shared "bossDefeated" boolean, which is what this class originally had)
 * since 2026-09-28's two-tier escalation change (see {@link
 * com.solme.emberfall.wave.WaveDirector}'s javadoc: Hydra is now the
 * tier-1 boss, Devourer the automatic tier-2 boss) means a single run can
 * genuinely defeat both bosses and should be credited for each one
 * independently - {@link com.solme.emberfall.progression.RunRewardCalculator}
 * grants a bonus per boss actually cleared, not a flat "a boss died" bit.
 *
 * Known v1 limitation: this is keyed by slot, not by (slot, player), so a
 * player who explicitly leaves and rejoins the same still-active slot
 * (e.g. another player is still inside it) would be credited again for
 * time already rewarded on their first leave. Not guarded against here -
 * every run observed so far is single-player-per-slot, and real
 * multiplayer-aware reward tracking is a bigger design question than this
 * pass's scope.
 */
public final class RunTelemetry {
    private static final Map<Integer, Long> runStartMillis = new HashMap<>();
    private static final Map<Integer, Boolean> hydraDefeated = new HashMap<>();
    private static final Map<Integer, Boolean> devourerDefeated = new HashMap<>();

    private RunTelemetry() {}

    /** Call once, when a slot's arena is first pasted (run start). */
    public static void start(int slot) {
        runStartMillis.put(slot, System.currentTimeMillis());
        hydraDefeated.put(slot, false);
        devourerDefeated.put(slot, false);
    }

    public static void markHydraDefeated(int slot) {
        hydraDefeated.put(slot, true);
    }

    public static void markDevourerDefeated(int slot) {
        devourerDefeated.put(slot, true);
    }

    public static long elapsedSeconds(int slot) {
        Long start = runStartMillis.get(slot);
        if (start == null) {
            return 0L;
        }
        return Math.max(0L, (System.currentTimeMillis() - start) / 1000L);
    }

    public static boolean wasHydraDefeated(int slot) {
        return hydraDefeated.getOrDefault(slot, false);
    }

    public static boolean wasDevourerDefeated(int slot) {
        return devourerDefeated.getOrDefault(slot, false);
    }

    /** Call when a slot's arena is torn down, so telemetry doesn't leak across reuse. */
    public static void clear(int slot) {
        runStartMillis.remove(slot);
        hydraDefeated.remove(slot);
        devourerDefeated.remove(slot);
    }
}
