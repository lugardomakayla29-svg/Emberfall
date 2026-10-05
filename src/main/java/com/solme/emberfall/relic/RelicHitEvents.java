package com.solme.emberfall.relic;

import com.solme.emberfall.combat.AutoAttackSystem;
import com.solme.emberfall.world.RunManager;
import com.solme.emberfall.world.RunStats;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.phys.AABB;

import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * The offensive relics, applied to every hit a run player lands on an Emberfall hostile, whichever of the eight weapons
 * (or a Tome effect) delivered it. The decisions live in the pure {@link RelicOffence}; this class reads the game
 * event, asks it, and applies the answer.
 *
 *  - {@link #scale} runs BEFORE the hit (called by the mixin on LivingEntity.hurtServer): Anvil, Crown, Soul, Big Bonk.
 *  - the AFTER_DAMAGE listener runs once the hit landed with the real damage: Blood Chalice, Spiked Censer, Frost.
 *
 * A Censer blast is itself a player hit, so a guard stops it from scaling, healing or blasting again.
 */
public final class RelicHitEvents {
    private RelicHitEvents() {}

    /** Players currently inside a Censer blast, so the blast's own hits cannot trigger relics again. */
    private static final Set<UUID> IN_BLAST = ConcurrentHashMap.newKeySet();

    /** The run player behind this damage source when it is a direct player attack with relics, otherwise null. */
    private static ServerPlayer attackerOf(LivingEntity victim, DamageSource source) {
        if (!(source.getEntity() instanceof ServerPlayer p) || !(victim instanceof Mob mob) || !AutoAttackSystem.isEmberfallHostile(mob)) {
            return null;
        }
        if (!PlayerRelics.active(p) || RunManager.slotOf(p) == null || IN_BLAST.contains(p.getUUID())) {
            return null;
        }
        // Only weapon / tome damage: not the Thorn Vest reflect or environmental damage that merely names the player.
        return source.is(net.minecraft.world.damagesource.DamageTypes.PLAYER_ATTACK) ? p : null;
    }

    /** Called by the mixin with the incoming amount; returns the amount to actually use. */
    public static float scale(LivingEntity victim, DamageSource source, float amount) {
        ServerPlayer p = attackerOf(victim, source);
        if (p == null || amount <= 0.0F) {
            return amount;
        }
        RelicStats stats = RelicEffects.stats(p);
        float out = RelicOffence.scaledDamage(stats, amount, RunStats.kills(p), p.getRandom()::nextDouble);
        if (out >= amount * (float) RelicStats.BONK_MULTIPLIER * 0.99F && stats.bonkChance() > 0.0) {
            ((ServerLevel) p.level()).sendParticles(net.minecraft.core.particles.ParticleTypes.CRIT,
                    victim.getX(), victim.getY() + victim.getBbHeight() * 0.6, victim.getZ(), 24, 0.4, 0.4, 0.4, 0.3);
            p.sendSystemMessage(net.minecraft.network.chat.Component.literal("BONK!"), true);
        }
        return out;
    }

    public static void register() {
        ServerLivingEntityEvents.AFTER_DAMAGE.register((victim, source, baseDamage, damageTaken, blocked) -> {
            ServerPlayer p = attackerOf(victim, source);
            if (p == null || blocked || damageTaken <= 0.0F) {
                return;
            }
            RelicStats stats = RelicEffects.stats(p);
            float heal = RelicOffence.lifesteal(stats, damageTaken);
            if (heal > 0.0F) {
                p.heal(heal);
            }
            if (RelicOffence.censerProcs(stats, p.getRandom()::nextDouble)) {
                censerBlast(p, victim, damageTaken);
            }
            boolean chilled = victim.hasEffect(MobEffects.SLOWNESS) && victim.getEffect(MobEffects.SLOWNESS).getAmplifier() >= 1;
            switch (RelicOffence.frostOutcome(stats, chilled, p.getRandom()::nextDouble)) {
                case CHILL -> victim.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, RelicStats.FROST_CHILL_TICKS, 1));
                case FREEZE -> {
                    victim.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, RelicStats.FROST_FREEZE_TICKS, 4));
                    ((ServerLevel) p.level()).sendParticles(net.minecraft.core.particles.ParticleTypes.SNOWFLAKE,
                            victim.getX(), victim.getY() + victim.getBbHeight() * 0.5, victim.getZ(), 16, 0.3, 0.4, 0.3, 0.05);
                }
                default -> { }
            }
        });
    }

    /** A small blast around the struck foe: a share of the hit to the nearest few OTHER hostiles. */
    private static void censerBlast(ServerPlayer p, LivingEntity centre, float damageTaken) {
        ServerLevel level = (ServerLevel) p.level();
        float each = RelicOffence.censerDamage(damageTaken);
        if (each <= 0.0F) {
            return;
        }
        AABB box = centre.getBoundingBox().inflate(RelicStats.CENSER_RADIUS);
        List<Mob> near = level.getEntitiesOfClass(Mob.class, box,
                m -> m != centre && m.isAlive() && AutoAttackSystem.isEmberfallHostile(m));
        near.sort(Comparator.comparingDouble(m -> m.distanceToSqr(centre)));
        level.sendParticles(net.minecraft.core.particles.ParticleTypes.EXPLOSION, centre.getX(), centre.getY() + 0.5, centre.getZ(), 1, 0, 0, 0, 0);
        IN_BLAST.add(p.getUUID());
        try {
            for (int i = 0; i < Math.min(near.size(), RelicStats.CENSER_MAX_VICTIMS); i++) {
                near.get(i).hurtServer(level, p.damageSources().playerAttack(p), each);
            }
        } finally {
            IN_BLAST.remove(p.getUUID());
        }
    }
}
