package com.solme.emberfall.relic;

import com.solme.emberfall.world.RunManager;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;

import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Wires the defensive relics to the game: Ender Pearl Shard (dodge), Mirror Shard (invulnerability window + reflect),
 * Thorn Vest (reflect) and Totem of Returning (survive one fatal hit per run). The decisions themselves live in the
 * pure {@link RelicDefence}; this class only reads the event, asks it, and applies the answer.
 *
 * Only players inside a run are touched, and all per-player state is dropped by {@link #clear} when the run ends.
 */
public final class RelicDefenceEvents {
    private RelicDefenceEvents() {}

    /** Game tick until which a player's Mirror window runs / until which the Mirror is on cooldown. */
    private static final Map<UUID, Long> MIRROR_UNTIL = new ConcurrentHashMap<>();
    private static final Map<UUID, Long> MIRROR_COOLDOWN_UNTIL = new ConcurrentHashMap<>();
    /** Players whose Totem is already spent this run. */
    private static final Set<UUID> TOTEM_SPENT = ConcurrentHashMap.newKeySet();
    /** Guards against a reflected hit reflecting back again. */
    private static final Set<UUID> REFLECTING = ConcurrentHashMap.newKeySet();

    public static void clear(ServerPlayer player) {
        MIRROR_UNTIL.remove(player.getUUID());
        MIRROR_COOLDOWN_UNTIL.remove(player.getUUID());
        TOTEM_SPENT.remove(player.getUUID());
        REFLECTING.remove(player.getUUID());
    }

    public static boolean totemSpent(ServerPlayer player) {
        return TOTEM_SPENT.contains(player.getUUID());
    }

    private static boolean inRun(LivingEntity e) {
        return e instanceof ServerPlayer p && PlayerRelics.active(p) && RunManager.slotOf(p) != null;
    }

    private static long now(ServerPlayer p) {
        return ((ServerLevel) p.level()).getGameTime();
    }

    private static int ticksLeft(Map<UUID, Long> map, ServerPlayer p) {
        Long until = map.get(p.getUUID());
        return until == null ? 0 : (int) Math.max(0L, until - now(p));
    }

    public static void register() {
        ServerLivingEntityEvents.ALLOW_DAMAGE.register((entity, source, amount) -> {
            if (!inRun(entity) || amount <= 0.0F) {
                return true;
            }
            ServerPlayer player = (ServerPlayer) entity;
            RelicStats stats = RelicEffects.stats(player);
            RelicDefence.Verdict v = RelicDefence.judge(stats, ticksLeft(MIRROR_UNTIL, player), player.getRandom()::nextDouble);
            if (v == RelicDefence.Verdict.DODGED) {
                ServerLevel level = (ServerLevel) player.level();
                level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.ENDERMAN_TELEPORT, SoundSource.PLAYERS, 0.5F, 1.6F);
                level.sendParticles(net.minecraft.core.particles.ParticleTypes.PORTAL, player.getX(), player.getY() + 1.0, player.getZ(), 12, 0.3, 0.5, 0.3, 0.2);
                player.sendSystemMessage(net.minecraft.network.chat.Component.literal("Dodged!"), true);
            }
            return v == RelicDefence.Verdict.LANDS;
        });

        ServerLivingEntityEvents.AFTER_DAMAGE.register((entity, source, baseDamage, damageTaken, blocked) -> {
            if (!inRun(entity) || blocked || damageTaken <= 0.0F) {
                return;
            }
            ServerPlayer player = (ServerPlayer) entity;
            RelicStats stats = RelicEffects.stats(player);
            // Mirror Shard: a landed hit opens the invulnerability window, then goes on cooldown.
            if (RelicDefence.mirrorReady(stats, ticksLeft(MIRROR_COOLDOWN_UNTIL, player))) {
                long t = now(player);
                MIRROR_UNTIL.put(player.getUUID(), t + RelicStats.MIRROR_INVULN_TICKS);
                MIRROR_COOLDOWN_UNTIL.put(player.getUUID(), t + RelicStats.MIRROR_COOLDOWN_TICKS);
                ((ServerLevel) player.level()).playSound(null, player.getX(), player.getY(), player.getZ(),
                        SoundEvents.AMETHYST_BLOCK_RESONATE, SoundSource.PLAYERS, 0.8F, 1.2F);
            }
            // Reflect (Thorn Vest + Mirror Shard) only against a true melee attacker, never a projectile or an explosion.
            LivingEntity attacker = meleeAttacker(source);
            if (attacker == null || attacker == player || REFLECTING.contains(player.getUUID())) {
                return;
            }
            float back = RelicDefence.reflected(stats, damageTaken);
            if (back <= 0.0F || !attacker.isAlive()) {
                return;
            }
            REFLECTING.add(player.getUUID());
            try {
                attacker.hurtServer((ServerLevel) attacker.level(), player.damageSources().thorns(player), back);
            } finally {
                REFLECTING.remove(player.getUUID());
            }
        });
    }

    /** The attacker of a melee hit, or null for projectiles, explosions, fire, falls and anything indirect. */
    static LivingEntity meleeAttacker(DamageSource source) {
        if (source.getDirectEntity() == null || source.getDirectEntity() != source.getEntity()) {
            return null;
        }
        return source.getEntity() instanceof LivingEntity le ? le : null;
    }

    /**
     * Called from the run-death handler BEFORE the run ends. Returns true when the Totem of Returning absorbed the
     * fatal hit (the caller then cancels the death and must NOT end the run).
     */
    public static boolean tryTotem(ServerPlayer player) {
        if (!inRun(player) || !RelicDefence.totemSaves(RelicEffects.stats(player), TOTEM_SPENT.contains(player.getUUID()))) {
            return false;
        }
        TOTEM_SPENT.add(player.getUUID());
        player.setHealth(RelicDefence.totemHealth(player.getMaxHealth()));
        player.clearFire();
        player.fallDistance = 0;
        // A short grace, like the run-end path, so the same mob cannot finish the player in the same instant.
        player.addEffect(new net.minecraft.world.effect.MobEffectInstance(net.minecraft.world.effect.MobEffects.RESISTANCE, 60, 4));
        player.addEffect(new net.minecraft.world.effect.MobEffectInstance(net.minecraft.world.effect.MobEffects.REGENERATION, 100, 1));
        ServerLevel level = (ServerLevel) player.level();
        level.sendParticles(net.minecraft.core.particles.ParticleTypes.TOTEM_OF_UNDYING, player.getX(), player.getY() + 1.0, player.getZ(), 60, 0.5, 0.8, 0.5, 0.4);
        level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.TOTEM_USE, SoundSource.PLAYERS, 1.0F, 1.0F);
        player.sendSystemMessage(net.minecraft.network.chat.Component.literal("Totem of Returning saved you!"), false);
        return true;
    }
}
