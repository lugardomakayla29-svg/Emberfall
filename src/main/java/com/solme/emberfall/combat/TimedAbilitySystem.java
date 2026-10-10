package com.solme.emberfall.combat;

import com.solme.emberfall.entity.TrackedProjectiles;
import com.solme.emberfall.item.PlayerWeapon;
import com.solme.emberfall.item.WeaponPool;
import com.solme.emberfall.item.WeaponType;
import com.solme.emberfall.tome.CombatStats;
import com.solme.emberfall.world.RunManager;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Cooldown-driven weapon capstones that fire on their own timer instead of on the weapon's
 * every-Nth-shot cadence: the Arcane Staff's Piercing Laser and the Hunting Bow's Spin Barrage.
 * Both are inspired by SlopPack's Golden Shortbow spells but rebuilt for an auto-attack game and
 * with the SlopPack defects deliberately left behind:
 * <ul>
 *   <li>Spin Barrage there overwrote the player's velocity every tick and spawned ~53 real Arrow
 *       entities per cast. Here every bolt is a virtual sweep bolt ({@link TrackedProjectiles#launchSweep}):
 *       no entity, no pickup, it ends against any block, and the player is never pushed.</li>
 *   <li>Piercing Laser there could be re-triggered while a previous beam was still charging. Here a
 *       player has at most one pending beam, tracked by a game-time deadline.</li>
 * </ul>
 * All timing is in server game ticks (not wall clock) so lag stretches it consistently and it
 * cannot be skipped by a clock change. Per-player state is dropped by {@link #clear}.
 */
public final class TimedAbilitySystem {
    // ---- Piercing Laser (Arcane Staff) ----
    static final int LASER_COOLDOWN_TICKS = 180;         // 9s base, matches SlopPack's 9000ms
    static final double LASER_FASTER_FACTOR = 0.70;      // stack 2+: 30% faster recharge
    static final int LASER_CHARGE_TICKS = 10;            // same charge as SlopPack (10 ticks)
    static final double LASER_LENGTH = 26.0;
    static final double LASER_HALF_WIDTH = 0.9;
    static final float LASER_EXECUTE_DAMAGE = 30.0F;
    static final double LASER_EXECUTE_FRACTION = 0.30;
    static final double LASER_FORK_LENGTH = 16.0;        // stack 3: second lane off the last target
    static final int LASER_RGB = 0xB44CFF;

    // ---- Spin Barrage (Hunting Bow) ----
    static final int SPIN_COOLDOWN_TICKS = 280;          // 14s base, matches SlopPack's 14000ms
    static final int SPIN_DURATION_TICKS = 40;           // 2s of fire
    static final int SPIN_FIRE_EVERY_TICKS = 3;
    static final double SPIN_SWEEP_DEG_PER_FIRE = 27.0;  // same sweep step as SlopPack
    static final double SPIN_BOLT_SPEED = 1.1;           // blocks per tick
    static final int SPIN_BOLT_LIFETIME = 24;            // ~26 blocks max range
    static final double SPIN_HIT_RADIUS = 0.8;
    static final float SPIN_DAMAGE_FRACTION = 1.5F;      // of the bow's own bolt damage (6.0 per bolt); only a few bolts reach any one foe
    /** Hard ceiling on bolts alive from one cast, whatever the stack count - the entity-count rule. */
    static final int SPIN_MAX_BOLTS_PER_CAST = 120;

    private static final Map<UUID, Long> laserReadyAt = new HashMap<>();
    private static final Map<UUID, PendingLaser> pendingLaser = new HashMap<>();
    private static final Map<UUID, Long> spinReadyAt = new HashMap<>();
    private static final Map<UUID, SpinCast> activeSpin = new HashMap<>();

    private record PendingLaser(UUID targetId, Vec3 origin, Vec3 dir, long fireAtTick) {}

    private static final class SpinCast {
        final long startTick;
        double sweepDeg;
        int boltsFired;
        SpinCast(long startTick) {
            this.startTick = startTick;
        }
    }

    private TimedAbilitySystem() {}

    /** Drops every piece of state for a player (run exit / disconnect). */
    public static void clear(ServerPlayer player) {
        UUID id = player.getUUID();
        laserReadyAt.remove(id);
        pendingLaser.remove(id);
        spinReadyAt.remove(id);
        activeSpin.remove(id);
    }

    public static void tickAll(MinecraftServer server) {
        if (laserReadyAt.isEmpty() && pendingLaser.isEmpty() && spinReadyAt.isEmpty() && activeSpin.isEmpty()
                && !anyoneHasAbility(server)) {
            return;
        }
        for (ServerLevel level : RunManager.activeLevels()) {
            long now = level.getGameTime();
            for (ServerPlayer player : level.players()) {
                if (player.isSpectator() || player.isDeadOrDying() || RunManager.slotOf(player) == null) {
                    continue;
                }
                CombatStats stats = CombatStats.of(player);
                // Kills these abilities make belong to the staff / bow slot (see Loadout#acting).
                com.solme.emberfall.item.Loadout lo = com.solme.emberfall.item.Loadout.peek(player);
                if (stats.piercingLaserTier > 0 && PlayerWeapon.isRunning(player, "arcane_staff")) {
                    final int laserTier = stats.piercingLaserTier;
                    com.solme.emberfall.item.Loadout.acting(player, lo == null ? -1 : lo.indexOf("arcane_staff"),
                            () -> tickLaser(level, player, laserTier, now));
                }
                if (stats.spinBarrageTier > 0 && PlayerWeapon.isRunning(player, "hunting_bow")) {
                    final int spinTier = stats.spinBarrageTier;
                    com.solme.emberfall.item.Loadout.acting(player, lo == null ? -1 : lo.indexOf("hunting_bow"),
                            () -> tickSpin(level, player, spinTier, now));
                }
            }
        }
    }

    /** Cheap early-out so an idle server pays nothing: true only if some run player owns either tome. */
    private static boolean anyoneHasAbility(MinecraftServer server) {
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            CombatStats stats = CombatStats.of(player);
            if (stats.piercingLaserTier > 0 || stats.spinBarrageTier > 0) {
                return true;
            }
        }
        return false;
    }

    // ------------------------------------------------------------------ Piercing Laser

    static int laserCooldownTicks(int tier) {
        return tier >= 2 ? (int) Math.round(LASER_COOLDOWN_TICKS * LASER_FASTER_FACTOR) : LASER_COOLDOWN_TICKS;
    }

    private static void tickLaser(ServerLevel level, ServerPlayer player, int tier, long now) {
        UUID id = player.getUUID();
        PendingLaser pending = pendingLaser.get(id);
        if (pending != null) {
            if (now >= pending.fireAtTick()) {
                pendingLaser.remove(id);
                fireLaser(level, player, pending, tier);
            }
            return;
        }
        if (now < laserReadyAt.getOrDefault(id, 0L)) {
            return;
        }
        WeaponType staff = WeaponPool.byId("arcane_staff");
        if (staff == null) {
            return;
        }
        LivingEntity target = AutoAttackSystem.findNearestHostile(level, player.position(), LASER_LENGTH);
        if (target == null) {
            return; // nothing to shoot at: keep the beam ready rather than wasting the cooldown
        }
        Vec3 origin = player.getEyePosition();
        Vec3 dir = target.getEyePosition().subtract(origin).normalize();
        pendingLaser.put(id, new PendingLaser(target.getUUID(), origin, dir, now + LASER_CHARGE_TICKS));
        laserReadyAt.put(id, now + laserCooldownTicks(tier));
        level.playSound(null, origin.x, origin.y, origin.z, SoundEvents.GUARDIAN_ATTACK, SoundSource.PLAYERS, 1.2F, 0.6F);
        // Wind-up warning only (cost rule): one lane drawn once, not every tick.
        Fx.telegraphLine(level, origin, origin.add(dir.scale(LASER_LENGTH)), LASER_HALF_WIDTH, LASER_RGB);
    }

    private static void fireLaser(ServerLevel level, ServerPlayer player, PendingLaser laser, int tier) {
        WeaponType staff = WeaponPool.byId("arcane_staff");
        if (staff == null) {
            return;
        }
        Vec3 origin = player.getEyePosition(); // follow the player's current position, keep the marked aim
        List<Mob> hit = new ArrayList<>(beam(level, player, staff, origin, laser.dir(), LASER_LENGTH));

        level.playSound(null, origin.x, origin.y, origin.z, SoundEvents.GUARDIAN_ATTACK, SoundSource.PLAYERS, 1.6F, 0.7F);
        for (double d = 0.0; d <= LASER_LENGTH; d += 1.0) {
            Vec3 p = origin.add(laser.dir().scale(d));
            level.sendParticles(new DustParticleOptions(LASER_RGB, 1.2F), p.x, p.y, p.z, 1, 0.05, 0.05, 0.05, 0.0);
        }

        // Stack 3: a forked lane leaves from the farthest enemy this beam struck, angled off to one side.
        if (tier >= 3 && !hit.isEmpty()) {
            Mob last = hit.get(hit.size() - 1);
            Vec3 from = last.position().add(0, last.getBbHeight() * 0.5, 0);
            Vec3 forkDir = new Vec3(-laser.dir().z, 0.0, laser.dir().x).normalize()
                    .add(laser.dir().scale(0.5)).normalize();
            for (double d = 0.0; d <= LASER_FORK_LENGTH; d += 1.0) {
                Vec3 p = from.add(forkDir.scale(d));
                level.sendParticles(new DustParticleOptions(LASER_RGB, 0.9F), p.x, p.y, p.z, 1, 0.05, 0.05, 0.05, 0.0);
            }
            beam(level, player, staff, from, forkDir, LASER_FORK_LENGTH);
        }
    }

    /**
     * Strikes every living Emberfall hostile within {@code halfWidth} of the line once, executing
     * anything under 30% health. Returns the victims ordered nearest to farthest along the line.
     */
    private static List<Mob> beam(ServerLevel level, ServerPlayer player, WeaponType staff, Vec3 origin, Vec3 dir, double length) {
        AABB box = new AABB(origin, origin.add(dir.scale(length))).inflate(LASER_HALF_WIDTH + 1.0);
        List<Mob> victims = level.getEntitiesOfClass(Mob.class, box, mob -> {
            if (!mob.isAlive() || !AutoAttackSystem.isTargetable(mob)) {
                return false;
            }
            Vec3 body = mob.position().add(0, mob.getBbHeight() * 0.5, 0);
            double along = body.subtract(origin).dot(dir);
            return along > 0 && along <= length
                    && AutoAttackSystem.distanceToLine(body, origin, dir) <= LASER_HALF_WIDTH + mob.getBbWidth() * 0.5;
        });
        victims.sort(java.util.Comparator.comparingDouble(m -> m.position().subtract(origin).dot(dir)));
        for (Mob mob : victims) {
            boolean execute = mob.getHealth() / mob.getMaxHealth() <= LASER_EXECUTE_FRACTION;
            float healthBefore = mob.getHealth();
            AutoAttackSystem.clearInvulnerabilityWindow(mob);
            mob.hurtServer(level, player.damageSources().playerAttack(player),
                    execute ? LASER_EXECUTE_DAMAGE : staff.rangedDamage() * 2.0F);
            float dealt = Math.max(0.0F, healthBefore - mob.getHealth());
            OnHitEffects.apply(player, mob, dealt);
            Vec3 at = mob.position().add(0, mob.getBbHeight() * 0.5, 0);
            level.sendParticles(execute ? ParticleTypes.CRIT : ParticleTypes.WITCH,
                    at.x, at.y, at.z, execute ? 12 : 5, 0.25, 0.3, 0.25, 0.05);
            if (execute) {
                level.playSound(null, at.x, at.y, at.z, SoundEvents.PLAYER_ATTACK_CRIT, SoundSource.PLAYERS, 1.2F, 0.7F);
            }
        }
        return victims;
    }

    // ------------------------------------------------------------------ Spin Barrage

    static int spinBoltsPerRing(int tier) {
        return tier >= 3 ? 8 : 4;
    }

    static int spinSpirals(int tier) {
        return tier >= 2 ? 2 : 1;
    }

    /** Total bolts one cast will fire, used to prove the entity-count ceiling. */
    static int spinTotalBolts(int tier) {
        int fires = SPIN_DURATION_TICKS / SPIN_FIRE_EVERY_TICKS + 1;
        return Math.min(SPIN_MAX_BOLTS_PER_CAST, fires * spinBoltsPerRing(tier) * spinSpirals(tier));
    }

    private static void tickSpin(ServerLevel level, ServerPlayer player, int tier, long now) {
        UUID id = player.getUUID();
        SpinCast cast = activeSpin.get(id);
        if (cast == null) {
            if (now < spinReadyAt.getOrDefault(id, 0L)) {
                return;
            }
            if (AutoAttackSystem.findNearestHostile(level, player.position(), SPIN_BOLT_LIFETIME * SPIN_BOLT_SPEED) == null) {
                return; // only spend the barrage when there is something in range
            }
            cast = new SpinCast(now);
            activeSpin.put(id, cast);
            spinReadyAt.put(id, now + SPIN_COOLDOWN_TICKS);
            level.playSound(null, player.getX(), player.getY(), player.getZ(),
                    SoundEvents.TRIDENT_RIPTIDE_1, SoundSource.PLAYERS, 1.0F, 0.7F);
        }
        long elapsed = now - cast.startTick;
        if (elapsed >= SPIN_DURATION_TICKS || player.isDeadOrDying()) {
            activeSpin.remove(id);
            return;
        }
        if (elapsed % SPIN_FIRE_EVERY_TICKS != 0) {
            return;
        }
        WeaponType bow = WeaponPool.byId("hunting_bow");
        if (bow == null) {
            activeSpin.remove(id);
            return;
        }
        cast.sweepDeg += SPIN_SWEEP_DEG_PER_FIRE;
        int perRing = spinBoltsPerRing(tier);
        int spirals = spinSpirals(tier);
        Vec3 eye = player.getEyePosition();
        float damage = bow.rangedDamage() * SPIN_DAMAGE_FRACTION;
        for (int s = 0; s < spirals; s++) {
            double direction = s == 0 ? 1.0 : -1.0; // stack 2: second spiral counter-rotates
            for (int i = 0; i < perRing; i++) {
                if (cast.boltsFired >= SPIN_MAX_BOLTS_PER_CAST) {
                    return;
                }
                cast.boltsFired++;
                double deg = cast.sweepDeg * direction + (360.0 / perRing) * i;
                double rad = Math.toRadians(deg);
                Vec3 velocity = new Vec3(Math.cos(rad), 0.0, Math.sin(rad)).scale(SPIN_BOLT_SPEED);
                TrackedProjectiles.launchSweep(level, eye, velocity, player, SPIN_BOLT_LIFETIME,
                        ParticleTypes.CRIT,
                        pos -> firstHostileAt(level, pos),
                        // Lands on a later tick, so it re-enters the bow slot that fired it (see Loadout#deferred).
                        com.solme.emberfall.item.Loadout.<Vec3, LivingEntity>deferred2(player, (pos, victim) -> {
                            float before = victim.getHealth();
                            AutoAttackSystem.clearInvulnerabilityWindow(victim);
                            victim.hurtServer(level, player.damageSources().playerAttack(player), damage);
                            OnHitEffects.apply(player, victim, Math.max(0.0F, before - victim.getHealth()));
                        }));
            }
        }
        if ((elapsed / SPIN_FIRE_EVERY_TICKS) % 4 == 0) {
            level.playSound(null, player.getX(), player.getY(), player.getZ(),
                    SoundEvents.ARROW_SHOOT, SoundSource.PLAYERS, 0.8F, 0.8F + player.getRandom().nextFloat() * 0.4F);
        }
    }

    private static LivingEntity firstHostileAt(ServerLevel level, Vec3 pos) {
        AABB box = AABB.ofSize(pos, SPIN_HIT_RADIUS * 2, SPIN_HIT_RADIUS * 2, SPIN_HIT_RADIUS * 2);
        for (Mob mob : level.getEntitiesOfClass(Mob.class, box)) {
            if (mob.isAlive() && AutoAttackSystem.isTargetable(mob)) {
                return mob;
            }
        }
        return null;
    }
}
