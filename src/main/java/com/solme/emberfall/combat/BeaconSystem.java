package com.solme.emberfall.combat;

import com.solme.emberfall.item.Loadout;
import com.solme.emberfall.item.WeaponGrowth;
import com.solme.emberfall.world.DelayedTasks;
import net.minecraft.core.particles.ParticleTypes;
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
 * The Ashen Beacon's growth maths and its ultimate. {@link TotemWeaponSystem} owns the beacon records and the pulse loop and calls these.
 *
 * THE RULE IS KEPT: one MAIN beacon at a time, and the list of records never grows past the existing bound (1 main, 1 natural expiry ember,
 * 1 kill ember). Growth therefore does not add beacons. It makes the one beacon stronger and gives it SATELLITE FLAMES.
 *
 * PULSE: radius 3.0 to 5.0, interval 20 to 12 ticks, lifespan 200 to 280 ticks, range 8 to 11.
 * SATELLITES, fractional: 0.3 per level above 1 (0 at level 1, 2.7 at level 10). On every main pulse each satellite is a small fire burst
 * (radius 1.8, 60% of the pulse) on a different foe near the beacon. No records added: a satellite is an instant burst.
 * BURNING: a foe caught by a pulse is set alight for 2 seconds.
 *
 * ULTIMATE, Pyre Nova: the meter fills from the beacon's own pulse hits and kills. When full, the beacon erupts in 4 expanding rings, 6 ticks
 * apart, radii stepping out from 3 to 9 + 0.3 per level, each ring a band that burns everything inside it for 1.6 + 0.2 per level times the
 * pulse. It starts by itself, from the pulse that filled the meter. Kills are credited to the beacon by re-entering its slot.
 *
 * NO EXTRA ENTITIES: particles and tasks on the bounded {@link DelayedTasks} queue only.
 */
public final class BeaconSystem {
    public static final double RADIUS_BASE = 3.0;
    public static final double RADIUS_PER_LEVEL = 2.0 / 9.0;
    public static final int INTERVAL_BASE = 20;
    public static final double INTERVAL_PER_LEVEL = -8.0 / 9.0;
    public static final int LIFESPAN_BASE = 200;
    public static final double LIFESPAN_PER_LEVEL = 80.0 / 9.0;
    public static final double RANGE_PER_LEVEL = 3.0 / 9.0;
    public static final double SATELLITES_PER_LEVEL = 0.3;
    public static final double SATELLITE_RADIUS = 1.8;
    public static final double SATELLITE_FRACTION = 0.6;
    public static final int BURN_SECONDS = 2;
    public static final int NOVA_RINGS = 4;
    public static final int NOVA_STEP_TICKS = 6;
    public static final double NOVA_INNER = 3.0;
    public static final double NOVA_OUTER_BASE = 9.0;
    public static final double NOVA_OUTER_PER_LEVEL = 0.3;
    public static final double NOVA_MULTIPLE_BASE = 1.6;
    public static final double NOVA_MULTIPLE_PER_LEVEL = 0.2;

    // LIVING FLAMES: hunting fire that belongs to the one main beacon (no extra records, no entities, particles only).
    public static final double FLAMES_BASE = 1.0;
    public static final double FLAMES_PER_LEVEL = 3.0 / 9.0;
    public static final double FLAME_SPEED_BASE = 6.5;        // blocks per second: out-runs the fastest walker (a Bomber, about 6.2)
    public static final double FLAME_SPEED_PER_LEVEL = 4.5 / 9.0;
    public static final double FLAME_HUNT_BASE = 7.0;         // how far from the main beacon a flame will go after a foe
    public static final double FLAME_HUNT_PER_LEVEL = 5.0 / 9.0;
    public static final double FLAME_CONTACT_BASE = 1.4;
    public static final double FLAME_CONTACT_PER_LEVEL = 0.6 / 9.0;
    public static final double FLAME_PULL_BASE = 0.18;        // per pulse, chain vortex is 0.55 and the hook gather 0.9
    public static final double FLAME_PULL_PER_LEVEL = 0.17 / 9.0;
    public static final double FLAME_DAMAGE_FRACTION = 0.5;   // of the beacon's pulse, per contact
    public static final int FLAME_CONTACT_TICKS = 10;         // one contact per foe per half second
    public static final int FLAME_METER_GAIN = 12;            // meter per contact hit (a plain hit is 6, 1000 fills it)
    public static final int FLAME_DWELL_TICKS = 50;           // a flame keeps one target this long (2.5 s) before it may re-pick
    public static final double FLAME_CLUMP_RADIUS = 3.0;      // foes this close to a candidate count towards its group score

    private BeaconSystem() {}

    public static double radius(int level) {
        return WeaponGrowth.scale(RADIUS_BASE, RADIUS_PER_LEVEL, level);
    }

    public static int intervalTicks(int level) {
        return (int) Math.round(WeaponGrowth.scale(INTERVAL_BASE, INTERVAL_PER_LEVEL, level));
    }

    public static int lifespanTicks(int level) {
        return (int) Math.round(WeaponGrowth.scale(LIFESPAN_BASE, LIFESPAN_PER_LEVEL, level));
    }

    /** Extra reach for a beacon of this level: 0 at level 1 up to 3.0 at level 10. */
    public static double rangeBonus(int level) {
        return WeaponGrowth.scale(0.0, RANGE_PER_LEVEL, level);
    }

    /** Average satellite flames per pulse: 0 at level 1, 2.7 at level 10. */
    public static double satellites(int level) {
        return WeaponGrowth.scale(0.0, SATELLITES_PER_LEVEL, level);
    }

    public static double novaOuter(int level) {
        return WeaponGrowth.scale(NOVA_OUTER_BASE, NOVA_OUTER_PER_LEVEL, level);
    }

    public static double novaMultiple(int level) {
        return WeaponGrowth.scale(NOVA_MULTIPLE_BASE, NOVA_MULTIPLE_PER_LEVEL, level);
    }

    /** Average living flames for a beacon of this level: 1.0 at level 1 up to 4.0 at level 10. */
    public static double flames(int level) {
        return WeaponGrowth.scale(FLAMES_BASE, FLAMES_PER_LEVEL, level);
    }

    /** Blocks per TICK that a flame travels at this level. */
    public static double flameStep(int level) {
        return WeaponGrowth.scale(FLAME_SPEED_BASE, FLAME_SPEED_PER_LEVEL, level) / 20.0;
    }

    public static double flameHunt(int level) {
        return WeaponGrowth.scale(FLAME_HUNT_BASE, FLAME_HUNT_PER_LEVEL, level);
    }

    public static double flameContact(int level) {
        return WeaponGrowth.scale(FLAME_CONTACT_BASE, FLAME_CONTACT_PER_LEVEL, level);
    }

    public static double flamePull(int level) {
        return WeaponGrowth.scale(FLAME_PULL_BASE, FLAME_PULL_PER_LEVEL, level);
    }

    /**
     * Which foe a flame should go after: the one standing in the DENSEST group, nearer breaking ties. Score = 10 per foe within
     * {@link #FLAME_CLUMP_RADIUS} of the candidate (itself included) minus the distance from the flame. A foe the flame is ALREADY
     * touching ({@code contact}) is being burned, so it is only chosen when nothing else is left to go to: without that rule a flame
     * planted on a foe sat on it for the whole beacon and never hunted. Returns -1 when there is no foe. Pure maths on positions so it
     * is testable without a server.
     */
    public static int pickTarget(Vec3 flame, List<Vec3> foes, double contact) {
        int best = -1;
        double bestScore = -1.0E9;
        boolean anyAway = false;
        for (Vec3 f : foes) {
            if (flame.distanceTo(f) > contact) {
                anyAway = true;
                break;
            }
        }
        for (int i = 0; i < foes.size(); i++) {
            if (anyAway && flame.distanceTo(foes.get(i)) <= contact) {
                continue;
            }
            int group = 0;
            for (Vec3 other : foes) {
                if (other.distanceTo(foes.get(i)) <= FLAME_CLUMP_RADIUS) {
                    group++;
                }
            }
            double score = group * 10.0 - flame.distanceTo(foes.get(i));
            if (score > bestScore) {
                bestScore = score;
                best = i;
            }
        }
        return best;
    }

    /** Live hostiles within {@code reach} of {@code centre}, nearest first, other than {@code skip}. */
    public static List<Mob> foesIn(ServerLevel level, Vec3 centre, double reach, LivingEntity skip) {
        List<Mob> found = new ArrayList<>(level.getEntitiesOfClass(Mob.class, new AABB(centre, centre).inflate(reach),
                m -> m != skip && m.isAlive() && AutoAttackSystem.isTargetable(m) && m.position().distanceTo(centre) <= reach));
        found.sort(Comparator.comparingDouble(m -> m.position().distanceTo(centre)));
        return found;
    }

    /** Damages one foe for the owner, sets it alight, and returns the damage it actually took. */
    static float scorch(ServerLevel level, ServerPlayer owner, Mob mob, float damage) {
        float before = mob.getHealth();
        mob.invulnerableTime = 0;
        mob.hurtServer(level, owner.damageSources().playerAttack(owner), damage);
        float dealt = Math.max(0.0F, before - mob.getHealth());
        OnHitEffects.apply(owner, mob, dealt);
        if (mob.isAlive()) {
            mob.igniteForSeconds(BURN_SECONDS);
        }
        return dealt;
    }

    /**
     * The satellite flames of one main pulse: each burst lands on a different foe near the beacon (nearest first), as a small fire burst that
     * hits everything within {@link #SATELLITE_RADIUS} of that foe. Runs inside the beacon's slot, so the kills are the beacon's.
     */
    static void satelliteBursts(ServerLevel level, ServerPlayer owner, Vec3 beacon, double reach, int lvl, float pulseDamage, List<Mob> alreadyHit) {
        int count = WeaponGrowth.roll(satellites(lvl), AutoAttackSystem.levelRandom(level));
        if (count <= 0) {
            return;
        }
        List<Mob> foes = new ArrayList<>(foesIn(level, beacon, reach, null));
        foes.removeAll(alreadyHit);                     // satellites go where the main pulse did NOT reach
        float damage = (float) (pulseDamage * SATELLITE_FRACTION);
        for (int i = 0; i < count && i < foes.size(); i++) {
            Mob centre = foes.get(i);
            if (!centre.isAlive()) {
                continue;
            }
            Vec3 at = centre.position();
            level.sendParticles(ParticleTypes.FLAME, at.x, at.y + 0.5, at.z, 10, 0.3, 0.3, 0.3, 0.03);
            level.sendParticles(ParticleTypes.LAVA, at.x, at.y + 0.3, at.z, 3, 0.2, 0.1, 0.2, 0.0);
            for (Mob m : foesIn(level, at, SATELLITE_RADIUS, null)) {
                scorch(level, owner, m, damage);
            }
        }
    }

    /** Queues the Pyre Nova rings at {@code centre}. Returns how many rings were queued. */
    static int pyreNova(ServerLevel level, ServerPlayer owner, Vec3 centre, int lvl, float pulseDamage, int slotIndex) {
        double outer = novaOuter(lvl);
        float damage = (float) (pulseDamage * novaMultiple(lvl));
        int queued = 0;
        for (int ring = 0; ring < NOVA_RINGS; ring++) {
            final double inner = ring == 0 ? 0.0 : NOVA_INNER + (outer - NOVA_INNER) * (ring - 1) / (NOVA_RINGS - 1);
            final double edge = NOVA_INNER + (outer - NOVA_INNER) * ring / (NOVA_RINGS - 1);
            if (DelayedTasks.runLater(level, NOVA_STEP_TICKS * (ring + 1), o -> ring(level, owner, centre, inner, edge, damage, slotIndex))) {
                queued++;
            }
        }
        PyreLantern.raise(level, owner, centre);
        level.playSound(null, centre.x, centre.y, centre.z, SoundEvents.BLAZE_SHOOT, SoundSource.PLAYERS, 1.5F, 0.5F);
        level.sendParticles(ParticleTypes.LAVA, centre.x, centre.y + 0.5, centre.z, 24, 0.5, 0.3, 0.5, 0.1);
        return queued;
    }

    /** One expanding band: every foe with {@code inner} < distance <= {@code edge} from the centre burns. */
    private static void ring(ServerLevel level, ServerPlayer owner, Vec3 centre, double inner, double edge, float damage, int slotIndex) {
        if (!owner.isAlive()) {
            return;
        }
        Loadout.acting(owner, slotIndex, () -> {
            for (Mob m : foesIn(level, centre, edge, null)) {
                if (m.position().distanceTo(centre) > inner) {
                    scorch(level, owner, m, damage);
                }
            }
        });
        int points = 20;
        for (int i = 0; i < points; i++) {
            double a = Math.PI * 2.0 * i / points;
            level.sendParticles(i % 3 == 0 ? ParticleTypes.LAVA : ParticleTypes.FLAME,
                    centre.x + Math.cos(a) * edge, centre.y + 0.3, centre.z + Math.sin(a) * edge, 1, 0.05, 0.1, 0.05, 0.01);
        }
    }
}
