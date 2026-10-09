package com.solme.emberfall.boss;

import com.solme.emberfall.entity.BroodtideBody;
import com.solme.emberfall.world.RunManager;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.UUID;

/**
 * The entity side of the Broodtide's Grab (docs/PLAN_broodtide.md sections 4 and 8 test 5). All decisions come from the pure {@link BroodtideGrab}; this
 * class only reads the world, draws the telegraph, applies the two allowed effects, applies the ONE impulse and clears everything when a grab ends.
 * Zero entities: a grab is a record plus particles, never a spawned tentacle (owner rule: minimal entities).
 */
public final class BroodtideGrabber {
    /** One running grab on one player. */
    private static final class Grab {
        final UUID player;
        final long start;
        boolean impulseUsed;
        Grab(UUID player, long start) {
            this.player = player;
            this.start = start;
        }
    }

    private final int slot;
    private final List<Grab> grabs = new ArrayList<>();
    private long lastStart = -1;
    private BroodtideGrab.Phase phase = BroodtideGrab.Phase.ONE;
    /** Number of impulses applied in this fight, so a test can assert "at most one per grab". */
    private int impulsesApplied = 0;
    private int grabsStarted = 0;

    public BroodtideGrabber(int slot) {
        this.slot = slot;
    }

    public BroodtideGrab.Phase phase() {
        return phase;
    }

    public int impulsesApplied() {
        return impulsesApplied;
    }

    public int grabsStarted() {
        return grabsStarted;
    }

    public int activeGrabs() {
        return grabs.size();
    }

    /** Called once a tick by the body. {@code healthFraction} is current/max. */
    public void tick(ServerLevel level, BroodtideBody body, long fightTick, double healthFraction) {
        phase = BroodtideGrab.latch(phase, healthFraction);

        Iterator<Grab> it = grabs.iterator();
        while (it.hasNext()) {
            Grab g = it.next();
            ServerPlayer p = playerOf(level, g.player);
            boolean cancelled = p == null || !p.isAlive() || !body.isAlive();
            if (BroodtideGrab.ended(g.start, fightTick, cancelled)) {
                if (p != null) {
                    clearEffects(p);
                }
                it.remove();
                continue;
            }
            long age = fightTick - g.start;
            if (age < BroodtideGrab.WINDUP_TICKS) {
                telegraph(level, body, p, age);
            } else if (age == BroodtideGrab.WINDUP_TICKS) {
                land(level, body, p, g);
            }
        }

        if (BroodtideGrab.mayStart(fightTick, lastStart, phase, !grabs.isEmpty())) {
            begin(level, body, fightTick);
        }
    }

    private void begin(ServerLevel level, BroodtideBody body, long fightTick) {
        List<ServerPlayer> targets = new ArrayList<>();
        for (ServerPlayer p : level.players()) {
            Integer s = RunManager.slotOf(p);
            if (s == null || s != slot || !p.isAlive() || p.isSpectator()) {
                continue;
            }
            double dist = Math.hypot(p.getX() - body.getX(), p.getZ() - body.getZ());
            if (BroodtideGrab.inReach(dist)) {
                targets.add(p);
            }
        }
        if (targets.isEmpty()) {
            return;
        }
        targets.sort((a, b) -> Double.compare(b.distanceToSqr(body), a.distanceToSqr(body)));   // the farthest first: the grab punishes standing back
        int n = Math.min(BroodtideGrab.maxTargets(phase), targets.size());
        for (int i = 0; i < n; i++) {
            grabs.add(new Grab(targets.get(i).getUUID(), fightTick));
        }
        lastStart = fightTick;
        grabsStarted++;
        level.playSound(null, body.getX(), body.getY(), body.getZ(), SoundEvents.SLIME_SQUISH, SoundSource.HOSTILE, 2.0F, 0.6F);
    }

    /** A line of dust from the body to the player, thickening with the wind-up. At most 12 points, every 4th tick (packet cost rule). */
    private void telegraph(ServerLevel level, BroodtideBody body, ServerPlayer p, long age) {
        if (p == null || age % 4 != 0) {
            return;
        }
        DustParticleOptions dust = new DustParticleOptions(0x7BE04A, 1.4F);
        double bx = body.getX(), by = body.getY() + 1.0, bz = body.getZ();
        double dx = p.getX() - bx, dy = p.getY() + 0.5 - by, dz = p.getZ() - bz;
        int points = 12;
        for (int i = 1; i <= points; i++) {
            double t = (double) i / points;
            level.sendParticles(dust, true, true, bx + dx * t, by + dy * t, bz + dz * t, 1, 0.0, 0.0, 0.0, 0.0);
        }
    }

    /** The tentacle lands: re-check reach, apply the two effects and the ONE impulse. */
    private void land(ServerLevel level, BroodtideBody body, ServerPlayer p, Grab g) {
        if (p == null) {
            return;
        }
        double dist = Math.hypot(p.getX() - body.getX(), p.getZ() - body.getZ());
        if (!BroodtideGrab.inReach(dist)) {
            return;   // the player moved out of reach (or under the body) during the wind-up: the grab misses and ends on its timer
        }
        p.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, BroodtideGrab.HOLD_TICKS, BroodtideGrab.SLOWNESS_AMPLIFIER));
        p.addEffect(new MobEffectInstance(MobEffects.MINING_FATIGUE, BroodtideGrab.HOLD_TICKS, BroodtideGrab.FATIGUE_AMPLIFIER));
        if (!g.impulseUsed) {
            double[] v = BroodtideGrab.impulse(body.getX(), body.getZ(), p.getX(), p.getZ());
            if (v[0] != 0.0 || v[2] != 0.0) {
                p.setDeltaMovement(v[0], v[1], v[2]);
                p.hurtMarked = true;
                g.impulseUsed = true;
                impulsesApplied++;
            }
        }
        level.playSound(null, p.getX(), p.getY(), p.getZ(), SoundEvents.SLIME_ATTACK, SoundSource.HOSTILE, 1.5F, 0.7F);
    }

    private static void clearEffects(ServerPlayer p) {
        p.removeEffect(MobEffects.SLOWNESS);
        p.removeEffect(MobEffects.MINING_FATIGUE);
    }

    private static ServerPlayer playerOf(ServerLevel level, UUID id) {
        Entity e = level.getEntity(id);
        return e instanceof ServerPlayer sp ? sp : null;
    }

    /** Ends every grab and clears the effects: the boss died, or the run was torn down. */
    public void clearAll(ServerLevel level) {
        for (Grab g : grabs) {
            ServerPlayer p = playerOf(level, g.player);
            if (p != null) {
                clearEffects(p);
            }
        }
        grabs.clear();
    }
}
