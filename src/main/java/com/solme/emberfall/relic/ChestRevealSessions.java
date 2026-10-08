package com.solme.emberfall.relic;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * The server's memory of which chest reveals are currently open, one per player (GAME_PLAN row 2.6, server half). Pure, no Minecraft types, so the rule
 * a forged packet must not break can be proven without a server: a CLOSE is honoured only if that player has an open reveal AND the id it names is
 * the id the server issued. A client can say "close", it can never say "give me a different prize": the close carries no answer.
 *
 * The server decides the result BEFORE opening a reveal (see {@link ChestOpening#open}); this class only records that a reveal was shown, so it
 * can be closed once and cannot be closed by someone it was not shown to. Time is a server tick passed in, so there is no clock and it is deterministic.
 *
 * Every number is a PROPOSAL. Nothing here was seen or heard: no screen exists.
 */
public final class ChestRevealSessions {
    /** An open reveal that is never closed (the client crashed or ignored it) is dropped after this many ticks: the reveal itself is 140 ticks, so 30 s is generous. */
    public static final long MAX_OPEN_TICKS = 20L * 30L;

    /** What the server remembers about one open reveal. */
    public record Open(int id, String tier, String item, long seed, long openedAt) {}

    private final Map<UUID, Open> open = new HashMap<>();
    private int nextId = 1;

    /**
     * Opens a reveal for a player and returns the record to send. Any reveal that player already had open is REPLACED (a player sees one at a time), so a
     * stale id from the old one no longer closes anything. Ids start at 1 and only increase, so an id is never reused within a run. A null or empty tier
     * or item is refused with an {@link IllegalArgumentException}: a reveal with no answer must not be sent.
     */
    public Open open(UUID player, String tier, String item, long seed, long now) {
        if (player == null) {
            throw new IllegalArgumentException("player is null");
        }
        if (tier == null || tier.isEmpty() || item == null || item.isEmpty()) {
            throw new IllegalArgumentException("a reveal needs a tier and an item");
        }
        Open o = new Open(nextId++, tier, item, seed, now);
        open.put(player, o);
        return o;
    }

    /**
     * Handles a close packet. True ONLY if this player has an open reveal, the id matches it, and it has not expired; that reveal is then removed so a
     * second close of the same id is ignored. False for: a player with no open reveal, a wrong id, an old id, a repeat close, or an expired reveal.
     * A false result changes nothing.
     */
    public boolean close(UUID player, int id, long now) {
        if (player == null) {
            return false;
        }
        Open o = open.get(player);
        if (o == null || o.id() != id) {
            return false;
        }
        if (expired(o, now)) {
            // Expired: it is gone, but a late close is not an honoured close.
            open.remove(player);
            return false;
        }
        open.remove(player);
        return true;
    }

    /** True if this player has a reveal that is open and not expired at {@code now}. */
    public boolean isOpen(UUID player, long now) {
        Open o = open.get(player);
        return o != null && !expired(o, now);
    }

    /** The open reveal for a player, or null if none or expired. */
    public Open get(UUID player, long now) {
        Open o = open.get(player);
        return o != null && !expired(o, now) ? o : null;
    }

    /** Drops every expired reveal; returns how many were dropped. The server calls this occasionally so abandoned reveals do not pile up. */
    public int sweep(long now) {
        int before = open.size();
        open.values().removeIf(o -> expired(o, now));
        return before - open.size();
    }

    /** How many reveals are open and not expired. */
    public int count(long now) {
        int n = 0;
        for (Open o : open.values()) {
            if (!expired(o, now)) {
                n++;
            }
        }
        return n;
    }

    /** How many entries are STORED, expired or not. Only {@link #sweep} and {@link #forget} lower it, so it is what a test reads to prove they ran. */
    public int size() {
        return open.size();
    }

    /** Forgets one player (they left or died). */
    public void forget(UUID player) {
        open.remove(player);
    }

    /** Forgets everyone: a run ended, so no reveal may leak into the next one. Ids keep counting up, so an old id still cannot close a new reveal. */
    public void clear() {
        open.clear();
    }

    /** Expired when it has been open for MAX_OPEN_TICKS or more. A clock that goes backwards does not expire anything. */
    private static boolean expired(Open o, long now) {
        return now >= o.openedAt() && now - o.openedAt() >= MAX_OPEN_TICKS;
    }
}
