package com.solme.emberfall.bot;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Which players are EmberTester bots, and the pure rules for keeping them off every human's tab list.
 *
 * <p>Every packet a human receives leaves through one method, so the tab-list filter lives in a single mixin on that
 * method and only asks this class two questions: is this UUID a bot, and which entries of a list survive. No vanilla
 * code path (join, leave, latency ticks, game-mode change, chat session) needs its own patch.
 */
public final class BotRoster {
    private static final Set<UUID> BOTS = ConcurrentHashMap.newKeySet();

    private BotRoster() {}

    public static void add(UUID id) {
        BOTS.add(id);
    }

    public static void remove(UUID id) {
        BOTS.remove(id);
    }

    public static boolean isBot(UUID id) {
        return id != null && BOTS.contains(id);
    }

    public static int count() {
        return BOTS.size();
    }

    /** The entries that stay in a tab-list update: everything whose UUID is not a bot. Never null, order kept. */
    public static <T> List<T> humansOnly(List<T> entries, java.util.function.Function<T, UUID> idOf) {
        List<T> kept = new ArrayList<>(entries.size());
        for (T entry : entries) {
            if (!isBot(idOf.apply(entry))) {
                kept.add(entry);
            }
        }
        return kept;
    }

    /** UUIDs that stay in a tab-list removal. */
    public static List<UUID> humanIds(List<UUID> ids) {
        return humansOnly(ids, id -> id);
    }

    /** True when a tab-list update has entries and every one belongs to a bot, so the whole packet is dropped. */
    public static <T> boolean allBots(List<T> entries, java.util.function.Function<T, UUID> idOf) {
        if (entries.isEmpty()) {
            return false;
        }
        for (T entry : entries) {
            if (!isBot(idOf.apply(entry))) {
                return false;
            }
        }
        return true;
    }
}
