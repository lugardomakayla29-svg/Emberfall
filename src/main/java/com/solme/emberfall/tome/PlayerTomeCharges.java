package com.solme.emberfall.tome;

import com.solme.emberfall.progression.ChargeUpgradePool;
import com.solme.emberfall.progression.ChargeUpgradeType;
import com.solme.emberfall.progression.PlayerUpgrades;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Per-run Tome Choice reroll/banish charges - in-memory only, exactly like
 * {@link PlayerBuild} (wiped on run start, never persisted), since these
 * are a per-run resource pool, not a standing bonus. The pool SIZE each
 * run is permanent meta-progression though - seeded from
 * {@link com.solme.emberfall.progression.ChargeUpgradePool}'s owned levels
 * (see {@link #reset}) via the currency shop, same "buy it once, it's
 * there every run from now on" pattern as {@link com.solme.emberfall.progression.UpgradeType}.
 *
 * Also owns the per-run "banished Tome ids" set: once banished, a Tome id
 * is filtered out of {@link TomeOfferGenerator}'s candidate pool for the
 * rest of that run - permanent within the run, forgotten on the next one.
 */
public final class PlayerTomeCharges {
    private static final class State {
        int rerollRemaining;
        int banishRemaining;
        /** Gold rerolls bought so far this run; drives the rising price. Charges are separate and free. */
        int goldRerollsBought;
        final Set<String> banishedTomeIds = new HashSet<>();
    }

    private static final Map<UUID, State> state = new HashMap<>();

    private PlayerTomeCharges() {}

    /** Re-seeds this player's charge pool to their full owned amount - call once per run join. */
    public static void reset(ServerPlayer player) {
        MinecraftServer server = player.level().getServer();
        State fresh = new State();
        if (server != null) {
            PlayerUpgrades upgrades = PlayerUpgrades.get(server);
            for (ChargeUpgradeType type : ChargeUpgradePool.ALL) {
                int level = upgrades.getLevel(player.getUUID(), type.id());
                int charges = type.totalChargesAtLevel(level);
                if (type.kind() == ChargeUpgradeType.ChargeKind.REROLL) {
                    fresh.rerollRemaining = charges;
                } else {
                    fresh.banishRemaining = charges;
                }
            }
        }
        state.put(player.getUUID(), fresh);
    }

    /** Drops this player's charge state - call on run leave to avoid an unbounded leak across many runs. */
    public static void clear(ServerPlayer player) {
        state.remove(player.getUUID());
    }

    public static int rerollsRemaining(ServerPlayer player) {
        State s = state.get(player.getUUID());
        return s == null ? 0 : s.rerollRemaining;
    }

    public static int banishesRemaining(ServerPlayer player) {
        State s = state.get(player.getUUID());
        return s == null ? 0 : s.banishRemaining;
    }

    /** Spends one reroll charge. Returns false (no-op) if none remain. */
    public static boolean useReroll(ServerPlayer player) {
        State s = state.get(player.getUUID());
        if (s == null || s.rerollRemaining <= 0) {
            return false;
        }
        s.rerollRemaining--;
        return true;
    }

    /** First gold reroll costs this; every further one this run costs {@link #GOLD_REROLL_STEP} more. */
    public static final int GOLD_REROLL_BASE = 30;
    public static final int GOLD_REROLL_STEP = 30;

    /** Gold the NEXT reroll would cost this run (30, 60, 90, ...), or 0 when a free charge will be used instead. */
    public static int nextGoldRerollPrice(ServerPlayer player) {
        State s = state.get(player.getUUID());
        if (s == null || s.rerollRemaining > 0) {
            return 0;
        }
        return GOLD_REROLL_BASE + GOLD_REROLL_STEP * s.goldRerollsBought;
    }

    /** True when the player holds a free reroll charge (the paid-for perk is always spent before gold). */
    public static boolean hasFreeReroll(ServerPlayer player) {
        return rerollsRemaining(player) > 0;
    }

    /** Records that a gold reroll was actually delivered, so the next one costs more. */
    public static void recordGoldReroll(ServerPlayer player) {
        State s = state.get(player.getUUID());
        if (s != null) {
            s.goldRerollsBought++;
        }
    }

    /** Spends one banish charge and permanently excludes {@code tomeId} for the rest of this run. Returns false if none remain. */
    public static boolean useBanish(ServerPlayer player, String tomeId) {
        State s = state.get(player.getUUID());
        if (s == null || s.banishRemaining <= 0) {
            return false;
        }
        s.banishRemaining--;
        s.banishedTomeIds.add(tomeId);
        return true;
    }

    /** Tome ids permanently excluded from this run's offers so far. Never null. */
    public static Set<String> banishedTomeIds(ServerPlayer player) {
        State s = state.get(player.getUUID());
        return s == null ? Set.of() : s.banishedTomeIds;
    }
}
