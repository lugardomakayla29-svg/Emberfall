package com.solme.emberfall.relic;

import net.minecraft.server.level.ServerPlayer;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * A player's relics for the CURRENT run only (stacks never carry over; only unlocks do, see {@link RelicUnlocks}).
 * Same contract as {@code PlayerBuild}: {@link #reset} when a run starts, {@link #clear} when it ends. {@link #clear}
 * also undoes everything {@link RelicEffects} attached to the player entity (attribute modifiers live in the player's
 * NBT and would otherwise leak into the next run, the exact crash the tome system hit).
 *
 * Also holds the run's paid-chest counter, because the price is per player and must reset with the run.
 */
public final class PlayerRelics {
    private PlayerRelics() {}

    private static final Map<UUID, Map<String, Integer>> OWNED = new ConcurrentHashMap<>();
    private static final Map<UUID, Integer> CHESTS_OPENED = new ConcurrentHashMap<>();

    public static void reset(ServerPlayer player) {
        clear(player);
        OWNED.put(player.getUUID(), new LinkedHashMap<>());
        CHESTS_OPENED.put(player.getUUID(), 0);
    }

    public static void clear(ServerPlayer player) {
        RelicEffects.detach(player);
        RelicDefenceEvents.clear(player);
        RelicRegenSystem.clear(player);
        OWNED.remove(player.getUUID());
        CHESTS_OPENED.remove(player.getUUID());
    }

    public static boolean active(ServerPlayer player) {
        return OWNED.containsKey(player.getUUID());
    }

    public static int stacks(ServerPlayer player, String relicId) {
        Map<String, Integer> m = OWNED.get(player.getUUID());
        return m == null ? 0 : m.getOrDefault(relicId, 0);
    }

    /** A copy, safe to iterate while relics are added. */
    public static Map<String, Integer> all(ServerPlayer player) {
        Map<String, Integer> m = OWNED.get(player.getUUID());
        return m == null ? Map.of() : new HashMap<>(m);
    }

    public static int totalStacks(ServerPlayer player) {
        int n = 0;
        for (int v : all(player).values()) {
            n += v;
        }
        return n;
    }

    /**
     * Gives one stack. Returns false (and changes nothing) when the player is not in a run, the id is unknown, or the
     * relic is already at its stack cap. Re-derives every relic effect afterwards.
     */
    public static boolean give(ServerPlayer player, String relicId) {
        Relic relic = RelicPool.byId(relicId);
        Map<String, Integer> m = OWNED.get(player.getUUID());
        if (relic == null || m == null || m.getOrDefault(relicId, 0) >= relic.maxStacks()) {
            return false;
        }
        m.merge(relicId, 1, Integer::sum);
        RelicEffects.recompute(player);
        return true;
    }

    /** Removes one stack (Microwave sacrifice). Returns false when the player has none. */
    public static boolean take(ServerPlayer player, String relicId) {
        Map<String, Integer> m = OWNED.get(player.getUUID());
        if (m == null || m.getOrDefault(relicId, 0) <= 0) {
            return false;
        }
        if (m.merge(relicId, -1, Integer::sum) <= 0) {
            m.remove(relicId);
        }
        RelicEffects.recompute(player);
        return true;
    }

    public static int chestsOpened(ServerPlayer player) {
        return CHESTS_OPENED.getOrDefault(player.getUUID(), 0);
    }

    /**
     * Counts one PAID chest opening toward the price. Ember Ledger stops the count from rising, which is how the
     * price stops rising. A free opening (Key proc, elite/boss/shrine chest) never calls this at all.
     */
    public static void addChestOpened(ServerPlayer player) {
        if (stacks(player, "ember_ledger") > 0) {
            return;
        }
        CHESTS_OPENED.merge(player.getUUID(), 1, Integer::sum);
    }

    /** Price of this player's next PAID chest, from the counter above. */
    public static int nextChestPrice(ServerPlayer player) {
        return RelicMath.chestPrice(chestsOpened(player));
    }
}
