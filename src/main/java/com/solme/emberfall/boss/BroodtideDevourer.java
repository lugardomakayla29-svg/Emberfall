package com.solme.emberfall.boss;

import com.solme.emberfall.combat.AutoAttackSystem;
import com.solme.emberfall.entity.BroodtideBody;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.UUID;

/**
 * The entity side of the Broodtide's Devour (docs/PLAN_broodtide.md section 3). Every decision comes from the pure {@link BroodtideDevour}; this class reads the world, drives
 * the reaching arm, hides a mob, brings it back and keeps the cap. Zero new entities: a swallowed mob is the SAME mob, hidden (never deleted), and it returns with its AI, aggro and
 * team intact (Prototype A, measured live). Only the two proven types are ever eaten.
 *
 * <p>Hide = invulnerable + silent + invisible + NoAI + the {@link AutoAttackSystem#SWALLOWED_TAG} tag (so weapons skip it while the wave cap still counts it). What the mob had
 * BEFORE (its own NoAI, silent, invulnerable and any invisibility effect) is recorded and restored exactly, never guessed.
 */
public final class BroodtideDevourer {
    /** Scoreboard tag on a mob the Devour has spat out and that is still alive: it is a Brood-Kin. Counts toward the cap of {@link BroodtideDevour#BROOD_KIN_CAP}. */
    public static final String KIN_TAG = "emberfall_brood_kin";

    /** One mob in the Devour's care: reaching for it, or swallowed. */
    private static final class Eaten {
        final UUID mob;
        final long startedAt;
        long swallowedAt = -1;
        /** What the mob was like before, restored exactly on the spit. */
        boolean wasNoAi, wasSilent, wasInvulnerable;
        MobEffectInstance hadInvisibility;
        /** The mob's own max health before the Brood-Kin bonus, so a test can read the factor. */
        double baseMaxHealth;
        Eaten(UUID mob, long startedAt) {
            this.mob = mob;
            this.startedAt = startedAt;
        }
        boolean swallowed() {
            return swallowedAt >= 0;
        }
    }

    private final List<Eaten> eaten = new ArrayList<>();
    private long lastStart = -1;
    private int eatsStarted = 0;
    private int spits = 0;


    public int eatsStarted() {
        return eatsStarted;
    }

    public int spits() {
        return spits;
    }

    /** Mobs currently reached for or swallowed. */
    public int inCare() {
        return eaten.size();
    }

    public int swallowedNow() {
        int n = 0;
        for (Eaten e : eaten) {
            if (e.swallowed()) {
                n++;
            }
        }
        return n;
    }

    /** Called once a tick by the body, after the Grabber. */
    public void tick(ServerLevel level, BroodtideBody body, long fightTick, BroodtideGrab.Phase phase) {
        Iterator<Eaten> it = eaten.iterator();
        while (it.hasNext()) {
            Eaten e = it.next();
            Entity ent = level.getEntity(e.mob);
            if (!(ent instanceof Mob mob) || !mob.isAlive() || !body.isAlive()) {
                // The mob died or vanished (or the boss did): nothing to return. If the boss is gone, give the mob back first so it is not left hidden.
                if (ent instanceof Mob m && m.isAlive()) {
                    reveal(m, e);
                }
                it.remove();
                clearArmIfIdle(body);
                continue;
            }
            long age = fightTick - e.startedAt;
            if (!e.swallowed()) {
                if (age < BroodtideDevour.WINDUP_TICKS) {
                    reachTelegraph(level, body, mob, age);
                } else {
                    swallow(level, body, mob, e, fightTick);
                }
            } else {
                pullIn(body, mob, e, fightTick);
                if (BroodtideDevour.shouldSpit(fightTick, e.swallowedAt)) {
                    spit(level, body, mob, e, fightTick);
                    it.remove();
                    clearArmIfIdle(body);
                }
            }
        }

        if (eaten.isEmpty() || !anyReaching()) {
            tryBegin(level, body, fightTick, phase);
        }
    }

    private boolean anyReaching() {
        for (Eaten e : eaten) {
            if (!e.swallowed()) {
                return true;
            }
        }
        return false;
    }

    private void clearArmIfIdle(BroodtideBody body) {
        if (!anyReaching() && body.arms() != null) {
            body.arms().clearReach();
        }
    }

    /** Brood-Kin alive now, counting those swallowed: the number the cap is checked against. */
    public int kinAlive(ServerLevel level, BroodtideBody body) {
        int n = 0;
        for (Mob m : level.getEntitiesOfClass(Mob.class, arenaBox(body), x -> x.isAlive() && x.getTags().contains(KIN_TAG))) {
            n++;
        }
        for (Eaten e : eaten) {
            Entity ent = level.getEntity(e.mob);
            if (e.swallowed() && ent instanceof Mob m && m.isAlive() && !m.getTags().contains(KIN_TAG)) {
                n++;   // swallowed but not yet tagged as spat-out kin: it will be, so it already holds a slot
            }
        }
        return n;
    }

    private static AABB arenaBox(BroodtideBody body) {
        return body.getBoundingBox().inflate(80.0, 40.0, 80.0);
    }

    private void tryBegin(ServerLevel level, BroodtideBody body, long fightTick, BroodtideGrab.Phase phase) {
        if (!BroodtideDevour.mayStartEat(fightTick, lastStart, phase, anyReaching(), kinAlive(level, body))) {
            return;
        }
        if (body.arms() == null) {
            return;
        }
        Mob best = null;
        double bestD = Double.MAX_VALUE;
        for (Mob m : level.getEntitiesOfClass(Mob.class, body.getBoundingBox().inflate(BroodtideDevour.REACH, 6.0, BroodtideDevour.REACH))) {
            String id = BuiltInRegistries.ENTITY_TYPE.getKey(m.getType()).toString();
            if (!BroodtideDevour.isEdibleType(id)) {
                continue;
            }
            double d = Math.hypot(m.getX() - body.getX(), m.getZ() - body.getZ());
            boolean kin = m.getTags().contains(KIN_TAG);
            boolean swallowed = m.getTags().contains(AutoAttackSystem.SWALLOWED_TAG);
            if (!com.solme.emberfall.world.RunMobTeam.isTeamMob(m)) {
                continue;   // only a mob standing in a live run's arena: never one outside it
            }
            if (!BroodtideDevour.isEdible(m.isAlive(), true, kin, swallowed, d)) {
                continue;
            }
            if (d < bestD) {
                bestD = d;
                best = m;
            }
        }
        if (best == null) {
            return;
        }
        body.arms().startReach(best.getUUID());
        eaten.add(new Eaten(best.getUUID(), fightTick));
        lastStart = fightTick;
        eatsStarted++;
        level.playSound(null, body.getX(), body.getY(), body.getZ(), SoundEvents.SLIME_SQUISH_SMALL, SoundSource.HOSTILE, 2.0F, 0.5F);
    }

    /** A thin green line from the body to the mob while the arm reaches. At most 8 points, every 4th tick (packet cost rule). */
    private void reachTelegraph(ServerLevel level, BroodtideBody body, Mob mob, long age) {
        if (age % 4 != 0) {
            return;
        }
        DustParticleOptions dust = new DustParticleOptions(0xB6F23A, 1.1F);
        double bx = body.getX(), by = body.getY() + 1.2, bz = body.getZ();
        double dx = mob.getX() - bx, dy = mob.getY() + 0.6 - by, dz = mob.getZ() - bz;
        for (int i = 1; i <= 8; i++) {
            double t = (double) i / 8;
            level.sendParticles(dust, true, true, bx + dx * t, by + dy * t, bz + dz * t, 1, 0.0, 0.0, 0.0, 0.0);
        }
    }

    /** The arm lands: record what the mob was, then hide it. */
    private void swallow(ServerLevel level, BroodtideBody body, Mob mob, Eaten e, long fightTick) {
        double dist = Math.hypot(mob.getX() - body.getX(), mob.getZ() - body.getZ());
        if (dist > BroodtideDevour.REACH + 1.0) {
            // It was out of reach by the time the arm landed (it ran, or was knocked back): the eat misses, the mob is simply left alone.
            eaten.remove(e);
            if (body.arms() != null) {
                body.arms().clearReach();
            }
            return;
        }
        e.wasNoAi = mob.isNoAi();
        e.wasSilent = mob.isSilent();
        e.wasInvulnerable = mob.isInvulnerable();
        e.hadInvisibility = mob.getEffect(MobEffects.INVISIBILITY);
        e.baseMaxHealth = mob.getMaxHealth();
        e.swallowedAt = fightTick;
        mob.addTag(AutoAttackSystem.SWALLOWED_TAG);
        mob.setNoAi(true);
        mob.setSilent(true);
        mob.setInvulnerable(true);
        mob.addEffect(new MobEffectInstance(MobEffects.INVISIBILITY, BroodtideDevour.MAX_HIDDEN_TICKS + 40, 0, false, false));
        mob.setTarget(null);
        level.playSound(null, body.getX(), body.getY(), body.getZ(), SoundEvents.SLIME_ATTACK, SoundSource.HOSTILE, 1.6F, 0.6F);
        level.sendParticles(ParticleTypes.ITEM_SLIME, mob.getX(), mob.getY() + 0.8, mob.getZ(), 14, 0.3, 0.4, 0.3, 0.05);
    }

    /** While swallowed the mob slides to the body's centre and stays inside it (a position set each tick, no sustained velocity). */
    private void pullIn(BroodtideBody body, Mob mob, Eaten e, long fightTick) {
        double t = Math.min(1.0, (double) (fightTick - e.swallowedAt) / Math.max(1, BroodtideDevour.WINDUP_TICKS));
        double f = BroodtideDevour.pullFraction(t);
        Vec3 c = body.position().add(0.0, 1.0, 0.0);
        double x = mob.getX() + (c.x - mob.getX()) * f;
        double y = mob.getY() + (c.y - mob.getY()) * f;
        double z = mob.getZ() + (c.z - mob.getZ()) * f;
        mob.teleportTo(x, Math.max(y, body.getY()), z);
        mob.setDeltaMovement(Vec3.ZERO);
    }

    /** Bring the mob back exactly as it was, as a Brood-Kin: tagged, with more health, and thrown out of the body. */
    private void spit(ServerLevel level, BroodtideBody body, Mob mob, Eaten e, long fightTick) {
        reveal(mob, e);
        mob.addTag(KIN_TAG);
        double hp = BroodtideDevour.kinHealth(e.baseMaxHealth);
        var attr = mob.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.MAX_HEALTH);
        if (attr != null) {
            attr.setBaseValue(hp);
            mob.setHealth((float) hp);
        }
        // Out of the body, in a random direction, with a small hop so it does not stay inside the boss's hit box.
        double a = level.getRandom().nextDouble() * Math.PI * 2.0;
        double r = 3.5;
        mob.teleportTo(body.getX() + Math.cos(a) * r, body.getY(), body.getZ() + Math.sin(a) * r);
        mob.setDeltaMovement(Math.cos(a) * 0.5, 0.45, Math.sin(a) * 0.5);
        mob.hurtMarked = true;
        spits++;
        level.playSound(null, body.getX(), body.getY(), body.getZ(), SoundEvents.SLIME_JUMP, SoundSource.HOSTILE, 2.0F, 0.7F);
        level.sendParticles(ParticleTypes.ITEM_SLIME, mob.getX(), mob.getY() + 0.8, mob.getZ(), 20, 0.4, 0.5, 0.4, 0.08);
    }

    /** Undo the hide, restoring the mob's OWN earlier state. */
    private static void reveal(Mob mob, Eaten e) {
        mob.removeTag(AutoAttackSystem.SWALLOWED_TAG);
        mob.setNoAi(e.wasNoAi);
        mob.setSilent(e.wasSilent);
        mob.setInvulnerable(e.wasInvulnerable);
        mob.removeEffect(MobEffects.INVISIBILITY);
        if (e.hadInvisibility != null) {
            mob.addEffect(new MobEffectInstance(e.hadInvisibility));   // it was invisible before the Devour: it stays so
        }
    }

    /** Gives every swallowed mob back and ends every reach: the boss died or the run was torn down. Idempotent. */
    public void clearAll(ServerLevel level) {
        for (Eaten e : eaten) {
            Entity ent = level.getEntity(e.mob);
            if (ent instanceof Mob m && m.isAlive() && e.swallowed()) {
                reveal(m, e);
            }
        }
        eaten.clear();
    }
}
