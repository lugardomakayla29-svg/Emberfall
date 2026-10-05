package com.solme.emberfall.hub;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;

/**
 * The "refuse, then show me the nearest good spot" behaviour, per player.
 *
 * When a Hearth cannot light, {@link #begin} starts an incremental {@link HubSiteFinder} for that
 * player. Each server tick the finder examines {@link HubSiteFinder#PER_STEP} candidates, so a full
 * search never stalls a tick. When it finishes the player is told the distance and compass
 * direction, and a column of flame particles marks the spot for {@link #MARKER_TICKS}. Nothing is
 * ever moved or built automatically.
 *
 * The tick handler returns immediately (no allocation) while no search or marker is active.
 */
public final class HubSiteSearch {
    /** How long the particle marker stays visible after a site is found (30 seconds). */
    public static final int MARKER_TICKS = 600;
    /** Height of the particle column in blocks. */
    private static final int COLUMN_HEIGHT = 24;

    private record Search(HubSiteFinder finder, ServerLevel level, BlockPos origin) {}

    private static final class Marker {
        final ServerLevel level;
        final BlockPos site;
        int ticksLeft = MARKER_TICKS;

        Marker(ServerLevel level, BlockPos site) {
            this.level = level;
            this.site = site;
        }
    }

    private static final Map<UUID, Search> SEARCHES = new HashMap<>();
    private static final Map<UUID, Marker> MARKERS = new HashMap<>();

    private HubSiteSearch() {}

    public static void register() {
        ServerTickEvents.END_SERVER_TICK.register(HubSiteSearch::tick);
    }

    /** Starts (or restarts) the nearest-site search for {@code player}, centred on the Hearth. */
    public static void begin(ServerPlayer player, BlockPos hearth) {
        if (!(player.level() instanceof ServerLevel level)) {
            return;
        }
        MARKERS.remove(player.getUUID());
        SEARCHES.put(player.getUUID(), new Search(new HubSiteFinder(level, hearth), level, hearth));
        player.sendSystemMessage(Component.literal("§7Searching for the nearest flat, clear ground..."));
    }

    /** Drops any search or marker for a player who left. */
    public static void forget(UUID player) {
        SEARCHES.remove(player);
        MARKERS.remove(player);
    }

    private static void tick(MinecraftServer server) {
        if (SEARCHES.isEmpty() && MARKERS.isEmpty()) {
            return;
        }
        Iterator<Map.Entry<UUID, Search>> it = SEARCHES.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<UUID, Search> entry = it.next();
            ServerPlayer player = server.getPlayerList().getPlayer(entry.getKey());
            Search search = entry.getValue();
            if (player == null) {
                it.remove();
                continue;
            }
            search.finder().step();
            if (!search.finder().isDone()) {
                continue;
            }
            it.remove();
            BlockPos site = search.finder().result();
            if (site == null) {
                player.sendSystemMessage(Component.literal(
                        "§cNo flat, clear ground within " + HubSiteFinder.MAX_RADIUS
                                + " blocks. Try a different area (plains and deserts work best)."));
                continue;
            }
            player.sendSystemMessage(Component.literal(describe(search.origin(), site)));
            MARKERS.put(player.getUUID(), new Marker(search.level(), site));
        }

        Iterator<Map.Entry<UUID, Marker>> mit = MARKERS.entrySet().iterator();
        while (mit.hasNext()) {
            Map.Entry<UUID, Marker> entry = mit.next();
            Marker marker = entry.getValue();
            if (--marker.ticksLeft <= 0 || server.getPlayerList().getPlayer(entry.getKey()) == null) {
                mit.remove();
                continue;
            }
            // Every 5 ticks keeps the column visible without spamming packets.
            if (marker.ticksLeft % 5 == 0) {
                drawColumn(marker.level, marker.site);
            }
        }
    }

    private static void drawColumn(ServerLevel level, BlockPos site) {
        double x = site.getX() + 0.5;
        double z = site.getZ() + 0.5;
        for (int i = 0; i < COLUMN_HEIGHT; i += 2) {
            level.sendParticles(ParticleTypes.FLAME, x, site.getY() + 1 + i, z, 1, 0.15, 0.0, 0.15, 0.0);
        }
        // A ring on the ground shows the size of the footprint the hub needs.
        int r = HubSiteAnalyzer.RADIUS;
        for (int i = -r; i <= r; i += 2) {
            level.sendParticles(ParticleTypes.END_ROD, x + i, site.getY() + 1.1, z - r, 1, 0, 0, 0, 0.0);
            level.sendParticles(ParticleTypes.END_ROD, x + i, site.getY() + 1.1, z + r, 1, 0, 0, 0, 0.0);
            level.sendParticles(ParticleTypes.END_ROD, x - r, site.getY() + 1.1, z + i, 1, 0, 0, 0, 0.0);
            level.sendParticles(ParticleTypes.END_ROD, x + r, site.getY() + 1.1, z + i, 1, 0, 0, 0, 0.0);
        }
    }

    /** "Nearest good ground: 54 blocks north-east (x, y, z). Follow the flames." */
    static String describe(BlockPos from, BlockPos site) {
        int dx = site.getX() - from.getX();
        int dz = site.getZ() - from.getZ();
        int dist = (int) Math.round(Math.sqrt((double) dx * dx + (double) dz * dz));
        return "§aNearest good ground: §f" + dist + " blocks " + compass(dx, dz) + " §7(" + site.getX() + ", "
                + site.getY() + ", " + site.getZ() + "). Follow the flames.";
    }

    /** Eight-way compass word for an offset. North is -Z and east is +X, matching Minecraft. */
    static String compass(int dx, int dz) {
        double deg = Math.toDegrees(Math.atan2(dx, -dz));
        deg = (deg % 360.0 + 360.0) % 360.0;
        String[] names = {"north", "north-east", "east", "south-east", "south", "south-west", "west", "north-west"};
        return names[(int) Math.round(deg / 45.0) % 8];
    }
}
