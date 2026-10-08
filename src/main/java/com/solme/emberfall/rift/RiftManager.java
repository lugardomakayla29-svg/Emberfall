package com.solme.emberfall.rift;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.solme.emberfall.world.ArenaInstance;
import com.solme.emberfall.world.Dimensions;
import com.solme.emberfall.world.RunManager;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

/**
 * The open Rifts of a server, and the two ways one comes to exist: a Rift Shard used by a player, and the natural event.
 * Every rule (odds, cooldowns, spacing, idle time, open air) is a pure predicate in {@link RiftRules} and {@link RiftSpot}; this class only
 * supplies the facts (where the players are, what the blocks are) and keeps the list. A Rift is drawn with particles by {@link RiftStage} and
 * touches NO block and creates NO entity, so there is no ground to restore when it closes.
 *
 * A Rift lives only in memory: it is a short event (10 minutes idle at most), so a server restart simply ends it, with nothing left behind.
 */
public final class RiftManager {
    private RiftManager() {}

    /** One open Rift. */
    public static final class Rift {
        public final ServerLevel level;
        public final double x;
        public final double y;
        public final double z;
        public final int facing;
        public final long seed;
        /** Game time at which the opening show started. */
        public final long openedAt;
        /** Players who stepped into this Rift (step 4 fills it); an idle Rift has none. */
        public int waiting;
        /** Game time the closing show started, or -1 while the Rift is open. */
        long closingAt = -1;
        /** How many timed events the last show (opening or closing) holds, for diagnostics. */
        public int showEvents;

        Rift(ServerLevel level, double x, double y, double z, int facing, long seed, long openedAt) {
            this.level = level;
            this.x = x;
            this.y = y;
            this.z = z;
            this.facing = facing;
            this.seed = seed;
            this.openedAt = openedAt;
        }

        /** True once the 5 s opening has played, so the Rift can be entered (design: OPEN at t = 5 s). */
        public boolean isOpen(long now) {
            return closingAt < 0 && now - openedAt >= RiftFx.OPEN_TICK;
        }
    }

    /** All Rifts, in every level. Only the server thread touches this, and iteration is always over a snapshot. */
    private static final List<Rift> RIFTS = new ArrayList<>();
    /** Per player: game time of the last natural roll and of the last Rift that opened for them. */
    private static final Map<UUID, Long> LAST_ROLL = new HashMap<>();
    private static final Map<UUID, Long> LAST_NATURAL = new HashMap<>();

    /** Sample grid across the Rift's box when judging open air: 5 columns by 5 rows. */
    private static final int SAMPLE_COLS = 5;
    private static final int SAMPLE_ROWS = 5;

    public static List<Rift> all() {
        return new ArrayList<>(RIFTS);
    }

    public static int count() {
        return RIFTS.size();
    }

    /** Forgets everything (server stop, tests). */
    public static void clear() {
        RIFTS.clear();
        LAST_ROLL.clear();
        LAST_NATURAL.clear();
        RiftStage.clear();
    }

    /** The result of asking for a Rift: the Rift, or the reason there is none. */
    public record Result(Rift rift, String refusal) {
        public boolean ok() {
            return rift != null;
        }
    }

    /**
     * Opens a Rift at an anchor (the middle of the opening). Returns the Rift, or the reason it was refused. Plays the opening show and
     * tells everyone nearby in chat. {@code facing} is RiftPlacement's 0..3.
     */
    public static Result open(ServerLevel level, double x, double y, double z, int facing, long seed) {
        if (level.dimension().equals(Dimensions.EXPEDITION)) {
            return new Result(null, "the expedition map is a run arena");
        }
        boolean inRun = insideActiveRun(level, x, y, z);
        double nearest = RiftSpot.nearest(positions(), x, y, z);
        boolean air = hasOpenAir(level, x, y, z, facing);
        String refusal = RiftRules.placementRefusal(inRun, nearest, air);
        if (refusal != null) {
            return new Result(null, refusal);
        }
        Rift r = new Rift(level, x, y, z, facing, seed, level.getGameTime());
        RIFTS.add(r);
        r.showEvents = RiftStage.start(level, x, y, z, facing, seed, false);
        return new Result(r, null);
    }

    /** Starts the closing show for a Rift and removes it from the list when the show is over (see {@link #tickAll}). */
    public static void close(Rift r) {
        if (r.closingAt < 0) {
            r.closingAt = r.level.getGameTime();
            r.showEvents = RiftStage.start(r.level, r.x, r.y, r.z, r.facing, r.seed, true);
        }
    }

    private static List<RiftSpot.At> positions() {
        List<RiftSpot.At> out = new ArrayList<>();
        for (Rift r : new ArrayList<>(RIFTS)) {
            out.add(new RiftSpot.At(r.x, r.y, r.z));
        }
        return out;
    }

    /** True when the anchor lies inside the bounds of a run that is live in this level. */
    static boolean insideActiveRun(ServerLevel level, double x, double y, double z) {
        BlockPos p = BlockPos.containing(x, y, z);
        for (ArenaInstance inst : new ArrayList<>(RunManager.activeInstances())) {
            if (inst.level() == level && inst.bounds().isInside(p)) {
                return true;
            }
        }
        return false;
    }

    /** Samples a 5 by 5 grid across the Rift's box, in its own plane, and counts blocks that do not block (air, water, plants). */
    static boolean hasOpenAir(ServerLevel level, double x, double y, double z, int facing) {
        int open = 0;
        int total = 0;
        for (int i = 0; i < SAMPLE_COLS; i++) {
            for (int j = 0; j < SAMPLE_ROWS; j++) {
                int cx = (int) Math.round((i + 0.5) / SAMPLE_COLS * RiftShape.BOX_W);
                int cy = (int) Math.round((j + 0.5) / SAMPLE_ROWS * RiftShape.BOX_H);
                RiftPlacement.Pos p = RiftPlacement.cell(x, y, z, facing, RiftShape.BOX_W, RiftShape.BOX_H, cx, cy);
                BlockPos bp = BlockPos.containing(p.x(), p.y(), p.z());
                if (!level.hasChunkAt(bp)) {
                    continue;
                }
                total++;
                BlockState s = level.getBlockState(bp);
                if (s.getCollisionShape(level, bp).isEmpty()) {
                    open++;
                }
            }
        }
        return RiftSpot.hasOpenAir(open, total);
    }

    /** One server tick: closes idle Rifts, drops finished ones, and rolls the natural event. */
    public static void tickAll(MinecraftServer server) {
        if (RIFTS.isEmpty() && LAST_ROLL.isEmpty() && server.getPlayerCount() == 0) {
            return;
        }
        for (Rift r : new ArrayList<>(RIFTS)) {
            long now = r.level.getGameTime();
            if (r.closingAt >= 0) {
                if (now - r.closingAt > RiftFx.CLOSE_TICKS) {
                    RIFTS.remove(r);
                }
            } else if (RiftRules.idleExpired(now - r.openedAt, r.waiting)) {
                close(r);
            }
        }
        rollNatural(server);
    }

    private static void rollNatural(MinecraftServer server) {
        ServerLevel overworld = server.getLevel(Level.OVERWORLD);
        if (overworld == null) {
            return;
        }
        long now = overworld.getGameTime();
        // Forget players who left, so these maps cannot grow without bound.
        Iterator<UUID> gone = LAST_ROLL.keySet().iterator();
        while (gone.hasNext()) {
            if (server.getPlayerList().getPlayer(gone.next()) == null) {
                gone.remove();
            }
        }
        for (ServerPlayer p : new ArrayList<>(overworld.players())) {
            Long last = LAST_ROLL.get(p.getUUID());
            if (last == null) {
                LAST_ROLL.put(p.getUUID(), now);   // first sight: start the interval, do not roll at once
                continue;
            }
            if (!RiftRules.naturalRollDue(now - last)) {
                continue;
            }
            LAST_ROLL.put(p.getUUID(), now);
            if (RunManager.slotOf(p) != null) {
                continue;                          // never during a run
            }
            Long lastRift = LAST_NATURAL.get(p.getUUID());
            if (lastRift != null && RiftRules.inNaturalCooldown(now - lastRift)) {
                continue;
            }
            if (!RiftRules.naturalRollWins(p.getRandom().nextInt(RiftRules.NATURAL_ODDS))) {
                continue;
            }
            if (tryOpenNear(p) != null) {
                LAST_NATURAL.put(p.getUUID(), now);
            }
        }
    }

    /**
     * Tries a few spots 16 to 32 blocks from a player, in front of them, and opens a Rift at the first legal one, facing the player.
     * Returns the Rift or null. The ground height comes from the surface, then the Rift hangs 7 blocks up so its box clears the floor.
     */
    public static Rift tryOpenNear(ServerPlayer p) {
        ServerLevel level = (ServerLevel) p.level();
        for (int attempt = 0; attempt < 8; attempt++) {
            double dist = RiftRules.NATURAL_MIN_DISTANCE + p.getRandom().nextDouble() * (RiftRules.NATURAL_MAX_DISTANCE - RiftRules.NATURAL_MIN_DISTANCE);
            double ang = p.getRandom().nextDouble() * Math.PI * 2.0;
            double x = p.getX() + Math.cos(ang) * dist;
            double z = p.getZ() + Math.sin(ang) * dist;
            if (!level.hasChunkAt(BlockPos.containing(x, p.getY(), z))) {
                continue;
            }
            int ground = level.getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING, (int) Math.floor(x), (int) Math.floor(z));
            double y = ground + RiftShape.BOX_H / 2.0;
            double real = Math.sqrt((x - p.getX()) * (x - p.getX()) + (z - p.getZ()) * (z - p.getZ()));
            if (!RiftRules.naturalDistanceOk(real)) {
                continue;
            }
            int facing = RiftSpot.facingToward(p.getX() - x, p.getZ() - z);
            Result r = open(level, x, y, z, facing, level.getGameTime() ^ ((long) attempt << 40) ^ p.getUUID().getLeastSignificantBits());
            if (r.ok()) {
                p.sendSystemMessage(Component.translatable("emberfall.rift.natural"));
                return r.rift();
            }
        }
        return null;
    }
}
