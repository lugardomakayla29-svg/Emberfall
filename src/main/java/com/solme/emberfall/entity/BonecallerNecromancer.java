package com.solme.emberfall.entity;

import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.zombie.Zombie;
import net.minecraft.world.entity.monster.zombie.ZombieVillager;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.minecraft.core.BlockPos;

import java.util.HashSet;
import java.util.Set;

/**
 * Corrupted archetype #5: the Bonecaller Necromancer. Ported from
 * SlopPack's BonecallerNecromancer.
 *
 * KEPT: rides a tamed undead horse mount that periodically spawns
 * temporary "Restless Foal" decoys which charge and kick nearby players;
 * Curse (beam-telegraphed DoT that withers a target and heals the caster);
 * Sewage Breath (a lingering nausea/slowness cone, no direct damage -
 * confirmed from source, not an oversight); Minion Summon (spawns 1-2
 * temporary Stitched Brute/Archer/Vanguard adds, occasionally a Stitched
 * Anchorite healer minion instead, capped at 4 active minions); on death
 * the mount is ejected and goes berserk as a "Vengeful Horse" for 30s.
 * All of the mount/minion/healer machinery lives in
 * {@link BonecallerMinionRunner}.
 *
 * DEVIATION (deliberate scope cut, not a fidelity loss by omission): the
 * 15%-per-summon-roll "Charnel Warden" ultimate - a full secondary
 * miniboss with 5 independently-acting invulnerable limb entities - is
 * its own large sub-feature and is deferred to a follow-up pass rather
 * than half-built here. Minion Summon always does the normal minion roll
 * for now.
 *
 * DEVIATION: SlopPack's findNearestPlayer/enrageHorse loops decompile
 * with a stray unused local ("if (!(d2 < d))" comparing a never-assigned
 * d2 instead of the just-computed d3) - the same CFR mis-render already
 * confirmed harmless for Umbral Magus's identical pattern in this same
 * jar. The obviously-intended "compare the freshly computed distance"
 * logic is what's implemented here and in {@link BonecallerMinionRunner}.
 */
public class BonecallerNecromancer extends ZombieVillager {
    private static final double BASE_HEALTH = 85.0;
    /** Larger than fodder, smaller than the Sentinel (3.0). A humanoid above 1.0 no longer fits a 2 high door; the arena is open ground. */
    private static final float ELITE_SCALE = 1.25F;
    private static final double BASE_ATTACK_DAMAGE = 4.5;

    private static final int SUMMON_COOLDOWN_TICKS = 360;   // 18s
    private static final int CURSE_COOLDOWN_TICKS = 280;    // 14s
    private static final int SEWAGE_COOLDOWN_TICKS = 320;   // 16s
    private static final int MAX_ACTIVE_MINIONS = 4;

    private static final double CURSE_TRIGGER_RANGE = 12.0;
    private static final double CURSE_RESOLVE_MAX_RANGE = 14.0;
    private static final double SEWAGE_TRIGGER_RANGE = 7.0;

    private static final int CURSE_TELEGRAPH_TICKS = 14;
    private static final int SUMMON_TELEGRAPH_TICKS = 12;
    private static final int SEWAGE_CAST_LOCK_TICKS = 10;

    private static final double CURSE_DAMAGE = 5.0;
    private static final double CURSE_SELF_HEAL = 5.0;

    private static final int SEWAGE_BREATH_DURATION_TICKS = 60; // 3s, matches SlopPack's 30 checks every 2 ticks
    private static final double SEWAGE_CONE_RANGE = 7.0;
    private static final double SEWAGE_CONE_DOT_THRESHOLD = 0.5; // ~60 degree half-angle
    /** The half-angle the dot-product threshold above works out to, so the outline matches the damage cone. */
    private static final double SEWAGE_CONE_HALF_ANGLE_DEG = Math.toDegrees(Math.acos(SEWAGE_CONE_DOT_THRESHOLD));

    private enum AbilityState { IDLE, TELEGRAPH_CURSE, TELEGRAPH_SEWAGE, TELEGRAPH_SUMMON }

    private double statMultiplier = 1.0;
    private AbilityState state = AbilityState.IDLE;
    private long telegraphEndsAtTick = 0L;
    private LivingEntity curseTarget = null;

    private long nextSummonAtTick = 0L;
    private long nextCurseAtTick = 0L;
    private long nextSewageAtTick = 0L;

    // active sewage breath cone, ticked directly by this entity (it's tied to the caster's own aim, not a companion)
    private boolean breathActive = false;
    private long breathEndsAtTick = 0L;
    private int breathTickCounter = 0;

    private boolean mountSpawned = false;

    public BonecallerNecromancer(EntityType<? extends ZombieVillager> type, Level level) {
        super(type, level);
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Zombie.createAttributes();
    }

    public static BonecallerNecromancer spawn(ServerLevel level, BlockPos pos, double statMultiplier) {
        BonecallerNecromancer necromancer = new BonecallerNecromancer(ModEntities.BONECALLER_NECROMANCER, level);
        necromancer.statMultiplier = statMultiplier;
        necromancer.setPos(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5);
        necromancer.setYRot(level.getRandom().nextFloat() * 360.0F);
        AttributeInstance eliteScale = necromancer.getAttribute(Attributes.SCALE);
        if (eliteScale != null) {
            eliteScale.setBaseValue(ELITE_SCALE);
        }
        necromancer.finalizeSpawn(level, level.getCurrentDifficultyAt(pos), EntitySpawnReason.MOB_SUMMONED, null);

        MobNames.apply(necromancer, "Bonecaller Necromancer", MobNames.Tier.BONE);

        double hp = BASE_HEALTH * statMultiplier;
        AttributeInstance maxHealth = necromancer.getAttribute(Attributes.MAX_HEALTH);
        if (maxHealth != null) {
            maxHealth.setBaseValue(hp);
            necromancer.setHealth((float) hp);
        }
        AttributeInstance attackDamage = necromancer.getAttribute(Attributes.ATTACK_DAMAGE);
        if (attackDamage != null) {
            attackDamage.setBaseValue(BASE_ATTACK_DAMAGE * statMultiplier);
        }

        necromancer.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.STICK));
        necromancer.setItemSlot(EquipmentSlot.HEAD, EliteHeads.necromancerHead());
        necromancer.setItemSlot(EquipmentSlot.CHEST, new ItemStack(Items.LEATHER_CHESTPLATE));
        necromancer.setItemSlot(EquipmentSlot.LEGS, new ItemStack(Items.LEATHER_LEGGINGS));
        necromancer.setItemSlot(EquipmentSlot.FEET, new ItemStack(Items.LEATHER_BOOTS));
        for (EquipmentSlot slot : EquipmentSlot.values()) {
            necromancer.setDropChance(slot, 0.0f);
        }

        level.addFreshEntity(necromancer);
        BonecallerMinionRunner.spawnMount(level, necromancer);
        necromancer.mountSpawned = true;
        return necromancer;
    }

    @Override
    public void customServerAiStep(ServerLevel level) {
        super.customServerAiStep(level);
        long now = level.getGameTime();

        // Vanilla hostile-mob AI has no notion of "these are my summons" - a stray arrow from a
        // Stitched Archer (or any incidental bump) can provoke this boss into retaliating against
        // its own minion. Confirmed live (necromancer killed 2 of its own Stitched Archers in a
        // 140s test) - not a SlopPack fidelity concern, just vanilla AI needing an explicit guard.
        if (this.getTarget() != null && BonecallerMinionRunner.isFriendly(this.getUUID(), this.getTarget())) {
            this.setTarget(null);
        }

        if (breathActive) {
            tickSewageBreath(level, now);
        }

        if (state != AbilityState.IDLE) {
            if (now >= telegraphEndsAtTick) {
                resolveTelegraph(level, now);
            }
            return;
        }

        int activeMinions = BonecallerMinionRunner.countActiveMinions(this.getUUID());
        if (activeMinions < MAX_ACTIVE_MINIONS && now >= nextSummonAtTick) {
            beginTelegraph(level, AbilityState.TELEGRAPH_SUMMON, SUMMON_TELEGRAPH_TICKS);
            return;
        }

        LivingEntity target = this.getTarget();
        if (target != null && target.isAlive() && now >= nextCurseAtTick
                && this.distanceTo(target) <= CURSE_TRIGGER_RANGE) {
            curseTarget = target;
            beginTelegraph(level, AbilityState.TELEGRAPH_CURSE, CURSE_TELEGRAPH_TICKS);
            return;
        }

        if (target != null && target.isAlive() && now >= nextSewageAtTick
                && this.distanceTo(target) <= SEWAGE_TRIGGER_RANGE) {
            beginTelegraph(level, AbilityState.TELEGRAPH_SEWAGE, SEWAGE_CAST_LOCK_TICKS);
        }
    }

    private void beginTelegraph(ServerLevel level, AbilityState next, int ticks) {
        state = next;
        telegraphEndsAtTick = level.getGameTime() + ticks;
        switch (next) {
            case TELEGRAPH_SUMMON -> {
                level.playSound(null, this.getX(), this.getY(), this.getZ(), SoundEvents.WITCH_CELEBRATE, SoundSource.HOSTILE, 1.0f, 0.5f);
                level.playSound(null, this.getX(), this.getY(), this.getZ(), SoundEvents.VEX_CHARGE, SoundSource.HOSTILE, 0.8f, 0.5f);
            }
            case TELEGRAPH_CURSE -> {
                level.playSound(null, this.getX(), this.getY(), this.getZ(), SoundEvents.EVOKER_PREPARE_ATTACK, SoundSource.HOSTILE, 1.0f, 0.7f);
                if (curseTarget != null) {
                    level.playSound(null, curseTarget.getX(), curseTarget.getY(), curseTarget.getZ(), SoundEvents.RESPAWN_ANCHOR_CHARGE, SoundSource.HOSTILE, 0.7f, 0.9f);
                }
            }
            case TELEGRAPH_SEWAGE -> {
                Vec3 eye = this.getEyePosition();
                level.playSound(null, eye.x, eye.y, eye.z, SoundEvents.ZOMBIE_VILLAGER_CURE, SoundSource.HOSTILE, 1.0f, 0.5f);
                level.playSound(null, eye.x, eye.y, eye.z, SoundEvents.HUSK_AMBIENT, SoundSource.HOSTILE, 1.2f, 0.4f);
                level.sendParticles(ParticleTypes.SNEEZE, eye.x, eye.y, eye.z, 20, 0.4, 0.3, 0.4, 0.05);
            }
            default -> {}
        }
    }

    private void tickAmbientTelegraph(ServerLevel level) {
        Vec3 loc = this.position();
        switch (state) {
            case TELEGRAPH_SUMMON -> {
                long ticksLeft = telegraphEndsAtTick - level.getGameTime();
                double angle = ticksLeft * 1.0471975511965976;
                double dx = Math.cos(angle) * 0.8;
                double dz = Math.sin(angle) * 0.8;
                level.sendParticles(ParticleTypes.WITCH, loc.x + dx, loc.y + 1.0, loc.z + dz, 5, 0.1, 0.1, 0.1, 0.01);
                level.sendParticles(ParticleTypes.SOUL_FIRE_FLAME, loc.x - dx, loc.y + 0.5, loc.z - dz, 3, 0.1, 0.1, 0.1, 0.01);
            }
            case TELEGRAPH_CURSE -> {
                if (curseTarget != null && curseTarget.isAlive()) {
                    // A ring at the victim's feet so they know they are the one being cursed.
                    // This runs every tick, so the heavier ring is gated to every 4th.
                    if (level.getGameTime() % 4 == 0) {
                        com.solme.emberfall.combat.Fx.telegraphTarget(level, curseTarget, 0x8A2BE2);
                    }
                    Vec3 from = this.getEyePosition();
                    Vec3 to = curseTarget.position().add(0.0, 1.0, 0.0);
                    Vec3 dir = to.subtract(from);
                    double dist = Math.max(0.5, dir.length());
                    dir = dir.normalize();
                    for (double d = 0.0; d < dist; d += 1.0) {
                        Vec3 p = from.add(dir.scale(d));
                        level.sendParticles(ParticleTypes.SQUID_INK, p.x, p.y, p.z, 1, 0.05, 0.05, 0.05, 0.0);
                    }
                }
            }
            case TELEGRAPH_SEWAGE -> {
                if (level.getGameTime() % 4 == 0) {
                    com.solme.emberfall.combat.Fx.telegraphCone(level, this.position(), this.getLookAngle(),
                            SEWAGE_CONE_RANGE, SEWAGE_CONE_HALF_ANGLE_DEG, 0x6B8E23);
                }
            }
            default -> {}
        }
    }

    private void resolveTelegraph(ServerLevel level, long now) {
        AbilityState resolved = state;
        state = AbilityState.IDLE;
        switch (resolved) {
            case TELEGRAPH_SUMMON -> {
                nextSummonAtTick = now + (long) (SUMMON_COOLDOWN_TICKS * statMultiplier);
                executeSummon(level);
            }
            case TELEGRAPH_CURSE -> {
                nextCurseAtTick = now + (long) (CURSE_COOLDOWN_TICKS * statMultiplier);
                executeCurse(level);
            }
            case TELEGRAPH_SEWAGE -> {
                nextSewageAtTick = now + (long) (SEWAGE_COOLDOWN_TICKS * statMultiplier);
                executeSewageBreath(level, now);
            }
            default -> {}
        }
    }

    @Override
    public void tick() {
        super.tick();
        if (state != AbilityState.IDLE && this.level() instanceof ServerLevel level) {
            tickAmbientTelegraph(level);
        }
    }

    private void executeSummon(ServerLevel level) {
        boolean spawnedHealer = false;
        if (!BonecallerMinionRunner.hasHealerMinion(this.getUUID()) && this.random.nextDouble() < 0.3) {
            spawnedHealer = BonecallerMinionRunner.spawnHealerMinion(level, this);
        }
        int existing = BonecallerMinionRunner.countActiveMinions(this.getUUID());
        int desired = existing <= 1 ? 1 + this.random.nextInt(2) : 1;
        if (spawnedHealer) {
            desired = Math.max(0, desired - 1);
        }
        for (int i = 0; i < desired; i++) {
            BonecallerMinionRunner.spawnCombatMinion(level, this);
        }
    }

    private void executeCurse(ServerLevel level) {
        LivingEntity target = curseTarget;
        curseTarget = null;
        if (target == null || !target.isAlive() || this.distanceTo(target) > CURSE_RESOLVE_MAX_RANGE) {
            return;
        }
        target.hurtServer(level, this.damageSources().mobAttack(this), (float) CURSE_DAMAGE);
        target.addEffect(new MobEffectInstance(MobEffects.WITHER, 60, 1));
        target.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, 100, 0));

        AttributeInstance maxHealth = this.getAttribute(Attributes.MAX_HEALTH);
        double maxHp = maxHealth != null ? maxHealth.getValue() : this.getMaxHealth();
        this.setHealth((float) Math.min(maxHp, this.getHealth() + CURSE_SELF_HEAL));

        Vec3 loc = target.position().add(0.0, 1.0, 0.0);
        level.sendParticles(ParticleTypes.WITCH, loc.x, loc.y, loc.z, 20, 0.3, 0.4, 0.3, 0.02);
        level.playSound(null, loc.x, loc.y, loc.z, SoundEvents.WITCH_DRINK, SoundSource.HOSTILE, 1.0f, 0.6f);
        if (target instanceof Player player) {
            player.displayClientMessage(Component.literal("The Bonecaller Necromancer withers your soul!").withStyle(net.minecraft.ChatFormatting.DARK_PURPLE), false);
        }
    }

    private void executeSewageBreath(ServerLevel level, long now) {
        breathActive = true;
        breathEndsAtTick = now + SEWAGE_BREATH_DURATION_TICKS;
        breathTickCounter = 0;
    }

    private void tickSewageBreath(ServerLevel level, long now) {
        if (now >= breathEndsAtTick) {
            breathActive = false;
            return;
        }
        breathTickCounter++;
        Vec3 eye = this.getEyePosition();
        Vec3 dir = this.getLookAngle();
        // A rasping pulse every 10 ticks (6 per 3s breath) so the active phase is audible, not just the
        // wind-up. Deliberately not every tick: that would stack into a wall of sound.
        if (breathTickCounter % 10 == 1) {
            level.playSound(null, eye.x, eye.y, eye.z, SoundEvents.HUSK_AMBIENT, SoundSource.HOSTILE,
                    0.8F, 0.5F + this.getRandom().nextFloat() * 0.15F);
        }
        level.sendParticles(ParticleTypes.SNEEZE, eye.x + dir.x * 1.2, eye.y + dir.y * 1.2, eye.z + dir.z * 1.2, 6, 0.3, 0.2, 0.3, 0.02);
        level.sendParticles(ParticleTypes.ASH, eye.x + dir.x * 1.6, eye.y + dir.y * 1.6, eye.z + dir.z * 1.6, 4, 0.4, 0.3, 0.4, 0.01);
        level.sendParticles(ParticleTypes.WHITE_ASH, eye.x + dir.x * 2.0, eye.y + dir.y * 2.0, eye.z + dir.z * 2.0, 3, 0.4, 0.3, 0.4, 0.01);

        for (Player player : level.getEntitiesOfClass(Player.class,
                this.getBoundingBox().inflate(SEWAGE_CONE_RANGE, 4.0, SEWAGE_CONE_RANGE), Player::isAlive)) {
            Vec3 toPlayer = player.getEyePosition().subtract(eye);
            double dist = toPlayer.length();
            if (dist > SEWAGE_CONE_RANGE || dist < 0.01) {
                continue;
            }
            Vec3 norm = toPlayer.normalize();
            if (dir.dot(norm) < SEWAGE_CONE_DOT_THRESHOLD) {
                continue;
            }
            player.addEffect(new MobEffectInstance(MobEffects.NAUSEA, 100, 0));
            player.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, 60, 1));
            if (breathTickCounter % 12 == 0) {
                level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.PLAYER_HURT_DROWN, SoundSource.HOSTILE, 1.0f, 0.6f);
                player.setDeltaMovement(player.getDeltaMovement().add(
                        net.minecraft.util.Mth.nextDouble(this.random, -0.15, 0.15), 0.02,
                        net.minecraft.util.Mth.nextDouble(this.random, -0.15, 0.15)));
            }
        }
    }

    @Override
    public boolean canAttack(LivingEntity target) {
        // Proactively excludes minions from targeting-goal candidate lists (not just a reactive
        // clear in customServerAiStep) - closes the race where a fast melee swing could land in
        // the same tick a stray Stitched Archer arrow provokes this boss against its own summon.
        if (BonecallerMinionRunner.isFriendly(this.getUUID(), target)) {
            return false;
        }
        return super.canAttack(target);
    }

    @Override
    public void die(DamageSource source) {
        if (this.level() instanceof ServerLevel level) {
            BonecallerMinionRunner.onOwnerDeath(level, this);
        }
        super.die(source);
    }

    @Override
    public void remove(RemovalReason reason) {
        if (mountSpawned && reason != RemovalReason.KILLED && this.level() instanceof ServerLevel level) {
            BonecallerMinionRunner.onOwnerRemoved(level, this);
        }
        super.remove(reason);
    }
}
