package com.solme.emberfall.combat;

import com.solme.emberfall.item.Loadout;
import com.solme.emberfall.item.WeaponGrowth;
import com.solme.emberfall.item.WeaponProgress;
import com.solme.emberfall.world.DelayedTasks;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * The Gravechain's growth. The chain was one hook per hit and a pile on every 4th. Levels make it a crowd weapon:
 *
 * EXTRA CHAINS, fractional: every hit throws 0.25 per level above 1 extra chains (0 at level 1, 2.25 at level 10). 1.5 means one extra
 * chain and a 50% chance of a second ({@link WeaponGrowth#roll}). Each extra chain hooks ANOTHER foe within chain reach, deals 65% of the
 * hit, yanks it toward the wielder and draws its own soul-fire line, 2 ticks apart.
 * BIGGER GATHER: the every-4th pile reaches 6.0 to 9.0 blocks and catches 4 to 8 foes ({@link #gatherRadius}, {@link #gatherCap}).
 * LONGER CHAIN: reach 4.5 to 7.0.
 *
 * ULTIMATE, Grave Vortex: the meter fills from the chain's own hits and kills. When full, the next landed hit opens a vortex on the target.
 * For 3 seconds, every 4 ticks, each foe within 8.0 + 0.3 per level blocks is dragged toward the centre and cut for 1.0 + 0.15 per level
 * times the hit, under a spiral of soul fire. It fires on its own.
 *
 * NO EXTRA ENTITIES: chains are particle lines, the vortex is a run of tasks on the bounded {@link DelayedTasks} queue, and every later
 * step re-enters the chain's slot with {@link Loadout#acting} so its kills count for the chain. The Grave Anchor Tome is not touched:
 * extra chains neither start nor extend its pile record.
 */
public final class ChainSystem {
    public static final double EXTRA_CHAINS_PER_LEVEL = 0.3;
    public static final double EXTRA_CHAIN_FRACTION = 0.65;
    public static final double GATHER_RADIUS_BASE = 6.0;
    public static final double GATHER_RADIUS_PER_LEVEL = 3.0 / 9.0;
    public static final int GATHER_CAP_BASE = 4;
    public static final double GATHER_CAP_PER_LEVEL = 4.0 / 9.0;
    public static final double RANGE_PER_LEVEL = 2.5 / 9.0;
    public static final double VORTEX_RADIUS_BASE = 8.0;
    public static final double VORTEX_RADIUS_PER_LEVEL = 0.3;
    public static final double VORTEX_MULTIPLE_BASE = 1.0;
    public static final double VORTEX_MULTIPLE_PER_LEVEL = 0.15;
    public static final double VORTEX_PULL = 0.55;
    public static final int VORTEX_PULSES = 15;
    public static final int VORTEX_STEP_TICKS = 4;
    public static final int STEP_TICKS = 2;
    private static final boolean TEST_MODE = Boolean.getBoolean("emberfall.testMode");
    /** Test-only control: with -Demberfall.noLegion=true nobody is raised, so a test can measure the vortex alone. Inert outside test mode. */
    private static final boolean MUTE_FOR_CONTROL = TEST_MODE && Boolean.getBoolean("emberfall.noLegion");

    private ChainSystem() {}

    /** Extra reach for a chain of this level: 0 at level 1 up to 2.5 at level 10. */
    public static double rangeBonus(int level) {
        return WeaponGrowth.scale(0.0, RANGE_PER_LEVEL, level);
    }

    /** Average extra chains per hit: 0 at level 1, 2.25 at level 10. */
    public static double extraChains(int level) {
        return WeaponGrowth.scale(0.0, EXTRA_CHAINS_PER_LEVEL, level);
    }

    public static double gatherRadius(int level) {
        return WeaponGrowth.scale(GATHER_RADIUS_BASE, GATHER_RADIUS_PER_LEVEL, level);
    }

    /** How many foes the every-4th pile may catch: 4 at level 1 up to 8 at level 10, the fraction rolled by the caller's random. */
    public static int gatherCap(int level, ServerLevel world) {
        return WeaponGrowth.roll(WeaponGrowth.scale(GATHER_CAP_BASE, GATHER_CAP_PER_LEVEL, level), AutoAttackSystem.levelRandom(world));
    }

    public static double vortexRadius(int level) {
        return WeaponGrowth.scale(VORTEX_RADIUS_BASE, VORTEX_RADIUS_PER_LEVEL, level);
    }

    public static double vortexMultiple(int level) {
        return WeaponGrowth.scale(VORTEX_MULTIPLE_BASE, VORTEX_MULTIPLE_PER_LEVEL, level);
    }

    /**
     * After a landed hit of {@code damage} on {@code target}: adds meter and, if it is now full, opens the vortex on the target; otherwise
     * throws this hit's extra chains at other foes within {@code reach}. Runs inside the swing, so the chain is the acting slot.
     */
    public static void afterHit(ServerLevel level, ServerPlayer player, LivingEntity target, float damage, double reach) {
        if (damage <= 0.0F) {
            return;
        }
        int lvl = WeaponProgress.levelOf(player);
        WeaponProgress.onHit(player);
        final int slotIndex = Loadout.actingIndexFor(player);
        if (WeaponProgress.ultimateReady(player) && WeaponProgress.consumeUltimate(player)) {
            vortex(level, player, target.position(), lvl, damage, slotIndex);
            return;
        }
        int count = WeaponGrowth.roll(extraChains(lvl) + com.solme.emberfall.relic.RelicTempoGame.bonusStrikes(player), AutoAttackSystem.levelRandom(level));
        if (count <= 0) {
            return;
        }
        List<Mob> foes = foesIn(level, player.position(), reach, target);
        // Echo: a lone foe is chained again and dragged in; each chain grows with the level.
        List<LivingEntity> victims = Echo.victims(target, foes, count);
        for (int i = 0; i < victims.size(); i++) {
            final LivingEntity victim = victims.get(i);
            final float chainDamage = (float) (damage * EXTRA_CHAIN_FRACTION * Echo.lift(lvl));
            DelayedTasks.runLater(level, STEP_TICKS * (i + 1), o -> extraChain(level, player, victim, chainDamage, slotIndex));
        }
    }

    /** One extra chain: a soul-fire line to the foe, damage, a yank toward the wielder. Dropped silently if either is gone. */
    private static void extraChain(ServerLevel level, ServerPlayer player, LivingEntity victim, float damage, int slotIndex) {
        if (!victim.isAlive() || !player.isAlive()) {
            return;
        }
        Loadout.acting(player, slotIndex, () -> {
            float before = victim.getHealth();
            AutoAttackSystem.clearInvulnerabilityWindow(victim);
            victim.hurtServer(level, player.damageSources().playerAttack(player), damage);
            OnHitEffects.apply(player, victim, Math.max(0.0F, before - victim.getHealth()));
            AutoAttackSystem.pullToward(victim, player.position(), 0.65);
        });
        WeaponFx.chainLine(level, player, victim);
        level.playSound(null, victim.getX(), victim.getY(), victim.getZ(), SoundEvents.CHAIN_HIT, SoundSource.PLAYERS, 0.6F, 1.0F);
    }

    /** Opens the Grave Vortex at {@code centre}. Returns how many pulses were queued. */
    static int vortex(ServerLevel level, ServerPlayer player, Vec3 centre, int lvl, float hitDamage, int slotIndex) {
        final double radius = vortexRadius(lvl);
        final float damage = (float) (hitDamage * vortexMultiple(lvl));
        // One legion per vortex, shared by its pulses: foes that die inside it are raised and fight until it ends.
        final Legion legion = new Legion(lvl, hitDamage);
        int queued = 0;
        for (int i = 0; i < VORTEX_PULSES; i++) {
            final int pulse = i;
            if (DelayedTasks.runLater(level, VORTEX_STEP_TICKS * (i + 1), o -> pulse(level, player, centre, radius, damage, slotIndex, pulse, legion))) {
                queued++;
            }
        }
        level.playSound(null, centre.x, centre.y, centre.z, SoundEvents.WARDEN_SONIC_CHARGE, SoundSource.PLAYERS, 1.2F, 0.7F);
        return queued;
    }

    /** The state of one vortex's raised dead. Plain data: no entities, so it costs a list and some particles. */
    static final class Legion {
        final int level;
        final double hitDamage;
        /** Kind of each standing revenant, with where it stands and how many strike steps it has left. */
        final List<GraveLegion.Kind> kinds = GraveLegion.newLegion();
        final List<Vec3> spots = new ArrayList<>();
        final List<Integer> life = new ArrayList<>();
        int raised;        // debug: total ever raised
        int strikes;       // debug: total blows landed

        Legion(int level, double hitDamage) {
            this.level = level;
            this.hitDamage = hitDamage;
        }

        boolean raise(GraveLegion.Kind kind, Vec3 at) {
            if (MUTE_FOR_CONTROL || !GraveLegion.raise(kinds, kind, level)) {
                return false;
            }
            spots.add(at);
            life.add(GraveLegion.LIFE_STEPS);
            raised++;
            return true;
        }

        void drop(int i) {
            kinds.remove(i);
            spots.remove(i);
            life.remove(i);
        }
    }

    /** One vortex pulse: drag every foe in radius toward the centre, cut it, raise what died, let the dead strike, draw a soul-fire spiral. */
    private static void pulse(ServerLevel level, ServerPlayer player, Vec3 centre, double radius, float damage, int slotIndex, int pulse, Legion legion) {
        if (!player.isAlive()) {
            return;
        }
        Loadout.acting(player, slotIndex, () -> {
            List<Mob> foes = foesIn(level, centre, radius, null);
            for (Mob mob : foes) {
                float before = mob.getHealth();
                AutoAttackSystem.clearInvulnerabilityWindow(mob);
                mob.hurtServer(level, player.damageSources().playerAttack(player), damage);
                OnHitEffects.apply(player, mob, Math.max(0.0F, before - mob.getHealth()));
                AutoAttackSystem.pullToward(mob, centre, VORTEX_PULL);
            }
            // RAISE: everything that was alive before the cut and is dead now stands up as a revenant (while there is room).
            for (Mob mob : foes) {
                if (!mob.isAlive()) {
                    var key = BuiltInRegistries.ENTITY_TYPE.getKey(mob.getType());
                    GraveLegion.Kind kind = GraveLegion.kindOf(key == null ? "" : key.getPath(), mob.getMaxHealth());
                    if (legion.raise(kind, mob.position())) {
                        level.sendParticles(ParticleTypes.SOUL, mob.getX(), mob.getY() + 0.8, mob.getZ(), 10, 0.3, 0.5, 0.3, 0.04);
                    }
                }
            }
            // STRIKE: each standing revenant lunges at the nearest living foe; one that finds nobody waits, and all fade with time.
            legionStrike(level, player, centre, radius, legion);
            if (TEST_MODE && pulse == VORTEX_PULSES - 1) {
                // permanent trace read by bot/legion_test.js: how many were raised, how many blows they landed, how many still stand
                com.solme.emberfall.EmberfallMod.LOGGER.info("LEGION_TEST level={} raised={} strikes={} standing={} cap={}",
                        legion.level, legion.raised, legion.strikes, legion.kinds.size(), GraveLegion.cap(legion.level));
            }
        });
        // a spiral of two arms whose phase turns with the pulse; 12 points a pulse keeps the packet cost flat
        for (int arm = 0; arm < 2; arm++) {
            for (int p = 0; p < 6; p++) {
                double r = radius * (p + 1) / 6.0;
                double a = pulse * 0.6 + arm * Math.PI + p * 0.55;
                level.sendParticles(ParticleTypes.SOUL_FIRE_FLAME, centre.x + Math.cos(a) * r, centre.y + 0.4, centre.z + Math.sin(a) * r,
                        1, 0.05, 0.05, 0.05, 0.0);
            }
        }
    }

    /** All standing revenants strike once. Runs inside the chain's acting slot, so their kills count for the chain. */
    private static void legionStrike(ServerLevel level, ServerPlayer player, Vec3 centre, double radius, Legion legion) {
        if (legion.kinds.isEmpty()) {
            return;
        }
        List<Mob> targets = foesIn(level, centre, radius, null);
        for (int i = legion.kinds.size() - 1; i >= 0; i--) {
            GraveLegion.Kind kind = legion.kinds.get(i);
            Vec3 from = legion.spots.get(i);
            Mob victim = nearestTo(targets, from);
            if (victim != null) {
                float blow = GraveLegion.blow(kind, legion.hitDamage, legion.level);
                for (int h = 0; h < kind.hits; h++) {
                    float before = victim.getHealth();
                    AutoAttackSystem.clearInvulnerabilityWindow(victim);
                    victim.hurtServer(level, player.damageSources().playerAttack(player), blow);
                    OnHitEffects.apply(player, victim, Math.max(0.0F, before - victim.getHealth()));
                    legion.strikes++;
                }
                // the revenant flies to its victim: a streak of its own colour from where it stood to where it struck
                revenantStreak(level, from, victim.position().add(0.0, 0.8, 0.0), kind.colour);
                legion.spots.set(i, victim.position());
                if (!victim.isAlive()) {
                    targets.remove(victim);
                }
            }
            int left = legion.life.get(i) - 1;
            if (left <= 0) {
                level.sendParticles(ParticleTypes.SOUL, from.x, from.y + 0.6, from.z, 6, 0.25, 0.3, 0.25, 0.02);
                legion.drop(i);
            } else {
                legion.life.set(i, left);
            }
        }
    }

    private static Mob nearestTo(List<Mob> foes, Vec3 from) {
        Mob best = null;
        double bestD = Double.MAX_VALUE;
        for (Mob m : foes) {
            if (!m.isAlive()) {
                continue;
            }
            double d = m.position().distanceToSqr(from);
            if (d < bestD) {
                bestD = d;
                best = m;
            }
        }
        return best;
    }

    /** A short dotted streak between two points in the revenant's colour (6 dust points: bounded cost). */
    private static void revenantStreak(ServerLevel level, Vec3 a, Vec3 b, int rgb) {
        DustParticleOptions dust = new DustParticleOptions(rgb, 1.2F);
        for (int i = 0; i <= 5; i++) {
            double t = i / 5.0;
            level.sendParticles(dust, a.x + (b.x - a.x) * t, a.y + 0.8 + (b.y - a.y - 0.8) * t, a.z + (b.z - a.z) * t, 1, 0.0, 0.0, 0.0, 0.0);
        }
    }

    /** Live hostiles within {@code reach} of {@code centre}, nearest first, other than {@code skip}. */
    public static List<Mob> foesIn(ServerLevel level, Vec3 centre, double reach, LivingEntity skip) {
        List<Mob> found = new ArrayList<>(level.getEntitiesOfClass(Mob.class, new AABB(centre, centre).inflate(reach),
                m -> m != skip && m.isAlive() && AutoAttackSystem.isEmberfallHostile(m) && m.position().distanceTo(centre) <= reach));
        found.sort(Comparator.comparingDouble(m -> m.position().distanceTo(centre)));
        return found;
    }
}
