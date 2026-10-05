package com.solme.emberfall.combat;

import com.solme.emberfall.item.WeaponType;
import com.solme.emberfall.tome.CombatStats;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Ashen Beacon's {@link com.solme.emberfall.item.WeaponMoveset#TOTEM} - the third
 * fresh weapon identity (design goal: genuinely new roguelike weapons, not more
 * variants of "auto-target and land a gated hit" - see {@link AutoAttackSystem}
 * and {@link OrbitWeaponSystem}). Instead of striking its target, each "shot"
 * plants a stationary beacon there that pulses AoE fire damage to anything
 * nearby on its own timer for a duration, then burns out - a deployable,
 * not a direct hit.
 *
 * Same "virtual, not a real entity" tradeoff as {@link OrbitWeaponSystem} and
 * {@link com.solme.emberfall.entity.TrackedProjectiles}: a beacon is a plain
 * in-memory record here, rendered purely via particles, so however many
 * players carry this weapon it costs zero extra entities.
 *
 * One active beacon per player at a time - planting a new one replaces
 * (extinguishes) that player's existing beacon rather than stacking, which
 * both keeps this list bounded regardless of how spammy a player's cadence
 * gets and gives "replace" a real tactical identity (move the zone, don't
 * just pile more of them up). With Undying Embers (Tome) owned, a replaced
 * or naturally-expired main beacon leaves behind one Ember - see that
 * Tome's javadoc on {@link com.solme.emberfall.tome.CombatStats#undyingEmbersTier}
 * - so the list is bounded at 2 entries per player (1 main + 1 Ember) even then.
 * Tier 3 adds one more possible entry: a kill-triggered Ember planted at the kill site
 * (see {@code Beacon#isKillEmber}), itself replaced-not-stacked and barred from ever
 * spawning a further kill-ember off its own pulses - so the bound becomes 3 entries per
 * player (1 main + 1 natural-expiry Ember + 1 kill-Ember), never more, regardless of run
 * length or kill count.
 */
public final class TotemWeaponSystem {

    /** Undying Embers (Tome) tuning. */
    private static final int EMBER_DURATION_TICKS = 80; // 4s
    private static final double EMBER_DAMAGE_MULTIPLIER = 0.5; // half strength
    private static final double EMBER_TIER2_FINAL_BURST_MULTIPLIER = 2.0; // relative to the Ember's own (already halved) pulse damage

    private static final List<Beacon> ACTIVE = new ArrayList<>();

    private TotemWeaponSystem() {}

    private static final class Beacon {
        final UUID ownerId;
        final ServerLevel level;
        final Vec3 pos;
        final float pulseDamage;
        final boolean isEmber;
        /** Undying Embers (Tome) tier 3 ("Feeding Embers"): true only for an Ember spawned
         *  by a landed kill (see {@link #spawnKillEmber}), never for the main beacon or the
         *  natural-expiry residual Ember. Read at the {@link #pulseAt} call site to refuse
         *  spawning a further kill-ember off a kill-ember's own pulses - without that one
         *  guard, a long kill-heavy fight could grow this player's active-beacon list without
         *  bound; with it, at most one kill-ember can ever exist per player at a time. */
        final boolean isKillEmber;
        /** The beacon weapon's level when this beacon was planted. A level-up mid-fight does not change a live beacon. */
        int weaponLevel = 1;
        int ticksLived = 0;
        int ticksUntilNextPulse = 0; // pulse immediately on the tick it's planted
        /** Living flames (BeaconSystem): positions of this MAIN beacon's hunting fires. Empty on embers. Bounded by BeaconSystem.flames(level) rounded up. */
        final List<Vec3> flames = new ArrayList<>();
        /** The foe each flame is committed to (by index, parallel to {@link #flames}) and the tick its commitment ends. A flame keeps its target until
         *  the target dies, leaves the hunt reach, or {@link BeaconSystem#FLAME_DWELL_TICKS} pass, then it re-picks: this stops two close foes
         *  from pulling a flame back and forth between them. */
        final List<UUID> flameTarget = new ArrayList<>();
        final List<Long> flameUntil = new ArrayList<>();
        /** Game tick of each flame's last contact per foe, keyed by mob uuid, so one foe is cut at most every FLAME_CONTACT_TICKS. */
        /** Last contact tick per (flame index, foe): each flame cuts on its own beat, so more flames really mean more hits on ONE foe. */
        final java.util.Map<String, Long> flameContact = new java.util.HashMap<>();

        Beacon(UUID ownerId, ServerLevel level, Vec3 pos, float pulseDamage, boolean isEmber) {
            this(ownerId, level, pos, pulseDamage, isEmber, false);
        }

        Beacon(UUID ownerId, ServerLevel level, Vec3 pos, float pulseDamage, boolean isEmber, boolean isKillEmber) {
            this.ownerId = ownerId;
            this.level = level;
            this.pos = pos;
            this.pulseDamage = pulseDamage;
            this.isEmber = isEmber;
            this.isKillEmber = isKillEmber;
        }
    }

    /** True if this player already has a live MAIN beacon planted (an unattached lingering Ember doesn't count - it's a residual effect, not the occupied "deploy slot"). */
    public static boolean hasMainBeaconActive(UUID ownerId) {
        for (Beacon b : ACTIVE) {
            if (b.ownerId.equals(ownerId) && !b.isEmber) {
                return true;
            }
        }
        return false;
    }

    /** Plants a new beacon for {@code player} at {@code pos}, extinguishing any beacon they already have active. */
    public static void deploy(ServerLevel level, ServerPlayer player, Vec3 pos, WeaponType weapon) {
        int undyingEmbersTier = CombatStats.of(player).undyingEmbersTier;
        List<Beacon> replaced = new ArrayList<>();
        ACTIVE.removeIf(b -> {
            if (b.ownerId.equals(player.getUUID())) {
                replaced.add(b);
                return true;
            }
            return false;
        });
        // Only the (at most one) replaced main beacon gets an Ember - a replaced Ember is
        // just cut short, matching Undying Embers' tier 2 design intent of rewarding letting
        // an Ember actually finish its cycle rather than spam-relocating through it.
        if (undyingEmbersTier >= 1) {
            for (Beacon old : replaced) {
                if (!old.isEmber) {
                    spawnEmber(old);
                }
            }
        }

        Beacon planted = new Beacon(player.getUUID(), level, pos, weapon.rangedDamage(), false);
        planted.weaponLevel = com.solme.emberfall.item.WeaponProgress.levelOf(player);
        ACTIVE.add(planted);
        level.playSound(null, pos.x, pos.y, pos.z, SoundEvents.FIRECHARGE_USE, SoundSource.PLAYERS, 1.0F, 0.8F);
        level.sendParticles(ParticleTypes.LAVA, pos.x, pos.y + 0.2, pos.z, 12, 0.3, 0.1, 0.3, 0.02);
    }

    public static void tickAll(MinecraftServer server) {
        PyreLantern.tickAll();
        if (ACTIVE.isEmpty()) {
            return;
        }
        List<Beacon> expired = new ArrayList<>();
        for (Beacon beacon : new ArrayList<>(ACTIVE)) {
            beacon.ticksLived++;

            // Standing flame-column visual every tick - cheap (2 particle calls), no entity.
            // Embers burn dimmer/smaller than a full beacon so the two read as visually distinct.
            int flameCount = beacon.isEmber ? 1 : 2;
            beacon.level.sendParticles(ParticleTypes.FLAME, beacon.pos.x, beacon.pos.y + 0.3, beacon.pos.z,
                    flameCount, 0.2, 0.1, 0.2, 0.01);
            beacon.level.sendParticles(ParticleTypes.SMALL_FLAME, beacon.pos.x, beacon.pos.y + 0.05, beacon.pos.z,
                    1, 0.3, 0.02, 0.3, 0.0);

            if (beacon.ticksUntilNextPulse <= 0) {
                pulse(beacon);
                beacon.ticksUntilNextPulse = BeaconSystem.intervalTicks(beacon.weaponLevel);
            } else {
                beacon.ticksUntilNextPulse--;
            }

            if (!beacon.isEmber) {
                tickFlames(beacon);
            }

            int lifespan = beacon.isEmber ? EMBER_DURATION_TICKS : BeaconSystem.lifespanTicks(beacon.weaponLevel);
            if (beacon.ticksLived >= lifespan) {
                expired.add(beacon);
            }
        }
        for (Beacon beacon : expired) {
            ACTIVE.remove(beacon);
            onNaturalExpiry(beacon);
        }
    }

    /**
     * Undying Embers (Tome) payoff for letting a beacon actually run its course (as opposed
     * to {@link #deploy}'s early-replace path, which does not call this): a main beacon that
     * burns out on its own leaves an Ember (tier 1+); an Ember that burns out on its own
     * detonates once in a bigger final burst (tier 2 only, since tier 1 only mentions the
     * Ember itself, not a second-order effect off the Ember).
     */
    private static void onNaturalExpiry(Beacon beacon) {
        ServerPlayer owner = beacon.level.getServer().getPlayerList().getPlayer(beacon.ownerId);
        int tier = owner != null ? CombatStats.of(owner).undyingEmbersTier : 0;
        if (tier <= 0) {
            return;
        }
        if (!beacon.isEmber) {
            spawnEmber(beacon);
        } else if (tier >= 2) {
            finalBurst(beacon);
        }
    }

    /** Undying Embers (Tome): spawns the half-strength, short-lived residual Ember at {@code original}'s position. */
    private static void spawnEmber(Beacon original) {
        Beacon ember = new Beacon(original.ownerId, original.level, original.pos,
                (float) (original.pulseDamage * EMBER_DAMAGE_MULTIPLIER), true);
        ember.weaponLevel = original.weaponLevel;
        ACTIVE.add(ember);
        original.level.playSound(null, original.pos.x, original.pos.y, original.pos.z,
                SoundEvents.FIRE_EXTINGUISH, SoundSource.PLAYERS, 0.6F, 1.4F);
        original.level.sendParticles(ParticleTypes.SMOKE, original.pos.x, original.pos.y + 0.2, original.pos.z,
                10, 0.3, 0.2, 0.3, 0.02);
    }

    /**
     * Undying Embers (Tome) tier 3 ("Feeding Embers"): {@code source}'s pulse just landed a
     * killing blow at {@code killPos} - plants a fresh Ember there, half the strength of
     * whatever pulse damage {@code source} was actually dealing (so a kill-ember spawned off
     * an already-halved residual Ember is itself a further half, not a flat rule). Replaces
     * any existing kill-ember this owner already has, the same "relocate, don't pile up"
     * identity {@link #deploy} gives the main beacon - see the {@code isKillEmber} field
     * javadoc for why this is the load-bearing bound on the whole mechanic.
     */
    private static void spawnKillEmber(Beacon source, Vec3 killPos) {
        ACTIVE.removeIf(b -> b.ownerId.equals(source.ownerId) && b.isKillEmber);
        Beacon killEmber = new Beacon(source.ownerId, source.level, killPos,
                (float) (source.pulseDamage * EMBER_DAMAGE_MULTIPLIER), true, true);
        killEmber.weaponLevel = source.weaponLevel;
        ACTIVE.add(killEmber);
        source.level.playSound(null, killPos.x, killPos.y, killPos.z,
                SoundEvents.FIRE_EXTINGUISH, SoundSource.PLAYERS, 0.6F, 1.6F);
        source.level.sendParticles(ParticleTypes.SOUL_FIRE_FLAME, killPos.x, killPos.y + 0.3, killPos.z,
                10, 0.25, 0.3, 0.25, 0.02);
    }

    /** Undying Embers (Tome) tier 2: an Ember's own last gasp - one bigger detonation, then it's gone for good. */
    private static void finalBurst(Beacon ember) {
        pulseAt(ember, (float) (ember.pulseDamage * EMBER_TIER2_FINAL_BURST_MULTIPLIER),
                SoundEvents.GENERIC_EXPLODE.value(), 1.0F, 0.9F, true);
    }

    private static void pulse(Beacon beacon) {
        pulseAt(beacon, beacon.pulseDamage, SoundEvents.GENERIC_BURN, 0.6F, 1.1F, false);
    }

    private static void pulseAt(Beacon beacon, float damage, SoundEvent sound, float volume, float pitch, boolean big) {
        // Every kill this pulse makes belongs to the slot holding the beacon weapon (see Loadout#acting).
        ServerPlayer actingOwner = beacon.level.getServer().getPlayerList().getPlayer(beacon.ownerId);
        com.solme.emberfall.item.Loadout ownerLoadout = actingOwner == null ? null : com.solme.emberfall.item.Loadout.peek(actingOwner);
        if (actingOwner != null && ownerLoadout != null) {
            com.solme.emberfall.item.Loadout.acting(actingOwner, ownerLoadout.indexOf("ashen_beacon"),
                    () -> pulseAtInner(beacon, damage, sound, volume, pitch, big));
        } else {
            pulseAtInner(beacon, damage, sound, volume, pitch, big);
        }
    }

    private static void pulseAtInner(Beacon beacon, float damage, SoundEvent sound, float volume, float pitch, boolean big) {
        beacon.level.playSound(null, beacon.pos.x, beacon.pos.y, beacon.pos.z, sound, SoundSource.PLAYERS, volume, pitch);
        beacon.level.sendParticles(ParticleTypes.EXPLOSION, beacon.pos.x, beacon.pos.y + 0.3, beacon.pos.z,
                big ? 3 : 1, big ? 0.4 : 0.0, big ? 0.3 : 0.0, big ? 0.4 : 0.0, 0.0);

        double radius = (big ? 1.4 : 1.0) * BeaconSystem.radius(beacon.weaponLevel);
        AABB box = AABB.ofSize(beacon.pos, radius * 2, radius * 2, radius * 2);
        List<Mob> targets = beacon.level.getEntitiesOfClass(Mob.class, box,
                mob -> mob.isAlive() && AutoAttackSystem.isEmberfallHostile(mob)
                        && mob.position().distanceTo(beacon.pos) <= radius);

        ServerPlayer owner = beacon.level.getServer().getPlayerList().getPlayer(beacon.ownerId);
        float dealtTotal = 0.0F;
        for (Mob mob : targets) {
            float healthBefore = mob.getHealth();
            Vec3 deathPos = mob.position();
            mob.invulnerableTime = 0;
            if (owner != null) {
                mob.hurtServer(beacon.level, owner.damageSources().playerAttack(owner), damage);
                float damageDealt = Math.max(0.0F, healthBefore - mob.getHealth());
                dealtTotal += damageDealt;
                OnHitEffects.apply(owner, mob, damageDealt);
                if (mob.isAlive()) {
                    mob.igniteForSeconds(BeaconSystem.BURN_SECONDS);
                }
                // Undying Embers tier 3: this pulse's killing blow immediately plants a fresh
                // Ember at the kill site. Gated off !beacon.isKillEmber so a kill-ember's own
                // pulses can never start a further chain - see the field javadoc above. If a
                // single AoE pulse kills more than one mob, the last kill in this loop is the
                // one that ends up placed - spawnKillEmber replaces, not stacks, so that's a
                // harmless tie-break rather than a growing list.
                if (!mob.isAlive() && !beacon.isKillEmber && CombatStats.of(owner).undyingEmbersTier >= 3) {
                    spawnKillEmber(beacon, deathPos);
                }
            } else {
                // Owner disconnected mid-lifespan (should already be pruned via clear()
                // on run leave, but guarded here too) - deal the same damage as a neutral
                // in-world source so it can't be player-attributed to nobody.
                mob.hurtServer(beacon.level, beacon.level.damageSources().onFire(), damage);
            }
        }
        // Growth (BeaconSystem): only a MAIN beacon's ordinary pulse grows the weapon. Embers and the final burst are the Tome's, not the weapon's.
        if (owner != null && !beacon.isEmber && !big) {
            BeaconSystem.satelliteBursts(beacon.level, owner, beacon.pos,
                    BeaconSystem.radius(beacon.weaponLevel) + 6.0, beacon.weaponLevel, damage, targets);
            if (dealtTotal > 0.0F) {
                com.solme.emberfall.item.WeaponProgress.onHit(owner);
                if (com.solme.emberfall.item.WeaponProgress.ultimateReady(owner)
                        && com.solme.emberfall.item.WeaponProgress.consumeUltimate(owner)) {
                    BeaconSystem.pyreNova(beacon.level, owner, beacon.pos, beacon.weaponLevel, damage,
                            com.solme.emberfall.item.Loadout.actingIndexFor(owner));
                }
            }
        }
    }

    private static final boolean TEST_MODE = Boolean.getBoolean("emberfall.testMode");

    /**
     * The main beacon's LIVING FLAMES, one step per tick. The number of flames follows the weapon level (fractional, see
     * {@link BeaconSystem#flames}); each one hunts the densest group of foes within reach of the beacon, flies at the level's speed with a
     * small wobble so it reads as alive, cuts and ignites foes in its contact radius (one cut per foe per half second) and drags foes in
     * range a little toward itself every half second. Every contact fills the weapon meter, so the Pyre Nova is reachable.
     * No entities: positions are plain vectors and the look is particles.
     */
    private static void tickFlames(Beacon beacon) {
        ServerPlayer owner = beacon.level.getServer().getPlayerList().getPlayer(beacon.ownerId);
        if (owner == null) {
            return;
        }
        final int lvl = beacon.weaponLevel;
        final long now = beacon.level.getGameTime();
        // Quiver of Plenty adds whole flames; the total stays under a hard ceiling so the hunt cost is bounded whatever the player holds.
        final double flameCount = Math.min(8.0, BeaconSystem.flames(lvl) + com.solme.emberfall.relic.RelicTempoGame.bonusStrikes(owner));
        int want = (int) Math.ceil(flameCount - 1.0E-9);
        // Fractional count: the extra flame is present for the share of each 100 ticks that the fraction is, judged by the clock, not a roll.
        double whole = Math.floor(flameCount);
        double frac = flameCount - whole;
        if (frac > 1.0E-9 && (beacon.ticksLived % 100) >= frac * 100.0) {
            want = (int) whole;
        }
        while (beacon.flames.size() < want) {
            beacon.flames.add(beacon.pos.add(0.0, 0.6, 0.0));
            beacon.flameTarget.add(null);
            beacon.flameUntil.add(0L);
        }
        while (beacon.flames.size() > want) {
            beacon.flames.remove(beacon.flames.size() - 1);
            beacon.flameTarget.remove(beacon.flameTarget.size() - 1);
            beacon.flameUntil.remove(beacon.flameUntil.size() - 1);
        }

        double hunt = BeaconSystem.flameHunt(lvl);
        List<Mob> foes = BeaconSystem.foesIn(beacon.level, beacon.pos, hunt, null);
        List<Vec3> spots = new ArrayList<>();
        for (Mob m : foes) {
            spots.add(m.position().add(0.0, m.getBbHeight() * 0.5, 0.0));
        }
        double step = BeaconSystem.flameStep(lvl);
        double contact = BeaconSystem.flameContact(lvl);
        float hit = (float) (beacon.pulseDamage * BeaconSystem.FLAME_DAMAGE_FRACTION);
        boolean pullTick = beacon.ticksLived % BeaconSystem.FLAME_CONTACT_TICKS == 0;

        List<Vec3> pool = new ArrayList<>(spots);
        for (int i = 0; i < beacon.flames.size(); i++) {
            Vec3 at = beacon.flames.get(i);
            // COMMITMENT: keep the current target while it is alive, in reach and inside its dwell; otherwise pick anew.
            Mob committed = null;
            UUID want_id = beacon.flameTarget.get(i);
            if (want_id != null && now < beacon.flameUntil.get(i)) {
                for (Mob m : foes) {
                    if (m.getUUID().equals(want_id) && m.isAlive()) {
                        committed = m;
                        break;
                    }
                }
            }
            Vec3 goal;
            int pick = -1;
            if (committed != null) {
                goal = committed.position().add(0.0, committed.getBbHeight() * 0.5, 0.0);
                pick = foes.indexOf(committed);
            } else {
                // Fresh pick from the foes NOT in a group a lower-numbered flame already claimed, so flames split up when they can.
                List<Vec3> from = pool.isEmpty() ? spots : pool;
                int idx = BeaconSystem.pickTarget(at, from, contact);
                if (idx >= 0) {
                    final Vec3 claimed = from.get(idx);
                    pick = spots.indexOf(claimed);
                    pool.removeIf(v -> v.distanceTo(claimed) <= BeaconSystem.FLAME_CLUMP_RADIUS);
                }
                goal = pick >= 0 ? spots.get(pick) : beacon.pos.add(0.0, 0.6, 0.0);
                beacon.flameTarget.set(i, pick >= 0 ? foes.get(pick).getUUID() : null);
                beacon.flameUntil.set(i, now + BeaconSystem.FLAME_DWELL_TICKS);
            }
            if (committed != null) {
                final Vec3 claimed = goal;
                pool.removeIf(v -> v.distanceTo(claimed) <= BeaconSystem.FLAME_CLUMP_RADIUS);
            }
            Vec3 toward = goal.subtract(at);
            double dist = toward.length();
            Vec3 move = dist < 1.0E-4 ? Vec3.ZERO : toward.scale(Math.min(step, dist) / dist);
            // Wobble: a small sideways weave that differs per flame, so a pack of flames does not fly in a single line.
            double wob = Math.sin((beacon.ticksLived + i * 23) * 0.35) * 0.05;
            Vec3 side = new Vec3(-toward.z, 0.0, toward.x);
            if (side.lengthSqr() > 1.0E-6) {
                move = move.add(side.normalize().scale(wob));
            }
            at = at.add(move);
            beacon.flames.set(i, at);

            beacon.level.sendParticles(ParticleTypes.FLAME, at.x, at.y, at.z, 3, 0.12, 0.12, 0.12, 0.01);
            beacon.level.sendParticles(ParticleTypes.SMALL_FLAME, at.x, at.y + 0.1, at.z, 2, 0.2, 0.15, 0.2, 0.0);
            if (beacon.ticksLived % 4 == 0) {
                beacon.level.sendParticles(ParticleTypes.LAVA, at.x, at.y, at.z, 1, 0.1, 0.1, 0.1, 0.0);
            }

            for (Mob m : foes) {
                if (!m.isAlive()) {
                    continue;
                }
                double d = m.position().add(0.0, m.getBbHeight() * 0.5, 0.0).distanceTo(at);
                if (pullTick && d <= hunt) {
                    AutoAttackSystem.pullToward(m, at, BeaconSystem.flamePull(lvl));
                    m.hurtMarked = true;
                }
                if (d > contact) {
                    continue;
                }
                final String contactKey = i + ":" + m.getUUID();
                Long last = beacon.flameContact.get(contactKey);
                if (last != null && now - last < BeaconSystem.FLAME_CONTACT_TICKS) {
                    continue;
                }
                beacon.flameContact.put(contactKey, now);
                com.solme.emberfall.item.Loadout ownerLoadout = com.solme.emberfall.item.Loadout.peek(owner);
                if (ownerLoadout == null) {
                    continue;
                }
                int idx = ownerLoadout.indexOf("ashen_beacon");
                final Mob target = m;
                final float dmg = hit;
                final Vec3 flameAt = at;
                com.solme.emberfall.item.Loadout.acting(owner, idx, () -> {
                    float dealt = BeaconSystem.scorch(beacon.level, owner, target, dmg);
                    if (dealt > 0.0F) {
                        com.solme.emberfall.item.WeaponProgress.onHit(owner, BeaconSystem.FLAME_METER_GAIN);
                    }
                    if (TEST_MODE) {
                        com.solme.emberfall.EmberfallMod.LOGGER.info("BEACON_TEST flame-hit tag={} dealt={} tick={}",
                                String.join("+", new java.util.TreeSet<>(target.getTags())), String.format("%.2f", dealt), now);
                    }
                });
            }
        }
        if (beacon.flameContact.size() > 64) {
            beacon.flameContact.entrySet().removeIf(e -> now - e.getValue() > 100);
        }
    }

    /**
     * Extinguishes {@code player}'s active beacon(s) - including any lingering Ember. Call on
     * run leave/disconnect ({@link com.solme.emberfall.world.RunManager#leavePlayer}) so a
     * beacon doesn't keep pulsing into an empty arena after its owner is gone.
     */
    public static void clear(ServerPlayer player) {
        ACTIVE.removeIf(b -> b.ownerId.equals(player.getUUID()));
        PyreLantern.clear(player.getUUID());
    }
}
