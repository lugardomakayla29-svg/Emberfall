package com.solme.emberfall.combat;

import com.solme.emberfall.item.Loadout;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * The Twin Daggers' ultimate in motion: PHANTOM BLADES. See {@link PhantomBlades} for the rules. Two ghost daggers follow the wielder for a few
 * seconds and dart between foes, cutting each one.
 *
 * ZERO EXTRA ENTITIES: a blade is a position in a per-player record, drawn as a short particle trail. One entity query per player per tick (not per
 * blade). Every cut goes through {@link Loadout#acting} so its kills count for the daggers and feed their meter, Rend stacks and tomes apply.
 * State is removed when the ultimate ends, when the wielder dies or leaves, and at run end ({@link #clear}).
 */
public final class PhantomBladeSystem {
    private static final boolean TEST_MODE = Boolean.getBoolean("emberfall.testMode");
    /** Test-only control: with -Demberfall.noPhantom=true the ultimate does not start, so a test can measure the daggers alone. Inert outside test mode. */
    private static final boolean MUTE_FOR_CONTROL = TEST_MODE && Boolean.getBoolean("emberfall.noPhantom");
    private static final DustParticleOptions GHOST = new DustParticleOptions(0x9FE8FF, 1.1F);
    private static final Map<UUID, State> ACTIVE = new ConcurrentHashMap<>();

    private PhantomBladeSystem() {}

    private static final class State {
        final int level;
        final float hitDamage;
        final int slotIndex;
        final long endsAt;
        final double[][] pos = new double[PhantomBlades.BLADES][3];
        final long[] lastCut = new long[PhantomBlades.BLADES];
        int cuts;
        int darts;         // debug: how many times a blade picked a new foe
        final UUID[] lastFoe = new UUID[PhantomBlades.BLADES];
        final int[] cutsOnFoe = new int[PhantomBlades.BLADES];
        /** How many times the pair has settled on each foe this ultimate: the less-visited foe is preferred, so they cover the whole crowd. */
        final java.util.Map<UUID, Integer> visits = new java.util.HashMap<>();
        final UUID[] previousFoe = new UUID[PhantomBlades.BLADES];

        State(int level, float hitDamage, int slotIndex, long endsAt) {
            this.level = level;
            this.hitDamage = hitDamage;
            this.slotIndex = slotIndex;
            this.endsAt = endsAt;
            java.util.Arrays.fill(lastCut, Long.MIN_VALUE / 2);
        }
    }

    /** Starts (or restarts, if already running) the ultimate for {@code player}. Returns the ticks it will run. */
    public static int start(ServerLevel level, ServerPlayer player, int lvl, float hitDamage, int slotIndex) {
        if (MUTE_FOR_CONTROL) {
            return 0;
        }
        int ticks = PhantomBlades.durationTicks(lvl);
        State s = new State(lvl, hitDamage, slotIndex, level.getGameTime() + ticks);
        for (int i = 0; i < PhantomBlades.BLADES; i++) {
            double[] rest = rest(player, i);
            s.pos[i][0] = rest[0];
            s.pos[i][1] = rest[1];
            s.pos[i][2] = rest[2];
        }
        ACTIVE.put(player.getUUID(), s);
        level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.PLAYER_ATTACK_SWEEP, SoundSource.PLAYERS, 1.0F, 1.8F);
        level.sendParticles(ParticleTypes.SWEEP_ATTACK, player.getX(), player.getY() + 1.0, player.getZ(), 1, 0.0, 0.0, 0.0, 0.0);
        return ticks;
    }

    public static boolean active(ServerPlayer player) {
        return ACTIVE.containsKey(player.getUUID());
    }

    public static void clear(ServerPlayer player) {
        ACTIVE.remove(player.getUUID());
    }

    /** The world position blade {@code i} rests at beside {@code player}. */
    private static double[] rest(ServerPlayer player, int i) {
        double yaw = Math.toRadians(player.getYRot());
        double[] off = PhantomBlades.restingOffset(i, yaw);
        return new double[] {player.getX() + off[0], player.getY() + off[1], player.getZ() + off[2]};
    }

    public static void tickAll(MinecraftServer server) {
        if (ACTIVE.isEmpty()) {
            return;
        }
        for (ServerLevel level : com.solme.emberfall.world.RunManager.activeLevels()) {
            for (ServerPlayer player : level.players()) {
                State s = ACTIVE.get(player.getUUID());
                if (s == null) {
                    continue;
                }
                if (player.isSpectator() || player.isDeadOrDying() || level.getGameTime() >= s.endsAt
                        || com.solme.emberfall.world.RunManager.slotOf(player) == null) {
                    finish(level, player, s);
                    continue;
                }
                Loadout.acting(player, s.slotIndex, () -> tick(level, player, s));
            }
        }
    }

    private static void finish(ServerLevel level, ServerPlayer player, State s) {
        ACTIVE.remove(player.getUUID());
        for (int i = 0; i < PhantomBlades.BLADES; i++) {
            level.sendParticles(ParticleTypes.POOF, s.pos[i][0], s.pos[i][1], s.pos[i][2], 4, 0.15, 0.15, 0.15, 0.02);
        }
        if (TEST_MODE) {
            // permanent trace read by bot/phantom_test.js
            com.solme.emberfall.EmberfallMod.LOGGER.info("PHANTOM_TEST level={} cuts={} darts={} ticks={}", s.level, s.cuts, s.darts,
                    PhantomBlades.durationTicks(s.level));
        }
    }

    private static void tick(ServerLevel level, ServerPlayer player, State s) {
        final long now = level.getGameTime();
        final double range = PhantomBlades.range(s.level);
        final Vec3 wielder = player.position();
        // ONE query for both blades: live hostiles within range of the wielder (blades hunt near him, so they follow him).
        List<Mob> foes = level.getEntitiesOfClass(Mob.class, AABB.ofSize(wielder.add(0, 1.0, 0), range * 2, range * 2, range * 2),
                m -> m.isAlive() && AutoAttackSystem.isEmberfallHostile(m) && PhantomBlades.inRange(m.position().distanceTo(wielder), s.level));
        final int gap = PhantomBlades.cutGap(s.level);
        final float damage = (float) (s.hitDamage * PhantomBlades.cutMultiple(s.level));
        for (int i = 0; i < PhantomBlades.BLADES; i++) {
            double[] at = s.pos[i];
            Mob prey = pickPrey(foes, at, i, s);
            double[] goal;
            if (prey != null) {
                goal = new double[] {prey.getX(), prey.getY() + prey.getBbHeight() * 0.5, prey.getZ()};
            } else {
                goal = rest(player, i);
                s.lastFoe[i] = null;
            }
            double[] next = PhantomBlades.stepToward(at, goal, PhantomBlades.BLADE_SPEED);
            trail(level, at, next);
            at[0] = next[0];
            at[1] = next[1];
            at[2] = next[2];
            if (prey != null) {
                double dist = Math.sqrt(sq(at[0] - goal[0]) + sq(at[1] - goal[1]) + sq(at[2] - goal[2]));
                if (PhantomBlades.mayCut(dist, now, s.lastCut[i], gap)) {
                    s.lastCut[i] = now;
                    float before = prey.getHealth();
                    AutoAttackSystem.clearInvulnerabilityWindow(prey);
                    prey.hurtServer(level, player.damageSources().playerAttack(player), damage);
                    OnHitEffects.apply(player, prey, Math.max(0.0F, before - prey.getHealth()));
                    AutoAttackSystem.applyRend(prey);
                    s.cuts++;
                    s.cutsOnFoe[i]++;
                    level.sendParticles(ParticleTypes.CRIT, goal[0], goal[1], goal[2], 3, 0.2, 0.2, 0.2, 0.1);
                    if (s.cuts % 4 == 1) {
                        level.playSound(null, goal[0], goal[1], goal[2], SoundEvents.PLAYER_ATTACK_CRIT, SoundSource.PLAYERS, 0.5F, 1.7F);
                    }
                }
            }
        }
    }

    /**
     * Who blade {@code i} goes after: the foe nearest to IT, except that the two blades prefer different foes when there is more than one (so
     * they spread over a crowd and do not stack on the same one). A blade keeps its foe while it lives and stays in range.
     */
    private static Mob pickPrey(List<Mob> foes, double[] at, int i, State s) {
        if (foes.isEmpty()) {
            return null;
        }
        UUID keep = s.lastFoe[i];
        if (keep != null && s.cutsOnFoe[i] < PhantomBlades.CUTS_PER_FOE) {
            for (Mob m : foes) {
                if (m.getUUID().equals(keep)) {
                    return m;
                }
            }
        }
        if (keep != null) {
            s.previousFoe[i] = keep;     // it has had its cuts (or the foe is gone): remember it so the next pick goes elsewhere
        }
        s.cutsOnFoe[i] = 0;
        UUID other = s.lastFoe[1 - i];
        Mob best = null;
        double bestD = Double.MAX_VALUE;
        for (Mob m : foes) {
            double d = sq(m.getX() - at[0]) + sq(m.getY() + m.getBbHeight() * 0.5 - at[1]) + sq(m.getZ() - at[2]);
            if (foes.size() > 1 && m.getUUID().equals(other)) {
                d += 400.0;   // the other blade already has this one: strongly prefer another
            }
            if (foes.size() > 1 && m.getUUID().equals(s.previousFoe[i])) {
                d += 900.0;   // the one it just left: go somewhere new first
            }
            // coverage: every earlier visit to this foe adds a penalty bigger than any in-range distance (14 blocks = 196), so a foe nobody has
            // visited yet always beats one already visited, and the pair works through the whole crowd before coming back
            d += 1000.0 * s.visits.getOrDefault(m.getUUID(), 0);
            if (d < bestD) {
                bestD = d;
                best = m;
            }
        }
        if (best != null) {
            s.lastFoe[i] = best.getUUID();
            s.visits.merge(best.getUUID(), 1, Integer::sum);
            s.darts++;
        }
        return best;
    }

    private static double sq(double v) {
        return v * v;
    }

    /** A short ghost-blue trail from where the blade was to where it is now (at most 6 points). */
    private static void trail(ServerLevel level, double[] a, double[] b) {
        double d = Math.sqrt(sq(b[0] - a[0]) + sq(b[1] - a[1]) + sq(b[2] - a[2]));
        int points = (int) Math.max(1, Math.min(6, Math.ceil(d * 2)));
        for (int k = 0; k <= points; k++) {
            double t = (double) k / points;
            level.sendParticles(k == points ? ParticleTypes.END_ROD : GHOST, a[0] + (b[0] - a[0]) * t, a[1] + (b[1] - a[1]) * t,
                    a[2] + (b[2] - a[2]) * t, 1, 0.0, 0.0, 0.0, 0.0);
        }
    }
}
