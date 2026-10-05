package com.solme.emberfall.combat;

import com.solme.emberfall.tome.CombatStats;
import com.solme.emberfall.tome.SynergyEffects;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.TamableAnimal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.Comparator;
import java.util.List;

/**
 * Applies a player's on-hit Tome effects (design doc 7.1-7.2: ignite, chill,
 * poison, lightning chain, on-kill detonation, lifesteal) after
 * AutoAttackSystem lands a real vanilla hit. Reads the actual measured
 * damage dealt (health before minus health after the attack call) rather
 * than re-deriving vanilla's damage formula, so lifesteal/chain/detonate are
 * always exactly proportional to what really landed - crits, armor,
 * resistances and all.
 */
public final class OnHitEffects {
    private OnHitEffects() {}

    public static void apply(ServerPlayer player, LivingEntity target, float damageDealt) {
        if (damageDealt <= 0.0F) {
            return;
        }
        CombatStats stats = CombatStats.of(player);
        RandomSource random = player.getRandom();
        boolean killedByThisHit = !target.isAlive();
        ServerLevel level = player.level() instanceof ServerLevel sl ? sl : null;

        // Base-loop feedback: EVERY landed hit sparks, so combat always speaks. Before this, hit feedback
        // only fired on rare empowered swings (measured: 2 particle packets in 45s with 61 mobs on screen).
        if (level != null) {
            boolean heavy = damageDealt >= target.getMaxHealth() * 0.25F;
            Fx.hit(level, target, heavy);
        }

        if (stats.lifestealPercent > 0.0) {
            player.heal((float) (damageDealt * stats.lifestealPercent));
            if (SynergyEffects.lifestealBonusActive(player)) {
                player.addEffect(new MobEffectInstance(MobEffects.ABSORPTION, 100, 0));
            }
        }

        // Sanguine Locket (Lifesteal tag): independent of Bloodletting's per-hit heal above -
        // a defensive burst on kill rather than sustained healing.
        if (killedByThisHit && stats.killShieldAmplifier >= 0) {
            player.addEffect(new MobEffectInstance(MobEffects.ABSORPTION, stats.killShieldDurationTicks, stats.killShieldAmplifier));
        }

        if (stats.igniteChance > 0.0 && target.isAlive() && random.nextDouble() < stats.igniteChance) {
            target.igniteForTicks(stats.igniteDurationTicks);
        }

        // Cinder Wisp (Fire tag): on-kill ignite spread, independent of Ember Touch's own chance above.
        if (killedByThisHit && stats.igniteSpreadRadius > 0.0 && level != null) {
            spreadStatus(level, player, target, stats.igniteSpreadRadius,
                    le -> le.igniteForTicks(stats.igniteSpreadDurationTicks));
        }

        if (stats.chillChance > 0.0 && target.isAlive() && random.nextDouble() < stats.chillChance) {
            target.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, stats.chillDurationTicks, stats.chillAmplifier));
        }

        // Permafrost Shard (Frost tag): on-kill chill spread, independent of Frostbite Fang above.
        if (killedByThisHit && stats.chillSpreadRadius > 0.0 && level != null) {
            spreadStatus(level, player, target, stats.chillSpreadRadius,
                    le -> le.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, stats.chillSpreadDurationTicks, 0)));
        }

        if (stats.poisonChance > 0.0 && target.isAlive() && random.nextDouble() < stats.poisonChance) {
            target.addEffect(new MobEffectInstance(MobEffects.POISON, stats.poisonDurationTicks, stats.poisonAmplifier));
        }

        // Serpent's Mark (Poison tag): on-kill poison spread, independent of Venomous Fang above.
        if (killedByThisHit && stats.poisonSpreadRadius > 0.0 && level != null) {
            spreadStatus(level, player, target, stats.poisonSpreadRadius,
                    le -> le.addEffect(new MobEffectInstance(MobEffects.POISON, stats.poisonSpreadDurationTicks, 0)));
        }

        if (stats.chainChance > 0.0 && random.nextDouble() < stats.chainChance && level != null) {
            chainLightning(level, player, target, damageDealt, stats.chainTargets);
        }

        // Storm Sigil (Lightning tag): a guaranteed chain every N landed hits, tracked
        // independently of Static Discharge's own chance roll above. Counter is live
        // per-hit state on CombatStats, not reset by recompute() - see that field's javadoc.
        if (stats.stormCadenceInterval > 0 && level != null) {
            stats.stormHitCounter++;
            if (stats.stormHitCounter >= stats.stormCadenceInterval) {
                stats.stormHitCounter = 0;
                chainLightning(level, player, target, damageDealt, 2);
            }
        }

        if (stats.detonateChance > 0.0 && killedByThisHit && random.nextDouble() < stats.detonateChance && level != null) {
            detonate(level, player, target, stats.detonateRadius, stats.detonateDamage);
        }

        // Unstable Core (Explosive tag): an independent, smaller on-kill detonate that can
        // hop to a further kill - a second, separate detonate roll from Volatile Rounds above,
        // not an amplifier that requires it. Hop count is hard-capped (unstableMaxHops <= 3)
        // so a busy horde can never turn this into an unbounded chain reaction.
        if (stats.unstableChance > 0.0 && killedByThisHit && random.nextDouble() < stats.unstableChance && level != null) {
            chainDetonate(level, player, target, stats.unstableRadius, stats.unstableDamage, stats.unstableMaxHops);
        }
    }

    /** Applies {@code applyEffect} to every valid nearby victim within {@code radius} of {@code origin}. */
    private static void spreadStatus(ServerLevel level, ServerPlayer player, LivingEntity origin, double radius,
                                       java.util.function.Consumer<LivingEntity> applyEffect) {
        AABB box = new AABB(origin.position(), origin.position()).inflate(radius);
        List<LivingEntity> nearby = level.getEntitiesOfClass(LivingEntity.class, box,
                le -> isValidChainOrBlastVictim(le, origin, player));
        for (LivingEntity le : nearby) {
            applyEffect.accept(le);
        }
    }

    /** Unstable Core: detonates around {@code origin} exactly once (VFX + a single damage pass
     * over everyone in radius - never double-hits, unlike calling detonate() and then re-looping
     * would), and if that pass kills something and hopsRemaining > 0, hops the chain to that one
     * kill. Only the first kill found in a given blast continues the chain, so a dense crowd
     * can't fan a single trigger out into many parallel chains - depth stays hard-capped either way. */
    private static void chainDetonate(ServerLevel level, ServerPlayer player, LivingEntity origin,
                                       double radius, float damage, int hopsRemaining) {
        Vec3 pos = origin.position();
        level.sendParticles(ParticleTypes.EXPLOSION, pos.x, pos.y + 0.5, pos.z, 1, 0.0, 0.0, 0.0, 0.0);
        level.playSound(null, pos.x, pos.y, pos.z, SoundEvents.GENERIC_EXPLODE, SoundSource.PLAYERS, 0.8f, 1.3f);

        AABB box = new AABB(pos, pos).inflate(radius);
        List<LivingEntity> nearby = level.getEntitiesOfClass(LivingEntity.class, box,
                le -> isValidChainOrBlastVictim(le, origin, player));
        LivingEntity chainInto = null;
        for (LivingEntity le : nearby) {
            le.hurtServer(level, player.damageSources().playerAttack(player), damage);
            if (chainInto == null && hopsRemaining > 0 && !le.isAlive()) {
                chainInto = le;
            }
        }
        if (chainInto != null) {
            chainDetonate(level, player, chainInto, radius, damage, hopsRemaining - 1);
        }
    }

    /** Static Discharge (Lightning tag): arcs the same damage to nearby enemies, chained one to the next. */
    private static void chainLightning(ServerLevel level, ServerPlayer player, LivingEntity primaryTarget,
                                        float damage, int maxTargets) {
        AABB box = primaryTarget.getBoundingBox().inflate(5.0, 3.0, 5.0);
        List<LivingEntity> nearby = level.getEntitiesOfClass(LivingEntity.class, box,
                le -> isValidChainOrBlastVictim(le, primaryTarget, player));
        nearby.sort(Comparator.comparingDouble(le -> le.distanceTo(primaryTarget)));

        Vec3 from = primaryTarget.position().add(0.0, primaryTarget.getBbHeight() * 0.5, 0.0);
        int hit = 0;
        for (LivingEntity le : nearby) {
            if (hit >= maxTargets) {
                break;
            }
            le.hurtServer(level, player.damageSources().playerAttack(player), damage);
            Vec3 to = le.position().add(0.0, le.getBbHeight() * 0.5, 0.0);
            spawnArc(level, from, to);
            level.playSound(null, le.getX(), le.getY(), le.getZ(), SoundEvents.TRIDENT_THUNDER, SoundSource.PLAYERS, 0.6f, 1.4f);
            from = to;
            hit++;
        }
    }

    private static void spawnArc(ServerLevel level, Vec3 from, Vec3 to) {
        Vec3 dir = to.subtract(from);
        double dist = Math.max(0.5, dir.length());
        dir = dir.normalize();
        for (double d = 0.0; d < dist; d += 0.5) {
            Vec3 p = from.add(dir.scale(d));
            level.sendParticles(ParticleTypes.ELECTRIC_SPARK, p.x, p.y, p.z, 1, 0.05, 0.05, 0.05, 0.0);
        }
    }

    /** Volatile Rounds (Explosive tag): a killed target detonates, damaging nearby enemies (no block damage). */
    private static void detonate(ServerLevel level, ServerPlayer player, LivingEntity target, double radius, float damage) {
        Vec3 pos = target.position();
        level.sendParticles(ParticleTypes.EXPLOSION, pos.x, pos.y + 0.5, pos.z, 1, 0.0, 0.0, 0.0, 0.0);
        level.playSound(null, pos.x, pos.y, pos.z, SoundEvents.GENERIC_EXPLODE, SoundSource.PLAYERS, 1.0f, 1.0f);

        AABB box = new AABB(pos, pos).inflate(radius);
        List<LivingEntity> nearby = level.getEntitiesOfClass(LivingEntity.class, box,
                le -> isValidChainOrBlastVictim(le, target, player));
        for (LivingEntity le : nearby) {
            le.hurtServer(level, player.damageSources().playerAttack(player), damage);
        }
    }

    /**
     * Chain/blast effects may only ever hurt the run's own hostile mobs. Runs now happen in the
     * player's REAL world, so villagers, golems, livestock, horses and other players' pets are all
     * standing around: the old "anything alive except players and my own pets" rule would have
     * killed them. Same namespace gate the auto-attack targeting already uses.
     */
    private static boolean isValidChainOrBlastVictim(LivingEntity candidate, LivingEntity origin, ServerPlayer player) {
        if (candidate == origin || candidate == player || !candidate.isAlive() || candidate instanceof Player) {
            return false;
        }
        return candidate instanceof net.minecraft.world.entity.Mob mob && AutoAttackSystem.isEmberfallHostile(mob);
    }
}
