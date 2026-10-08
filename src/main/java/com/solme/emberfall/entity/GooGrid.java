package com.solme.emberfall.entity;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * The Broodtide's goo as DATA: a grid of cells on the arena floor, no blocks, so terrain is never touched (docs/PLAN_broodtide.md, "The Spread").
 * Pure, with no Minecraft types, so the cap and the damage rate can be proven without a server (Broodtide work-split row 5, test 7).
 *
 * Three rules from the plan: (1) the number of goo cells has a CAP; (2) standing in goo hurts at most ONE damage tick per second per player,
 * from ALL goo together (ten overlapping cells, or goo laid by many sources, still give one tick a second); (3) tentacles may only rise from goo,
 * so a query tells whether a cell is goo. Every number below is a PROPOSAL: the plan names the rules, not the values.
 *
 * Cells are integer (x, z) on the arena floor. Time is in server ticks and is passed in, so the class has no clock and is deterministic.
 */
public final class GooGrid {
    /** At most this many goo cells exist at once. PROPOSAL: a 20 x 20 patch, about 1.5% of the roughly 27000 cells of a 93 block play radius. */
    public static final int MAX_CELLS = 400;
    /** A goo cell lasts this long unless laid again: 20 seconds. PROPOSAL. */
    public static final int LIFETIME_TICKS = 20 * 20;
    /** A player takes at most one goo damage tick per this many ticks: one a second. This is the plan's rule. */
    public static final int DAMAGE_INTERVAL_TICKS = 20;
    /** Walking speed multiplier on goo ("slow-and-sticky"). PROPOSAL; the adapter applies it. */
    public static final double SLOW_FACTOR = 0.6;

    private final Map<Long, Long> expiry = new HashMap<>();
    private final Map<UUID, Long> lastDamage = new HashMap<>();

    /** Packs a cell into one key. Both halves are used unsigned-safe, so negative coordinates work and never collide. */
    static long key(int x, int z) {
        return ((long) x << 32) | (z & 0xFFFFFFFFL);
    }

    /**
     * Lays goo on a cell at tick {@code now}. Returns true if the cell is goo afterwards. A cell that is already goo has its expiry refreshed and
     * does NOT take a new slot. A new cell is refused (returns false) when the grid is full: nothing is evicted, so the cap can never be exceeded and
     * the boss cannot flood the arena. Expired cells are cleared first, so a full grid of old goo does not block new goo.
     */
    public boolean lay(int x, int z, long now) {
        expire(now);
        long k = key(x, z);
        long until = now > Long.MAX_VALUE - LIFETIME_TICKS ? Long.MAX_VALUE : now + LIFETIME_TICKS;
        if (expiry.containsKey(k)) {
            expiry.put(k, until);
            return true;
        }
        if (expiry.size() >= MAX_CELLS) {
            return false;
        }
        expiry.put(k, until);
        return true;
    }

    /** True if the cell is goo at tick {@code now}. A cell is goo up to but NOT including its expiry tick. Tentacles may rise only here. */
    public boolean isGoo(int x, int z, long now) {
        Long until = expiry.get(key(x, z));
        return until != null && now < until;
    }

    /** Removes every cell whose time is up at {@code now}; returns how many were removed. */
    public int expire(long now) {
        int before = expiry.size();
        expiry.values().removeIf(until -> now >= until);
        return before - expiry.size();
    }

    /** How many cells are goo at tick {@code now} (counts only live cells, whether or not expire has run). */
    public int count(long now) {
        int n = 0;
        for (long until : expiry.values()) {
            if (now < until) {
                n++;
            }
        }
        return n;
    }

    /** Every live goo cell as {x, z}, sorted by x then z, so the result does not depend on hash order. */
    public List<int[]> cells(long now) {
        List<int[]> out = new ArrayList<>();
        for (Map.Entry<Long, Long> e : expiry.entrySet()) {
            if (now < e.getValue()) {
                out.add(new int[] {(int) (e.getKey() >> 32), (int) (long) e.getKey()});
            }
        }
        out.sort((p, q) -> p[0] != q[0] ? Integer.compare(p[0], q[0]) : Integer.compare(p[1], q[1]));
        return out;
    }

    /** Clears every cell and every damage clock (a run ended, so nothing may leak into the next one). */
    public void clear() {
        expiry.clear();
        lastDamage.clear();
    }

    /** Walking speed multiplier at this cell: {@link #SLOW_FACTOR} on goo, 1.0 off it. */
    public double speedFactor(int x, int z, long now) {
        return isGoo(x, z, now) ? SLOW_FACTOR : 1.0;
    }

    /**
     * Should this player take a goo damage tick now? True at most once per {@link #DAMAGE_INTERVAL_TICKS}, and only while they stand on goo. This is
     * the "one tick a second from ALL goo" rule: it keys on the PLAYER, not on the cell, so standing on many cells or crossing from one patch to
     * another gives no extra ticks. A true result starts the player's clock. Standing off goo neither damages nor resets the clock.
     */
    public boolean tryDamage(UUID player, int x, int z, long now) {
        if (!isGoo(x, z, now)) {
            return false;
        }
        Long last = lastDamage.get(player);
        if (last != null && now - last < DAMAGE_INTERVAL_TICKS && now >= last) {
            return false;
        }
        lastDamage.put(player, now);
        return true;
    }

    /** Forgets one player's damage clock (they left or died). */
    public void forget(UUID player) {
        lastDamage.remove(player);
    }
}
