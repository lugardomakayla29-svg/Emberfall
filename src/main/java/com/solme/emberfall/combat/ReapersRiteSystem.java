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

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * The Spectral Sickles' ultimate in motion: REAPER'S RITE. See {@link ReapersRite} for the rules and the phases.
 *
 * ZERO EXTRA ENTITIES. The pile point, the sickles and the disc are plain numbers in a per-player record drawn with particles. One entity query
 * per player per tick. While a Rite runs, {@link OrbitWeaponSystem} stands down for that player (see {@link #active}) so the ordinary ring does not
 * also cut. Every cut goes through {@link Loadout#acting} so kills count for the sickles. State ends with the Rite, on death or run end.
 */
public final class ReapersRiteSystem {
    private static final boolean TEST_MODE = Boolean.getBoolean("emberfall.testMode");
    /** Test-only control: with -Demberfall.noRite=true the ultimate does not start, so a test can measure the ordinary ring alone. Inert outside test mode. */
    private static final boolean MUTE_FOR_CONTROL = TEST_MODE && Boolean.getBoolean("emberfall.noRite");
    private static final DustParticleOptions SOUL_BLUE = new DustParticleOptions(0x6FE7FF, 1.3F);
    private static final Map<UUID, State> ACTIVE = new ConcurrentHashMap<>();

    private ReapersRiteSystem() {}

    private static final class State {
        final int level;
        final float hitDamage;
        final int slotIndex;
        final long startedAt;
        double pileX, pileZ, pileY;
        boolean pileSet;
        int cuts, pulled, maxInDisc;
        final java.util.Set<UUID> ever = new java.util.HashSet<>();

        State(int level, float hitDamage, int slotIndex, long startedAt) {
            this.level = level;
            this.hitDamage = hitDamage;
            this.slotIndex = slotIndex;
            this.startedAt = startedAt;
        }
    }

    /** Starts the Rite. {@code hitDamage} is what one sickle cut deals. Returns the ticks it will run (0 if muted for a control run). */
    public static int start(ServerLevel level, ServerPlayer player, int lvl, float hitDamage, int slotIndex) {
        if (MUTE_FOR_CONTROL) {
            return 0;
        }
        ACTIVE.put(player.getUUID(), new State(lvl, hitDamage, slotIndex, level.getGameTime()));
        level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.WARDEN_SONIC_CHARGE, SoundSource.PLAYERS, 1.2F, 1.4F);
        return ReapersRite.totalTicks(lvl);
    }

    public static boolean active(ServerPlayer player) {
        return ACTIVE.containsKey(player.getUUID());
    }

    public static void clear(ServerPlayer player) {
        ACTIVE.remove(player.getUUID());
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
                int t = (int) (level.getGameTime() - s.startedAt);
                if (player.isSpectator() || player.isDeadOrDying() || com.solme.emberfall.world.RunManager.slotOf(player) == null) {
                    ACTIVE.remove(player.getUUID());
                    continue;
                }
                if (t >= ReapersRite.totalTicks(s.level)) {
                    Loadout.acting(player, s.slotIndex, () -> shatter(level, player, s));
                    ACTIVE.remove(player.getUUID());
                    continue;
                }
                Loadout.acting(player, s.slotIndex, () -> tick(level, player, s, t));
            }
        }
    }

    private static List<Mob> foesAround(ServerLevel level, Vec3 centre, double radius) {
        List<Mob> found = level.getEntitiesOfClass(Mob.class, new AABB(centre, centre).inflate(radius, 6.0, radius),
                m -> m.isAlive() && AutoAttackSystem.isTargetable(m) && flat(m.position(), centre) <= radius);
        if (found.size() > ReapersRite.MAX_FOES) {
            found.sort(java.util.Comparator.comparingDouble(m -> flat(m.position(), centre)));
            return new ArrayList<>(found.subList(0, ReapersRite.MAX_FOES));
        }
        return found;
    }

    private static double flat(Vec3 a, Vec3 b) {
        return Math.hypot(a.x - b.x, a.z - b.z);
    }

    private static void tick(ServerLevel level, ServerPlayer player, State s, int t) {
        final Vec3 wielder = player.position();
        if (!s.pileSet) {
            // choose the pile at the start, from the foes in gather range
            List<Mob> around = foesAround(level, wielder, ReapersRite.gatherRange(s.level));
            List<double[]> pts = new ArrayList<>();
            for (Mob m : around) {
                pts.add(new double[] {m.getX(), m.getZ()});
            }
            double[] p = ReapersRite.pilePoint(pts, wielder.x, wielder.z);
            if (p == null) {
                // nobody to gather: the Rite still plays, on the spot in front of the wielder, so the ultimate is never wasted silently
                double yaw = Math.toRadians(player.getYRot());
                p = new double[] {wielder.x - Math.sin(yaw) * 5.0, wielder.z + Math.cos(yaw) * 5.0};
            }
            s.pileX = p[0];
            s.pileZ = p[1];
            s.pileY = wielder.y;
            s.pileSet = true;
        }
        final Vec3 pile = new Vec3(s.pileX, s.pileY, s.pileZ);
        final boolean gathering = t < ReapersRite.GATHER_TICKS;
        final double reach = gathering ? ReapersRite.gatherRange(s.level) + 4.0 : ReapersRite.discRadius(s.level);
        // The pile stays where it is; foes are found around the PILE once gathering is done, around the wielder's range while gathering.
        List<Mob> foes = gathering ? foesAround(level, wielder, ReapersRite.gatherRange(s.level)) : foesAround(level, pile, ReapersRite.discRadius(s.level) + 1.5);
        if (gathering) {
            if (ReapersRite.pullTick(t)) {
                for (Mob m : foes) {
                    if (ReapersRite.shouldPull(flat(m.position(), pile))) {
                        AutoAttackSystem.pullToward(m, pile, ReapersRite.GATHER_PULL);
                        s.pulled++;
                    }
                    s.ever.add(m.getUUID());
                }
            }
            drawGather(level, wielder, pile, t, s.level);
        } else {
            if (t % 2 == 0) {
                for (Mob m : foes) {
                    if (flat(m.position(), pile) > ReapersRite.PILE_STOP) {
                        AutoAttackSystem.pullToward(m, pile, ReapersRite.SLICE_KEEP_PULL);
                    }
                }
            }
            if (ReapersRite.cutTick(t)) {
                final float damage = (float) (s.hitDamage * ReapersRite.cutMultiple(s.level));
                final double disc = ReapersRite.discRadius(s.level);
                int inDisc = 0;
                for (Mob m : foes) {
                    if (flat(m.position(), pile) <= disc) {
                        inDisc++;
                        float before = m.getHealth();
                        AutoAttackSystem.clearInvulnerabilityWindow(m);
                        Vec3 keep = m.getDeltaMovement();
                        m.hurtServer(level, player.damageSources().playerAttack(player), damage);
                        m.setDeltaMovement(keep);          // the cut must not fling the foe out of the pile
                        OnHitEffects.apply(player, m, Math.max(0.0F, before - m.getHealth()));
                        s.cuts++;
                        s.ever.add(m.getUUID());
                    }
                }
                s.maxInDisc = Math.max(s.maxInDisc, inDisc);
                if (inDisc > 0) {
                    level.playSound(null, pile.x, pile.y, pile.z, SoundEvents.PLAYER_ATTACK_SWEEP, SoundSource.PLAYERS, 0.6F, 1.3F);
                }
            }
            drawDisc(level, pile, t - ReapersRite.GATHER_TICKS, s.level);
        }
    }

    /** The last tick: the sickles break. One big hit on everything in the disc, a ring of soul fire and a shatter sound. */
    private static void shatter(ServerLevel level, ServerPlayer player, State s) {
        final Vec3 pile = new Vec3(s.pileX, s.pileY, s.pileZ);
        final double disc = ReapersRite.discRadius(s.level);
        final float damage = (float) (s.hitDamage * ReapersRite.breakMultiple(s.level));
        int hit = 0;
        for (Mob m : foesAround(level, pile, disc + 1.5)) {
            if (flat(m.position(), pile) <= disc + 1.0) {
                float before = m.getHealth();
                AutoAttackSystem.clearInvulnerabilityWindow(m);
                m.hurtServer(level, player.damageSources().playerAttack(player), damage);
                OnHitEffects.apply(player, m, Math.max(0.0F, before - m.getHealth()));
                hit++;
            }
        }
        for (int i = 0; i < 48; i++) {
            double a = Math.PI * 2 * i / 48.0;
            level.sendParticles(ParticleTypes.SOUL_FIRE_FLAME, pile.x + Math.cos(a) * disc, pile.y + 0.3, pile.z + Math.sin(a) * disc, 1, 0.0, 0.15, 0.0, 0.02);
        }
        level.sendParticles(ParticleTypes.EXPLOSION, pile.x, pile.y + 0.6, pile.z, 2, disc * 0.3, 0.2, disc * 0.3, 0.0);
        level.playSound(null, pile.x, pile.y, pile.z, SoundEvents.GLASS_BREAK, SoundSource.PLAYERS, 1.2F, 0.6F);
        level.playSound(null, pile.x, pile.y, pile.z, SoundEvents.GENERIC_EXPLODE.value(), SoundSource.PLAYERS, 0.8F, 0.9F);
        if (TEST_MODE) {
            // permanent trace read by bot/rite_test.js
            com.solme.emberfall.EmberfallMod.LOGGER.info("RITE_TEST level={} cuts={} pulled={} foes={} maxInDisc={} shatterHit={} ticks={}",
                    s.level, s.cuts, s.pulled, s.ever.size(), s.maxInDisc, hit, ReapersRite.totalTicks(s.level));
        }
    }

    /** GATHER drawing: two sickles spiral from the wielder to the pile, with a soul trail, and a growing ring marks the pile. */
    private static void drawGather(ServerLevel level, Vec3 wielder, Vec3 pile, int t, int lvl) {
        double k = Math.min(1.0, (t + 1.0) / ReapersRite.GATHER_TICKS);
        for (int blade = 0; blade < 2; blade++) {
            double a = t * 0.55 + blade * Math.PI;
            double r = 2.5 * (1.0 - k) + 0.8;
            double x = wielder.x + (pile.x - wielder.x) * k + Math.cos(a) * r;
            double z = wielder.z + (pile.z - wielder.z) * k + Math.sin(a) * r;
            double y = wielder.y + 1.0;
            level.sendParticles(ParticleTypes.SOUL_FIRE_FLAME, x, y, z, 1, 0.02, 0.02, 0.02, 0.0);
            level.sendParticles(SOUL_BLUE, x, y, z, 1, 0.05, 0.05, 0.05, 0.0);
        }
        double ring = ReapersRite.discRadius(lvl) * k;
        for (int i = 0; i < 14; i++) {
            double a = Math.PI * 2 * i / 14.0 + t * 0.2;
            level.sendParticles(ParticleTypes.SOUL, pile.x + Math.cos(a) * ring, pile.y + 0.2, pile.z + Math.sin(a) * ring, 1, 0.0, 0.02, 0.0, 0.0);
        }
    }

    /**
     * SLICE drawing: a FULL disc, not an outline. Sickles sweep as spokes from the centre to the edge, several rings fill the inside, and the two
     * sickle heads orbit the rim. 44 particles a tick, flat whatever the level.
     */
    private static void drawDisc(ServerLevel level, Vec3 pile, int sliceTick, int lvl) {
        double disc = ReapersRite.discRadius(lvl);
        double spin = sliceTick * 0.9;
        for (int spoke = 0; spoke < 4; spoke++) {
            double a = spin + spoke * Math.PI / 2.0;
            for (int p = 1; p <= 4; p++) {
                double r = disc * p / 4.0;
                level.sendParticles(p == 4 ? ParticleTypes.SOUL_FIRE_FLAME : SOUL_BLUE, pile.x + Math.cos(a) * r, pile.y + 0.6 + 0.1 * p,
                        pile.z + Math.sin(a) * r, 1, 0.0, 0.0, 0.0, 0.0);
            }
        }
        for (int ring = 1; ring <= 3; ring++) {
            double r = disc * ring / 3.0;
            for (int i = 0; i < 6; i++) {
                double a = -spin * 0.5 + Math.PI * 2 * i / 6.0 + ring;
                level.sendParticles(ParticleTypes.SOUL, pile.x + Math.cos(a) * r, pile.y + 0.3, pile.z + Math.sin(a) * r, 1, 0.0, 0.03, 0.0, 0.0);
            }
        }
        for (int blade = 0; blade < 2; blade++) {
            double a = spin * 1.6 + blade * Math.PI;
            level.sendParticles(ParticleTypes.END_ROD, pile.x + Math.cos(a) * disc, pile.y + 1.0, pile.z + Math.sin(a) * disc, 1, 0.0, 0.0, 0.0, 0.0);
            level.sendParticles(ParticleTypes.SWEEP_ATTACK, pile.x + Math.cos(a) * disc * 0.7, pile.y + 0.9, pile.z + Math.sin(a) * disc * 0.7, 1, 0.0, 0.0, 0.0, 0.0);
        }
    }
}
