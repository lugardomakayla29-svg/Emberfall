package com.solme.emberfall.tome;

import net.minecraft.server.level.ServerPlayer;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;

/**
 * Tracks how many copies of each Tome a player currently owns, for the
 * current run only (design doc 10.1: build state is in-run, does not
 * persist). Backs both the "avoid offering an already-maxed Tome" weighting
 * (7.4) and the synergy tag threshold count (7.2).
 *
 * Cleanup registry: a Tome's onApply effect (e.g. {@link
 * com.solme.emberfall.tome.TomePool#statTome}) can register a matching
 * teardown action via {@link #addCleanup} the moment it applies something
 * that outlives PlayerBuild's own in-memory map - concretely, a vanilla
 * {@code AttributeInstance} permanent modifier, which is stored on the
 * player entity itself (and so persists in player NBT across runs, unlike
 * this class's own run-scoped map). Found via a live crash: without this,
 * a repeat pick of the same Tome at the same stack index in a *later* run
 * regenerates the exact same deterministic modifier id, and vanilla's
 * AttributeMap throws ("Modifier is already applied on this attribute!")
 * because the *previous* run's copy was never removed. {@link #clear} now
 * runs every registered cleanup before dropping the run's stack-count map,
 * so a run's Tome picks - stat boosts included - actually end when the run
 * does, matching the "in-run only" contract above instead of silently
 * leaking onto the player forever.
 */
public final class PlayerBuild {
    private static final Map<UUID, Map<String, Integer>> owned = new ConcurrentHashMap<>();
    private static final Map<UUID, List<Consumer<ServerPlayer>>> cleanupActions = new ConcurrentHashMap<>();

    private PlayerBuild() {}

    public static void reset(ServerPlayer player) {
        owned.put(player.getUUID(), new HashMap<>());
        // A fresh run's PlayerBuild bookkeeping starts clean too - any
        // cleanup left over from a run that ended without going through
        // clear() (there shouldn't be one, but this is cheap insurance)
        // must not fire twice against a player who has since moved on.
        cleanupActions.remove(player.getUUID());
    }

    /**
     * Registers a teardown action for something a Tome's onApply effect
     * attached to the player that outlives this run-scoped map (see class
     * javadoc) - most commonly removing a permanent AttributeModifier.
     * Runs exactly once, when {@link #clear} ends this player's run.
     */
    public static void addCleanup(ServerPlayer player, Consumer<ServerPlayer> action) {
        cleanupActions.computeIfAbsent(player.getUUID(), k -> new CopyOnWriteArrayList<>()).add(action);
    }

    public static void clear(ServerPlayer player) {
        List<Consumer<ServerPlayer>> actions = cleanupActions.remove(player.getUUID());
        if (actions != null) {
            for (Consumer<ServerPlayer> action : actions) {
                action.accept(player);
            }
        }
        owned.remove(player.getUUID());
    }

    public static int stacksOf(ServerPlayer player, String tomeId) {
        Map<String, Integer> build = owned.get(player.getUUID());
        return build == null ? 0 : build.getOrDefault(tomeId, 0);
    }

    public static Map<String, Integer> allOf(ServerPlayer player) {
        return owned.getOrDefault(player.getUUID(), Map.of());
    }

    /** How many DIFFERENT tomes the player holds. A second copy of a tome is a stack, not a new slot. */
    public static int distinctOwned(ServerPlayer player) {
        return allOf(player).size();
    }

    /** How many tome slots this player has unlocked (1 to 4), bought in the shop with Silver. */
    public static int allowedTomeSlots(ServerPlayer player) {
        var server = player.level().getServer();
        return server == null ? com.solme.emberfall.progression.SlotUnlocks.BASE_SLOTS
                : com.solme.emberfall.progression.SlotUnlocks.get(server)
                        .slots(player.getUUID(), com.solme.emberfall.progression.SlotUnlocks.Kind.TOME);
    }

    /**
     * True if the player may take this tome: they already hold it (it just stacks), or they have a free tome slot.
     * Every tome offer and every grant goes through this one rule.
     */
    public static boolean canTake(ServerPlayer player, String tomeId) {
        return stacksOf(player, tomeId) > 0 || distinctOwned(player) < allowedTomeSlots(player);
    }

    /** Records one more pick of the given Tome, returning the new stack count (1-based). */
    public static int grant(ServerPlayer player, String tomeId) {
        Map<String, Integer> build = owned.computeIfAbsent(player.getUUID(), k -> new HashMap<>());
        int next = build.getOrDefault(tomeId, 0) + 1;
        build.put(tomeId, next);
        return next;
    }

    /**
     * Total STACKS (not distinct Tome ids) of owned Tomes that carry the given tag.
     * Changed from a distinct-id count (v1) to a stack-sum count as part of the
     * tome-refinement pass that removed the pure "does nothing alone - shares the
     * tag" filler Tomes: each tag family now has exactly 2 genuinely-working Tome
     * ids instead of 1 real + 2 dead carriers, so reaching a tag's threshold has
     * to come from total investment (any mix of repeats across those 2) rather
     * than being hard-gated on owning every distinct id in the family.
     */
    public static int tagCount(ServerPlayer player, SynergyTag tag) {
        Map<String, Integer> build = owned.get(player.getUUID());
        if (build == null) {
            return 0;
        }
        int count = 0;
        for (Map.Entry<String, Integer> entry : build.entrySet()) {
            Tome tome = TomePool.byId(entry.getKey());
            if (tome != null && tome.tags().contains(tag)) {
                count += entry.getValue();
            }
        }
        return count;
    }
}
