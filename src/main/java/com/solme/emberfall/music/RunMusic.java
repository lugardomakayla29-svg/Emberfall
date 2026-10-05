package com.solme.emberfall.music;

import net.minecraft.network.protocol.game.ClientboundSoundPacket;
import net.minecraft.network.protocol.game.ClientboundStopSoundPacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Run music: a shuffle playlist that plays ONLY while a player is in a run. {@link #start} and {@link #stop}
 * are called from RunManager.joinPlayer / leavePlayer, the two hooks every way of entering or leaving a run
 * already goes through (death, leave, disconnect), so the music cannot outlive the run.
 *
 * State is per player. The shuffle plays every track once in random order before any repeats, and the first
 * track of a new pass is never the one that just finished.
 */
public final class RunMusic {
    private static final class State {
        final List<ModSounds.Track> queue = new ArrayList<>();
        ModSounds.Track current;
        long endsAtTick;
    }

    private static final Map<UUID, State> STATES = new HashMap<>();

    private RunMusic() {}

    public static void start(ServerPlayer player) {
        State s = new State();
        STATES.put(player.getUUID(), s);
        playNext(player, s, ((net.minecraft.server.level.ServerLevel) player.level()).getServer().getTickCount());
    }

    public static void stop(ServerPlayer player) {
        State s = STATES.remove(player.getUUID());
        if (s != null && s.current != null) {
            stopSound(player, s.current);
        }
    }

    /** Called once per server tick: moves on to the next track when the current one has finished. */
    public static void tick(MinecraftServer server) {
        if (STATES.isEmpty()) {
            return;
        }
        long now = server.getTickCount();
        for (Map.Entry<UUID, State> e : new ArrayList<>(STATES.entrySet())) {
            State s = e.getValue();
            if (now < s.endsAtTick) {
                continue;
            }
            ServerPlayer p = server.getPlayerList().getPlayer(e.getKey());
            if (p == null) {
                STATES.remove(e.getKey());
                continue;
            }
            playNext(p, s, now);
        }
    }

    /** Debug: pretend the current track just ended so the next tick starts the next one. */
    public static void debugFinishCurrent(ServerPlayer player) {
        State s = STATES.get(player.getUUID());
        if (s != null) {
            s.endsAtTick = 0;
        }
    }

    /** Who is currently hearing what, for tests and /emberfall debug. */
    public static String nowPlaying(ServerPlayer player) {
        State s = STATES.get(player.getUUID());
        return s == null || s.current == null ? "none" : s.current.name();
    }

    private static void playNext(ServerPlayer player, State s, long now) {
        if (s.queue.isEmpty()) {
            List<ModSounds.Track> pass = new ArrayList<>(ModSounds.ALL);
            Collections.shuffle(pass);
            // never start a new pass with the track that just played
            if (pass.size() > 1 && pass.get(0) == s.current) {
                Collections.swap(pass, 0, 1);
            }
            s.queue.addAll(pass);
        }
        s.current = s.queue.remove(0);
        s.endsAtTick = now + s.current.lengthTicks();
        // A packet sent straight to this player (not level.playSound), so nobody else hears it, and a fixed
        // position on the player with a large volume so it is not attenuated as they move.
        player.connection.send(new ClientboundSoundPacket(
                net.minecraft.core.Holder.direct(s.current.event()), SoundSource.MUSIC,
                player.getX(), player.getY(), player.getZ(), 1000.0F, 1.0F, player.getRandom().nextLong()));
    }

    private static void stopSound(ServerPlayer player, ModSounds.Track track) {
        player.connection.send(new ClientboundStopSoundPacket(
                net.minecraft.resources.Identifier.fromNamespaceAndPath("emberfall", "music." + track.name()), SoundSource.MUSIC));
    }
}
