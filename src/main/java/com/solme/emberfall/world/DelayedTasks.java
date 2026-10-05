package com.solme.emberfall.world;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * A minimal "run this a few ticks from now" queue, keyed on the level's game time. Weapons use it for follow-up sweeps and
 * volleys. Everything runs on the server thread inside the end-of-tick event, so no locking is needed.
 *
 * Bounded on purpose: at most {@link #MAX_PENDING} tasks wait at once and a task may wait at most {@link #MAX_DELAY_TICKS}, so a
 * bug or an extreme level can never grow it without limit. A task that throws is logged and dropped, never retried, so one bad
 * callback cannot stall the queue. A task whose level has unloaded is dropped.
 */
public final class DelayedTasks {
    public static final int MAX_PENDING = 4096;
    public static final int MAX_DELAY_TICKS = 20 * 30;

    private record Task(ServerLevel level, long runAt, Consumer<Object> body) {}

    private static final List<Task> PENDING = new ArrayList<>();
    private static boolean registered;

    private DelayedTasks() {}

    public static void register() {
        if (registered) {
            return;
        }
        registered = true;
        ServerTickEvents.END_SERVER_TICK.register(DelayedTasks::tick);
    }

    /** Runs {@code body} about {@code delayTicks} game ticks from now. Returns false, running nothing, if the queue is full. */
    public static boolean runLater(ServerLevel level, int delayTicks, Consumer<Object> body) {
        if (PENDING.size() >= MAX_PENDING) {
            return false;
        }
        int delay = Math.max(1, Math.min(delayTicks, MAX_DELAY_TICKS));
        PENDING.add(new Task(level, level.getGameTime() + delay, body));
        return true;
    }

    public static int pending() {
        return PENDING.size();
    }

    public static void clear() {
        PENDING.clear();
    }

    private static void tick(MinecraftServer server) {
        if (PENDING.isEmpty()) {
            return;
        }
        List<Task> due = new ArrayList<>();
        PENDING.removeIf(t -> {
            if (t.level.getGameTime() >= t.runAt) {
                due.add(t);
                return true;
            }
            return false;
        });
        for (Task t : due) {
            try {
                t.body.accept(null);
            } catch (RuntimeException e) {
                System.err.println("[Emberfall] delayed task failed and was dropped: " + e);
            }
        }
    }
}
