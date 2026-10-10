package com.solme.emberfall.rift;

import java.util.LinkedHashSet;
import java.util.Set;
import java.util.UUID;

/**
 * The pure rules for the Character Select step between the Rift countdown and the run. Order, as the owner decided (2026-10-08): click the
 * Rift, hold still for the 3 s countdown (a move cancels), THEN the party is offered Character Select, then the run starts.
 *
 * The run reads each player's STORED character at the moment it places them (RunManager), so a choice made on the screen is already a stored
 * choice. That makes "closed the screen without choosing" safe and simple: the player keeps the character they already had. This class only
 * decides WHEN the party may leave: when nobody is still deciding, or when {@link #TIMEOUT_TICKS} have passed. No Minecraft types, so a check
 * can run it without the game jar.
 */
public final class RiftSelect {
    /** How long the party may take to decide. After this the run starts and anyone still undecided keeps their stored character. */
    public static final int TIMEOUT_SECONDS = 30;
    public static final long TIMEOUT_TICKS = TIMEOUT_SECONDS * 20L;

    /** One party's selection phase. Members are the ones who held still through the countdown. */
    public static final class Phase {
        private final long startedTick;
        private final Set<UUID> pending = new LinkedHashSet<>();
        private final Set<UUID> everyone = new LinkedHashSet<>();

        public Phase(long startedTick, Set<UUID> members) {
            this.startedTick = startedTick;
            this.pending.addAll(members);
            this.everyone.addAll(members);
        }

        /** The member picked a character or closed the screen. A repeat, or a stranger, changes nothing. Returns true when this call resolved them. */
        public boolean resolve(UUID id) {
            return pending.remove(id);
        }

        /** The member left the server, died, or otherwise cannot go. They are no longer waited for and no longer in the party. */
        public boolean drop(UUID id) {
            everyone.remove(id);
            return pending.remove(id);
        }

        public boolean isPending(UUID id) {
            return pending.contains(id);
        }

        public int pendingCount() {
            return pending.size();
        }

        /** Members who will go on the run: everyone who was in the phase and has not been dropped. */
        public Set<UUID> party() {
            return new LinkedHashSet<>(everyone);
        }

        public long startedTick() {
            return startedTick;
        }

        /** True when the party may leave now: nobody is still deciding, or the time is up. A phase with nobody left to go never "finishes" a run. */
        public boolean done(long nowTick) {
            return pending.isEmpty() || timedOut(nowTick);
        }

        public boolean timedOut(long nowTick) {
            return nowTick - startedTick >= TIMEOUT_TICKS;
        }

        /** True when a phase has no one to send on a run (everyone was dropped). The caller discards it and starts nothing. */
        public boolean empty() {
            return everyone.isEmpty();
        }
    }

    private RiftSelect() {}

    /** Whole seconds left for the on-screen reminder; never negative, never above the timeout. */
    public static int secondsLeft(long elapsedTicks) {
        long left = TIMEOUT_TICKS - Math.max(0L, elapsedTicks);
        return (int) Math.min(TIMEOUT_SECONDS, Math.max(0L, (left + 19) / 20));
    }
}
