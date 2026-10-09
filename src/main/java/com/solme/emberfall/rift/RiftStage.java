package com.solme.emberfall.rift;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.phys.Vec3;

/**
 * The game-side player of a Rift: takes the pure timed schedule from {@link RiftFx} and shows it with particle, sound and chat
 * packets only. It creates NO entity of any kind (the owner's rule for the Rift), which {@code /emberfall rift state} reports as a
 * before and after entity count so it can be proven live. All the maths (cell to block, who is in range) lives in
 * {@link RiftPlacement} and is checked without the game jar.
 */
public final class RiftStage {
    private RiftStage() {}

    /** Vanilla only sends particles to players within 32 blocks unless forced (ServerLevel.sendParticles, measured from bytecode). */
    public static final double RENDER_RANGE = 32.0;
    /** Players this close to the Rift feel the push. */
    public static final double PUSH_RANGE = 6.0;
    /** Blocks per tick of outward velocity added by the push (a nudge, never damage). */
    public static final double PUSH_STRENGTH = 0.6;

    /** One Rift being shown. */
    static final class Active {
        final ServerLevel level;
        final double x;
        final double y;
        final double z;
        final int facing;
        final RiftShape.Shape shape;
        final List<RiftFx.Event> events;
        final boolean closing;
        int tick;

        Active(ServerLevel level, double x, double y, double z, int facing, RiftShape.Shape shape, boolean closing) {
            this.level = level;
            this.x = x;
            this.y = y;
            this.z = z;
            this.facing = facing;
            this.shape = shape;
            this.closing = closing;
            this.events = closing ? RiftFx.closing(shape) : RiftFx.opening(shape);
        }

        int lastTick() {
            int last = 0;
            for (RiftFx.Event e : events) {
                last = Math.max(last, e.tick);
            }
            return last;
        }
    }

    private static final List<Active> ACTIVE = new ArrayList<>();

    /** Starts showing a Rift opening (or closing) at an anchor. Returns how many events the schedule holds. */
    public static int start(ServerLevel level, double x, double y, double z, int facing, long seed, boolean closing) {
        RiftShape.Shape shape = RiftShape.generate(seed);
        Active a = new Active(level, x, y, z, facing, shape, closing);
        ACTIVE.add(a);
        return a.events.size();
    }

    /** Stops every Rift immediately (used by the debug command and on server stop). */
    public static void clear() {
        ACTIVE.clear();
    }

    public static int activeCount() {
        return ACTIVE.size();
    }

    /** One server tick: plays this tick's slice of every active Rift, then drops the ones whose schedule is over. */
    public static void tickAll(MinecraftServer server) {
        if (ACTIVE.isEmpty()) {
            return;
        }
        Iterator<Active> it = ACTIVE.iterator();
        while (it.hasNext()) {
            Active a = it.next();
            for (RiftFx.Event e : RiftFx.at(a.events, a.tick)) {
                play(a, e);
            }
            // The opening is three bursts with near-empty stretches between them (measured: ticks 50..80 averaged 3 particles, 80..100 none),
            // which is why the owner could barely see it. While it OPENS, the steady swirl of the open Rift plays under the bursts, so the
            // tear is never dark. It is a separate layer: the proven schedule above is unchanged. Closing keeps only its own schedule.
            if (!a.closing && a.tick >= RiftFx.T_CRACK_START && a.tick < RiftFx.OPEN_TICK) {
                drawIdle(a.level, a.x, a.y, a.z, a.facing, a.shape, a.tick);
            }
            a.tick++;
            if (a.tick > a.lastTick()) {
                it.remove();
            }
        }
    }

    private static void play(Active a, RiftFx.Event e) {
        RiftPlacement.Pos p = RiftPlacement.cell(a.x, a.y, a.z, a.facing, a.shape.width, a.shape.height, e.x, e.y);
        switch (e.kind) {
            case PARTICLE -> a.level.sendParticles(particle(e), p.x(), p.y(), p.z(), Math.max(1, e.count), 0.12, 0.12, 0.12, 0.01);
            case SOUND -> a.level.playSound(null, p.x(), p.y(), p.z(), sound(e.key), SoundSource.AMBIENT, e.a, e.b);
            case CHAT -> {
                Component line = Component.translatable("emberfall.rift." + e.key.replace("rift_", ""));
                for (ServerPlayer pl : new ArrayList<>(a.level.players())) {
                    if (RiftPlacement.within(a.x, a.y, a.z, pl.getX(), pl.getY(), pl.getZ(), RENDER_RANGE)) {
                        pl.sendSystemMessage(line);
                    }
                }
            }
            case PUSH -> push(a);
        }
    }

    /** A gentle outward nudge for players only, along the way the Rift faces. No damage, no mob is touched. */
    private static void push(Active a) {
        double[] n = RiftPlacement.normal(a.facing);
        for (ServerPlayer pl : new ArrayList<>(a.level.players())) {
            if (RiftPlacement.within(a.x, a.y, a.z, pl.getX(), pl.getY(), pl.getZ(), PUSH_RANGE)) {
                Vec3 v = pl.getDeltaMovement();
                pl.setDeltaMovement(v.x + n[0] * PUSH_STRENGTH, v.y + 0.15, v.z + n[1] * PUSH_STRENGTH);
                pl.hurtMarked = true;
            }
        }
    }

    /** Dust size for the rim's warm layer: smaller than the fill's 1.1 so it sits under the white streak as a tint, not as a second shape. PROPOSAL. */
    static final float RIM_DUST_SIZE = 0.8F;
    /** Dust size of the OPEN Rift's pink and lilac fill. Larger than the opening's 1.1 so a standing Rift reads as a body, not a sprinkle. PROPOSAL, unseen. */
    static final float FILL_DUST_SIZE = 1.7F;

    /**
     * Draws ONE tick of an OPEN Rift: the idle look from {@link RiftIdle}. Called by {@link RiftManager#tickAll} for every Rift that is open
     * (after the 5 s opening, before it starts closing). Particles are sent with {@code force} semantics by {@code sendParticles} to players
     * inside {@link #RENDER_RANGE}, so a Rift can be seen from far away.
     */
    public static int drawIdle(ServerLevel level, double x, double y, double z, int facing, RiftShape.Shape shape, int ticksOpen) {
        int drawn = 0;
        for (RiftFx.Event e : RiftIdle.at(shape, ticksOpen)) {
            RiftPlacement.Pos p = RiftPlacement.cell(x, y, z, facing, shape.width, shape.height, e.x, e.y);
            level.sendParticles(particle(e), p.x(), p.y(), p.z(), Math.max(1, e.count), 0.18, 0.22, 0.18, 0.012);
            drawn += Math.max(1, e.count);
        }
        return drawn;
    }

    /** Maps the schedule's plain keys onto real vanilla particles. Colour-carrying keys use dust so the pink and lilac show. */
    static ParticleOptions particle(RiftFx.Event e) {
        return switch (e.key) {
            case "end_rod" -> ParticleTypes.END_ROD;
            case "electric_spark" -> ParticleTypes.ELECTRIC_SPARK;
            case "dust_ring" -> ParticleTypes.DUST_PLUME;
            case "glow" -> new DustParticleOptions((int) e.a & 0xFFFFFF, 1.1F);
            case "rim_dust" -> new DustParticleOptions((int) e.a & 0xFFFFFF, RIM_DUST_SIZE);
            case "fill_dust" -> new DustParticleOptions((int) e.a & 0xFFFFFF, FILL_DUST_SIZE);
            default -> ParticleTypes.END_ROD;
        };
    }

    /** Maps the schedule's plain keys onto real vanilla sounds (layered, ominous, no custom audio). */
    static SoundEvent sound(String key) {
        return switch (key) {
            case "rift_boom" -> SoundEvents.END_PORTAL_SPAWN;
            case "rift_drone" -> SoundEvents.BEACON_AMBIENT;
            case "rift_hum" -> SoundEvents.BEACON_AMBIENT;
            case "rift_crackle" -> SoundEvents.AMETHYST_BLOCK_RESONATE;
            default -> SoundEvents.AMETHYST_BLOCK_RESONATE;
        };
    }
}
