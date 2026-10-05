package com.solme.emberfall.combat;

import com.solme.emberfall.item.ModItems;
import com.solme.emberfall.item.WeaponMoveset;
import com.solme.emberfall.item.WeaponType;
import com.solme.emberfall.tome.CombatStats;
import com.solme.emberfall.world.Dimensions;
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
 * Spectral Sickles' {@link WeaponMoveset#ORBIT} - a genuinely different weapon identity
 * from every gated-single-hit moveset ({@link AutoAttackSystem}): two virtual blades spin
 * continuously around the wielder at a fixed radius, dealing contact damage to anything they
 * sweep through. No windup, no cooldown, and entirely independent of the weapon's own
 * attack-speed attribute - it's a standing threat zone, not another timed "shot". Ticked
 * unconditionally every server tick (registered directly against
 * {@code ServerTickEvents.END_SERVER_TICK} in {@code EmberfallMod}), not dispatched through
 * {@link AutoAttackSystem#tickPlayer} at all.
 *
 * Same "virtual, not a real entity" tradeoff as {@link TotemWeaponSystem} and
 * {@link com.solme.emberfall.entity.TrackedProjectiles}: the blades are pure geometry +
 * particles here, no entity spawned, so however many players carry this weapon it costs zero
 * extra entities.
 *
 * Two engine facts this class had to be built around, both confirmed via decompiled 1.21.11
 * bytecode rather than guessed at (per standing project policy):
 *   - A mob's {@code position()} is its FEET, not its body center. Comparing a blade's point
 *     against a mob's eye-height-ish orbit plane using feet position alone puts most mobs'
 *     actual bodies ~1 block off the blade's true sweep line, so hit detection must compare
 *     against each mob's body-center point (feet + half bounding-box height), not raw
 *     {@code position()}.
 *   - {@code LivingEntity.hurtServer} unconditionally applies a real 0.4-magnitude knockback
 *     (and halves the target's existing velocity as part of the same formula) for any damage
 *     source not tagged NO_KNOCKBACK - including a player-attack DamageSource passed directly
 *     to hurtServer, not just vanilla's own player.attack() path. Orbit's entire identity is a
 *     standing contact-damage aura, not a knockback weapon, so every landed hit snapshots the
 *     target's velocity beforehand and restores it afterward, undoing exactly that impulse
 *     (and its velocity-halving side effect) regardless of the internal knockback formula.
 */
public final class OrbitWeaponSystem {
    private static final boolean TEST_MODE = Boolean.getBoolean("emberfall.testMode");
    private static final double HIT_RADIUS = 0.9; // how close a blade's point must pass to a mob's body-center point

    // Keyed by "<playerUUID>:<targetUUID>" -> the game tick of that pair's last landed hit.
    private static final Map<String, Long> lastHitTick = new ConcurrentHashMap<>();

    /** Widening Gyre (Tome) tuning - see {@link com.solme.emberfall.tome.CombatStats#wideningGyreTier}. */
    private static final int GYRE_MAX_STACKS = 4; // stacks 0..4 -> +8%..+32%, matches the Tome's advertised "up to +32%"
    private static final double GYRE_DAMAGE_PER_STACK = 0.08; // +8%/stack, up to +32% at 4 stacks held (5th hit)
    private static final int GYRE_DECAY_TICKS = 40; // 2s - longer than one full lap (45 ticks) so a single
                                                      // bad-angle pass doesn't reset it, but disengaging does
    private static final double GYRE_TIER2_RADIUS_BONUS = 0.5;
    private static final int GYRE_OVERCAP_STACKS = GYRE_MAX_STACKS + 1; // tier 3: 5 stacks -> +40%

    /** Per-player Widening Gyre state: current stack count, the tick of its last refresh, and
     *  (tier 3) whether this streak has already earned its one bonus stack beyond the normal
     *  cap - reset whenever the streak itself resets, so the bonus has to be re-earned with a
     *  fresh kill at max stacks rather than persisting forever off one lucky kill. */
    private static final class GyreState {
        int stacks = 0;
        long lastHitTick = Long.MIN_VALUE;
        boolean overcapEarned = false;
    }
    private static final Map<UUID, GyreState> gyreState = new ConcurrentHashMap<>();

    private OrbitWeaponSystem() {}

    /** Runs every player currently wielding an ORBIT weapon through one tick of blade movement + hit detection. */
    public static void tickAll(MinecraftServer server) {
        for (ServerLevel level : com.solme.emberfall.world.RunManager.activeLevels()) {
        long gameTime = level.getGameTime();
        for (ServerPlayer player : level.players()) {
            if (player.isSpectator() || player.isDeadOrDying()
                    || com.solme.emberfall.world.RunManager.slotOf(player) == null) {
                continue;
            }
            // The blades belong to whichever slot holds the orbit weapon, not to what is in the hand.
            com.solme.emberfall.item.Loadout loadout = com.solme.emberfall.item.Loadout.peek(player);
            if (loadout == null) {
                continue;
            }
            WeaponType weapon = null;
            for (WeaponType owned : loadout.weapons()) {
                if (owned.moveset() == WeaponMoveset.ORBIT) {
                    weapon = owned;
                    break;
                }
            }
            if (weapon == null) {
                continue;
            }
            // The blades' kills belong to the slot holding the sickles (see Loadout#acting).
            final WeaponType orbitWeapon = weapon;
            com.solme.emberfall.item.Loadout.acting(player, loadout.indexOf(weapon.id()),
                    () -> tickOrbit(level, player, orbitWeapon, gameTime));
        }
        }
    }

    private static void tickOrbit(ServerLevel level, ServerPlayer player, WeaponType weapon, long gameTime) {
        if (ReapersRiteSystem.active(player)) {
            return;       // Reaper's Rite owns the sickles until it ends
        }
        final int lvl = com.solme.emberfall.item.WeaponProgress.levelOf(player);
        final double blades = Math.min(SickleSystem.MAX_BLADES, SickleSystem.effectiveBlades(lvl) + com.solme.emberfall.relic.RelicTempoGame.bonusStrikes(player));
        final double baseRadius = SickleSystem.orbitRadius(lvl);
        final double orbitRadius = baseRadius;
        final double spin = SickleSystem.spinDegrees(lvl);
        final int cooldown = SickleSystem.cooldownAt(lvl);
        final double baseAngleDeg = (gameTime * spin) % 360;
        // The fractional blade is on for part of each lap, judged by the ring's own phase (steady, not a flicker).
        final double lapPhase = (baseAngleDeg / 360.0);
        Vec3 center = player.position();

        int gyreTier = CombatStats.of(player).wideningGyreTier;
        double effectiveRadius = effectiveHitRadius(player, gyreTier, gameTime) - HIT_RADIUS + SickleSystem.hitRadius(lvl);

        // ONE entity query per tick for the whole ring, however many blades there are. Each blade is then plain distance maths.
        double reach = orbitRadius + effectiveRadius + 1.0;
        List<Mob> candidates = level.getEntitiesOfClass(Mob.class, AABB.ofSize(center.add(0, 1.0, 0), reach * 2, 3.0 + effectiveRadius * 2, reach * 2),
                mob -> mob.isAlive() && AutoAttackSystem.isEmberfallHostile(mob));

        int slots = SickleSystem.slots(blades);
        for (int i = 0; i < slots; i++) {
            if (!SickleSystem.bladeOn(i, blades, lapPhase)) {
                continue;
            }
            // Blades sit on FIXED evenly spaced slots (whole blades plus one for the fractional blade), so switching the fractional blade on
            // and off never moves the others.
            double angleDeg = baseAngleDeg + (360.0 / slots) * i;
            double angleRad = Math.toRadians(angleDeg);
            Vec3 bladePos = center.add(Math.cos(angleRad) * orbitRadius, 1.0, Math.sin(angleRad) * orbitRadius);

            // The blade head, with a short comet tail behind it (the ring is swept round by SPIN degrees a tick, so the tail trails that way).
            level.sendParticles(ParticleTypes.SOUL_FIRE_FLAME, bladePos.x, bladePos.y, bladePos.z,
                    1, 0.02, 0.02, 0.02, 0.0);
            for (int tail = 1; tail <= SickleSystem.TRAIL_POINTS; tail++) {
                double back = angleRad - Math.toRadians(spin) * tail * 1.5;
                level.sendParticles(ParticleTypes.SOUL, center.x + Math.cos(back) * orbitRadius, center.y + 1.0, center.z + Math.sin(back) * orbitRadius,
                        1, 0.0, 0.0, 0.0, 0.0);
            }

            for (Mob mob : candidates) {
                if (!mob.isAlive()) {
                    continue;
                }
                // Body-center point, not position() (feet) - see class javadoc.
                if (mob.position().add(0, mob.getBbHeight() * 0.5, 0).distanceTo(bladePos) > effectiveRadius) {
                    continue;
                }
                cut(level, player, weapon, mob, gameTime, cooldown, gyreTier, center, orbitRadius);
            }
        }

        // THE FILLED RING: every other tick, an inner fill (two rings inside the blade circle) and the rim between the blades, so the circle
        // reads as a solid disc, denser and wider as the level rises. Bounded by SickleSystem.RING_PARTICLE_BUDGET; particles linger a few ticks,
        // so drawing on alternate ticks still looks continuous.
        if ((gameTime & 1L) == 0L) {
            int budget = SickleSystem.ringParticles(lvl, slots, true) - (slots + slots * SickleSystem.TRAIL_POINTS);
            int fill = Math.min(SickleSystem.fillPoints(lvl), Math.max(0, budget));
            for (int i = 0; i < fill; i++) {
                double ringR = orbitRadius * (i % 2 == 0 ? 0.35 : 0.7);
                double a = Math.toRadians(baseAngleDeg * 0.5) + Math.PI * 2 * i / Math.max(1, fill);
                level.sendParticles(ParticleTypes.SOUL, center.x + Math.cos(a) * ringR, center.y + 0.5 + 0.5 * (i % 2), center.z + Math.sin(a) * ringR,
                        1, 0.0, 0.0, 0.0, 0.0);
            }
            int rim = Math.min(SickleSystem.rimPoints(lvl), Math.max(0, budget - fill));
            for (int i = 0; i < rim; i++) {
                double a = Math.toRadians(baseAngleDeg) + Math.PI * 2 * i / Math.max(1, rim);
                level.sendParticles(ParticleTypes.SOUL_FIRE_FLAME, center.x + Math.cos(a) * orbitRadius, center.y + 1.0, center.z + Math.sin(a) * orbitRadius,
                        1, 0.0, 0.0, 0.0, 0.0);
            }
        }

        // INNER GUARD. The blades only hurt a foe within their hit reach, so a foe that slipped INSIDE the ring (a spider on top of the
        // player) sat in a dead zone and was never cut. Everything whose body centre is inside the blades' circle is cut on the same
        // per-foe cooldown and through the same hit rules. No new entity query: this is the ring's own candidate list.
        for (Mob mob : candidates) {
            if (!mob.isAlive()) {
                continue;
            }
            double flat = Math.hypot(mob.getX() - center.x, mob.getZ() - center.z);
            double dy = mob.position().y + mob.getBbHeight() * 0.5 - (center.y + 1.0);
            if (flat <= orbitRadius && Math.abs(dy) <= 1.5 + mob.getBbHeight() * 0.5) {
                cut(level, player, weapon, mob, gameTime, cooldown, gyreTier, center, orbitRadius);
            }
        }
    }

    /**
     * One landed cut on {@code mob}: the per-pair cooldown, damage with Gyre stacks, the knockback undo, on-hit effects, and the weapon meter
     * that starts Reaper's Rite. The blades and the inner guard both come through here, so there is one copy of the hit rules.
     */
    private static void cut(ServerLevel level, ServerPlayer player, WeaponType weapon, Mob mob, long gameTime, int cooldown, int gyreTier,
                            Vec3 center, double orbitRadius) {
        String key = player.getUUID() + ":" + mob.getUUID();
        Long last = lastHitTick.get(key);
        if (last != null && gameTime - last < cooldown) {
            return;
        }
        lastHitTick.put(key, gameTime);
        if (TEST_MODE) {
            com.solme.emberfall.EmberfallMod.LOGGER.info("SICKLE_TEST cut tag={} flat={} ring={} tick={}",
                    mob.getTags().stream().findFirst().orElse("-"),
                    String.format("%.2f", Math.hypot(mob.getX() - center.x, mob.getZ() - center.z)),
                    String.format("%.2f", orbitRadius), gameTime);
        }

        int stacksBeforeHit = gyreTier >= 1 ? currentGyreStacks(player, gameTime) : 0;
        float damage = weapon.meleeDamage() * (float) Echo.lift(com.solme.emberfall.item.WeaponProgress.levelOf(player));
        if (gyreTier >= 1) {
            damage *= (float) (1.0 + GYRE_DAMAGE_PER_STACK * stacksBeforeHit);
        }

        // See class javadoc: hurtServer's automatic knockback would otherwise fling the
        // target clean out of the ring after the very first landed hit. Snapshot/restore
        // velocity around the call to undo it, whatever the internal formula does.
        Vec3 preHitVelocity = mob.getDeltaMovement();
        float healthBefore = mob.getHealth();
        mob.invulnerableTime = 0;
        mob.hurtServer(level, player.damageSources().playerAttack(player), damage);
        mob.setDeltaMovement(preHitVelocity);
        float damageDealt = Math.max(0.0F, healthBefore - mob.getHealth());
        OnHitEffects.apply(player, mob, damageDealt);
        level.playSound(null, mob.getX(), mob.getY(), mob.getZ(), SoundEvents.PLAYER_ATTACK_SWEEP,
                SoundSource.PLAYERS, 0.4F, 1.5F);
        WeaponFx.sickleCut(level, mob);

        if (gyreTier >= 1) {
            // Widening Gyre tier 3 ("Overcharged Gyre"): a kill landed while already at
            // max stacks earns one bonus stack beyond the normal cap for the rest of
            // this streak - pushing the blades to +40% until the streak decays or
            // resets, at which point it has to be earned again.
            if (gyreTier >= 3 && !mob.isAlive() && stacksBeforeHit >= GYRE_MAX_STACKS) {
                gyreState.computeIfAbsent(player.getUUID(), k -> new GyreState()).overcapEarned = true;
            }
            advanceGyre(player, gameTime, gyreTier);
        }

        // Growth: every landed cut fills the meter; a full meter starts Reaper's Rite (never while one is already running).
        if (damageDealt > 0.0F) {
            com.solme.emberfall.item.WeaponProgress.onHit(player);
            if (!ReapersRiteSystem.active(player) && com.solme.emberfall.item.WeaponProgress.ultimateReady(player)
                    && com.solme.emberfall.item.WeaponProgress.consumeUltimate(player)) {
                ReapersRiteSystem.start(level, player, com.solme.emberfall.item.WeaponProgress.levelOf(player), damage,
                        com.solme.emberfall.item.Loadout.actingIndexFor(player));
            }
        }
    }

    /**
     * Widening Gyre (Tome): current stack count, decay-aware but non-mutating - a stale
     * stack (nothing hit for {@link #GYRE_DECAY_TICKS}) reads as 0 without needing a
     * separate tick loop just to age it out.
     */
    private static int currentGyreStacks(ServerPlayer player, long gameTime) {
        GyreState state = gyreState.get(player.getUUID());
        if (state == null || gameTime - state.lastHitTick > GYRE_DECAY_TICKS) {
            return 0;
        }
        return state.stacks;
    }

    /** Widening Gyre (Tome): records this landed hit, growing the stack (capped, or one higher
     *  than usual once tier 3's overcap has been earned this streak) and refreshing its decay
     *  timer. A stale streak restarting from 1 also forfeits any earlier overcap - it has to
     *  be earned fresh, not carried across a disengage. */
    private static void advanceGyre(ServerPlayer player, long gameTime, int gyreTier) {
        GyreState state = gyreState.computeIfAbsent(player.getUUID(), k -> new GyreState());
        boolean stale = gameTime - state.lastHitTick > GYRE_DECAY_TICKS;
        if (stale) {
            state.stacks = 1;
            state.overcapEarned = false;
        } else {
            int cap = (gyreTier >= 3 && state.overcapEarned) ? GYRE_OVERCAP_STACKS : GYRE_MAX_STACKS;
            state.stacks = Math.min(cap, state.stacks + 1);
        }
        state.lastHitTick = gameTime;
    }

    /** Widening Gyre (Tome) tier 2: at max stacks, the blades' effective contact radius grows too. */
    private static double effectiveHitRadius(ServerPlayer player, int gyreTier, long gameTime) {
        if (gyreTier >= 2 && currentGyreStacks(player, gameTime) >= GYRE_MAX_STACKS) {
            return HIT_RADIUS + GYRE_TIER2_RADIUS_BONUS;
        }
        return HIT_RADIUS;
    }

    /**
     * Drops any hit-cooldown/Widening Gyre bookkeeping keyed to this player. Call on run leave/
     * disconnect ({@link com.solme.emberfall.world.RunManager#leavePlayer}) - without
     * this the maps would otherwise hold a permanently-growing set of stale entries
     * for every target/player across every run they've ever played.
     */
    public static void clear(ServerPlayer player) {
        String prefix = player.getUUID() + ":";
        lastHitTick.keySet().removeIf(key -> key.startsWith(prefix));
        gyreState.remove(player.getUUID());
        ReapersRiteSystem.clear(player);
    }
}
