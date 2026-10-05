package com.solme.emberfall.wave;

import com.solme.emberfall.world.ArenaInstance;
import com.solme.emberfall.world.RunEndHandler;
import com.solme.emberfall.world.RunManager;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * The optional escape portal of the Final Swarm. Drawn with particles only (ZERO entities, nothing to clean up but a map).
 * Standing in it for {@link #HOLD_TICKS} without leaving cashes the player out at the CURRENT multiplier; a step out resets
 * the hold, so nobody leaves by brushing past. The multiplier a player left at is remembered PER PLAYER until the reward
 * calculator reads it, so one player escaping never pays the rest of the party.
 */
public final class SwarmPortal {
    private SwarmPortal() {}

    public static final int HOLD_TICKS = 40;
    public static final double RADIUS = 1.6;

    private static final class Site {
        final BlockPos at;
        final Map<UUID, Integer> held = new HashMap<>();

        Site(BlockPos at) { this.at = at; }
    }

    private static final Map<Integer, Site> SITES = new HashMap<>();
    /** Multiplier (tenths) each player left at, waiting for the reward calculator to read it. */
    private static final Map<UUID, Integer> ESCAPED = new HashMap<>();

    public static void open(int slot, BlockPos at) {
        SITES.put(slot, new Site(at));
    }

    public static boolean isOpen(int slot) {
        return SITES.containsKey(slot);
    }

    public static BlockPos where(int slot) {
        Site s = SITES.get(slot);
        return s == null ? null : s.at;
    }

    public static void clear(int slot) {
        SITES.remove(slot);
    }

    /** The multiplier this player escaped at, in tenths, removed as it is read. 0 when they did not escape. */
    public static int takeEscape(UUID player) {
        Integer t = ESCAPED.remove(player);
        return t == null ? 0 : t;
    }

    public static void forget(UUID player) {
        ESCAPED.remove(player);
    }

    /** Every tick for every slot that has a portal. */
    public static void tickAll(MinecraftServer server) {
        if (SITES.isEmpty()) {
            return;
        }
        for (Map.Entry<Integer, Site> e : new java.util.ArrayList<>(SITES.entrySet())) {
            ArenaInstance arena = RunManager.getActive(e.getKey());
            WaveDirector director = WaveDirector.get(e.getKey());
            if (arena == null || director == null || !director.swarmActive()) {
                continue;
            }
            tickSite(server, arena, director, e.getValue());
        }
    }

    private static void tickSite(MinecraftServer server, ArenaInstance arena, WaveDirector director, Site site) {
        ServerLevel level = arena.level();
        double cx = site.at.getX() + 0.5, cy = site.at.getY(), cz = site.at.getZ() + 0.5;
        long now = server.getTickCount();
        if (now % 2 == 0) {
            double a = (now * 0.21) % (Math.PI * 2);
            for (int i = 0; i < 6; i++) {
                double ang = a + i * Math.PI / 3;
                level.sendParticles(ParticleTypes.PORTAL, cx + Math.cos(ang) * 1.1, cy + 0.2 + (now % 40) / 40.0 * 2.4, cz + Math.sin(ang) * 1.1, 1, 0.05, 0.05, 0.05, 0.0);
            }
            level.sendParticles(ParticleTypes.REVERSE_PORTAL, cx, cy + 1.2, cz, 2, 0.5, 1.0, 0.5, 0.02);
        }
        java.util.List<ServerPlayer> leaving = null; // leaving ends the run and edits level.players(): do it after the loop
        for (ServerPlayer p : level.players()) {
            Integer slot = RunManager.slotOf(p);
            if (slot == null || slot != arena.slot() || !p.isAlive() || p.isSpectator()) {
                site.held.remove(p.getUUID());
                continue;
            }
            boolean inside = p.distanceToSqr(cx, cy + 1.0, cz) <= RADIUS * RADIUS * 2.2;
            if (!inside) {
                site.held.remove(p.getUUID());
                continue;
            }
            int held = site.held.merge(p.getUUID(), 1, Integer::sum);
            if (held == 1) {
                p.sendSystemMessage(Component.literal("§bHold still to leave at §e" + String.format("%.1f", director.swarmTenths() / 10.0) + "x §7(step out to stay)"), true);
            }
            if (held >= HOLD_TICKS) {
                site.held.remove(p.getUUID());
                if (leaving == null) {
                    leaving = new java.util.ArrayList<>(2);
                }
                leaving.add(p);
            }
        }
        if (leaving != null) {
            int tenths = director.swarmTenths();
            for (ServerPlayer p : leaving) {
                leave(server, p, tenths);
            }
        }
    }

    /** The one exit: remember the multiplier, then end the run exactly as a voluntary leave does. */
    static void leave(MinecraftServer server, ServerPlayer player, int tenths) {
        ESCAPED.put(player.getUUID(), tenths);
        if (Boolean.getBoolean("emberfall.testMode")) {
            com.solme.emberfall.EmberfallMod.LOGGER.info("SWARM_TEST escape player={} tenths={}", player.getGameProfile().name(), tenths);
        }
        ((ServerLevel) player.level()).playSound(null, player.blockPosition(), SoundEvents.PORTAL_TRAVEL, SoundSource.PLAYERS, 0.6F, 1.3F);
        RunEndHandler.escapeRun(player, server);
    }
}
