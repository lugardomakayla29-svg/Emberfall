package com.solme.emberfall.entity;

import com.solme.emberfall.EmberfallMod;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.illager.Vindicator;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.List;

/**
 * Ported and refined from SlopPack's CinderbrandReaver (a Vindicator-based
 * Corrupted archetype: Solar Chainblade melee, rage stacks + finisher,
 * Magma Plume, Solar Cleave, Solar Lunge). Deliberately NOT a straight
 * port - see class javadoc sections below for what's new versus what's
 * kept, per the user's explicit "nothing should be a plain reskin" ask.
 *
 * KEPT (ported, tuned for Emberfall's threatLevel scaling instead of
 * SlopPack's discrete dungeon tier):
 * - Magma Plume: ranged fire-pillar zone denial near the target.
 * - Solar Cleave: telegraphed wide melee AoE swing.
 * - Solar Lunge: telegraphed dash gap-closer with a burn hit.
 *
 * NEW / REFINED (replacing SlopPack's separate "rage stacks + rage
 * finisher" system, which was just a flat damage-boost buff with no
 * player-facing tell beyond a particle puff):
 * - Combo tracking -> "Solarburst Overdrive": landing 3 melee hits within
 *   a rolling window ignites the target and drops a short-lived scorched
 *   ground patch under them - a visible, thematic payoff instead of an
 *   invisible stat buff.
 * - Ember Trail: light ambient fire particles while actively chasing a
 *   target, cheap flavor that sells the "burning berserker" theme between
 *   ability casts.
 * - Last Blaze: a one-time desperate capstone at <=25% HP - a bigger nova
 *   than Solar Cleave that also heals the Reaver a little, giving low-HP
 *   Reavers a real "enrage" moment instead of just dying quietly.
 *
 * Base entity is vanilla Vindicator (real pathfinding + melee AI kept
 * as-is, exactly like {@link HordeZombie} leans on vanilla Zombie AI) -
 * only stats, equipment, and this bolt-on ability layer are custom.
 */
public class CinderbrandReaver extends Vindicator {
    private static final double BASE_HEALTH = 90.0;
    /** Larger than fodder, smaller than the Sentinel (3.0). A humanoid above 1.0 no longer fits a 2 high door; the arena is open ground. */
    private static final float ELITE_SCALE = 1.25F;
    private static final double BASE_ATTACK_DAMAGE = 6.0;

    private static final int COMBO_WINDOW_TICKS = 60;       // 3s to land the next hit in the combo
    private static final int MAGMA_PLUME_COOLDOWN_TICKS = 140;  // 7s
    private static final int SOLAR_CLEAVE_COOLDOWN_TICKS = 200; // 10s
    private static final int SOLAR_LUNGE_COOLDOWN_TICKS = 320;  // 16s
    private static final int CLEAVE_TELEGRAPH_TICKS = 10;    // 0.5s
    private static final int LUNGE_TELEGRAPH_TICKS = 14;     // 0.7s
    private static final double ABILITY_RANGE = 10.0;
    /** Damage radii. The telegraph rings are drawn from these same values. */
    private static final double CLEAVE_RADIUS = 4.5;
    private static final double LAST_BLAZE_RADIUS = 6.0;
    private static final int LAST_BLAZE_TELEGRAPH_TICKS = 16; // 0.8s, a bigger tell for the big hit
    /** The lunge dashes toward the target up to this far; the lane telegraph uses the same reach. */
    private static final double LUNGE_REACH = 8.0;
    private static final double LAST_BLAZE_HP_FRACTION = 0.25;

    private double statMultiplier = 1.0;

    private int comboHitCount = 0;
    private long comboExpiresAtTick = 0L;

    private long nextMagmaPlumeAtTick = 0L;
    private long nextSolarCleaveAtTick = 0L;
    private long nextSolarLungeAtTick = 0L;

    private AbilityState state = AbilityState.IDLE;
    private long telegraphEndsAtTick = 0L;

    private boolean lastBlazeUsed = false;

    private enum AbilityState { IDLE, TELEGRAPH_CLEAVE, TELEGRAPH_LUNGE, TELEGRAPH_LAST_BLAZE }

    public CinderbrandReaver(EntityType<? extends Vindicator> type, Level level) {
        super(type, level);
    }

    /** Spawns and fully outfits a Cinderbrand Reaver at the given position. */
    public static CinderbrandReaver spawn(ServerLevel level, BlockPos pos, double statMultiplier) {
        CinderbrandReaver reaver = new CinderbrandReaver(ModEntities.CINDERBRAND_REAVER, level);
        reaver.statMultiplier = statMultiplier;
        reaver.setPos(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5);
        reaver.setYRot(level.getRandom().nextFloat() * 360.0F);
        AttributeInstance eliteScale = reaver.getAttribute(Attributes.SCALE);
        if (eliteScale != null) {
            eliteScale.setBaseValue(ELITE_SCALE);
        }

        AttributeInstance health = reaver.getAttribute(Attributes.MAX_HEALTH);
        if (health != null) {
            double hp = BASE_HEALTH * statMultiplier;
            health.setBaseValue(hp);
            reaver.setHealth((float) hp);
        }
        AttributeInstance damage = reaver.getAttribute(Attributes.ATTACK_DAMAGE);
        if (damage != null) {
            damage.setBaseValue(BASE_ATTACK_DAMAGE * statMultiplier);
        }

        MobNames.apply(reaver, "Cinderbrand Reaver", MobNames.Tier.LAVA);
        reaver.setPersistenceRequired();

        ItemStack sword = new ItemStack(Items.NETHERITE_SWORD);
        sword.set(net.minecraft.core.component.DataComponents.CUSTOM_NAME,
                Component.literal("Solar Chainblade"));
        sword.set(net.minecraft.core.component.DataComponents.ENCHANTMENT_GLINT_OVERRIDE, true);
        reaver.setItemSlot(EquipmentSlot.MAINHAND, sword);
        reaver.setDropChance(EquipmentSlot.MAINHAND, 0.0F);
        reaver.setItemSlot(EquipmentSlot.HEAD, EliteHeads.magmaBerserkerHead());
        reaver.setDropChance(EquipmentSlot.HEAD, 0.0F);

        level.addFreshEntity(reaver);
        level.sendParticles(ParticleTypes.FLAME, reaver.getX(), reaver.getY() + 1.0, reaver.getZ(),
                25, 0.4, 0.6, 0.4, 0.02);
        level.playSound(null, reaver.getX(), reaver.getY(), reaver.getZ(),
                SoundEvents.BLAZE_AMBIENT, SoundSource.HOSTILE, 1.0F, 0.6F);
        return reaver;
    }

    @Override
    public boolean doHurtTarget(ServerLevel level, Entity target) {
        boolean hurt = super.doHurtTarget(level, target);
        if (hurt) {
            onMeleeLanded(level, target);
        }
        return hurt;
    }

    private void onMeleeLanded(ServerLevel level, Entity target) {
        long now = this.level().getGameTime();
        if (now >= comboExpiresAtTick) {
            comboHitCount = 0;
        }
        comboHitCount++;
        comboExpiresAtTick = now + COMBO_WINDOW_TICKS;

        if (comboHitCount >= 3 && target instanceof LivingEntity livingTarget) {
            comboHitCount = 0;
            triggerSolarburstOverdrive(level, livingTarget);
        }
    }

    private void triggerSolarburstOverdrive(ServerLevel level, LivingEntity target) {
        target.igniteForSeconds(4.0F);
        level.sendParticles(ParticleTypes.FLAME, target.getX(), target.getY() + 1.0, target.getZ(),
                30, 0.3, 0.5, 0.3, 0.05);
        level.playSound(null, target.getX(), target.getY(), target.getZ(),
                SoundEvents.FIRECHARGE_USE, SoundSource.HOSTILE, 1.0F, 0.8F);
        // Scorched ground patch: a short burst of lingering fire particles/damage ticks
        // under the target rather than placing real fire blocks (arena-safe, no cleanup needed).
        ScorchedGround.spawn(level, target.blockPosition(), 60, 1.5);
    }

    @Override
    protected void customServerAiStep(ServerLevel level) {
        super.customServerAiStep(level);
        if (!this.isAlive()) {
            return;
        }
        long now = this.level().getGameTime();

        if (state != AbilityState.IDLE) {
            if (now >= telegraphEndsAtTick) {
                resolveTelegraph(level);
            } else {
                if (now % 4 == 0) {
                    drawTelegraph(level, now);
                }
                return; // frozen mid-telegraph, like SlopPack's TELEGRAPH_UNTIL gate
            }
        }

        LivingEntity target = this.getTarget();
        if (target == null || !target.isAlive()) {
            return;
        }
        double distance = this.position().distanceTo(target.position());

        if (!lastBlazeUsed && this.getHealth() / this.getMaxHealth() <= LAST_BLAZE_HP_FRACTION) {
            lastBlazeUsed = true;
            beginTelegraph(AbilityState.TELEGRAPH_LAST_BLAZE, LAST_BLAZE_TELEGRAPH_TICKS);
            return;
        }

        if (distance <= ABILITY_RANGE) {
            if (now >= nextSolarCleaveAtTick) {
                nextSolarCleaveAtTick = now + SOLAR_CLEAVE_COOLDOWN_TICKS;
                beginTelegraph(AbilityState.TELEGRAPH_CLEAVE, CLEAVE_TELEGRAPH_TICKS);
                return;
            }
            if (now >= nextMagmaPlumeAtTick && distance > 3.0) {
                nextMagmaPlumeAtTick = now + MAGMA_PLUME_COOLDOWN_TICKS;
                spawnMagmaPlume(level, target);
            }
        } else if (distance <= ABILITY_RANGE * 1.6 && now >= nextSolarLungeAtTick) {
            nextSolarLungeAtTick = now + SOLAR_LUNGE_COOLDOWN_TICKS;
            beginTelegraph(AbilityState.TELEGRAPH_LUNGE, LUNGE_TELEGRAPH_TICKS);
            return;
        }

        tickEmberTrail(level);
    }

    /**
     * Shows the real danger area while the Reaver winds up: a filling ring at the true radius for the
     * two area attacks, and a lane from the Reaver toward its target for the lunge.
     */
    private void drawTelegraph(ServerLevel level, long now) {
        int total = switch (state) {
            case TELEGRAPH_CLEAVE -> CLEAVE_TELEGRAPH_TICKS;
            case TELEGRAPH_LUNGE -> LUNGE_TELEGRAPH_TICKS;
            default -> LAST_BLAZE_TELEGRAPH_TICKS;
        };
        double progress = 1.0 - Math.max(0L, telegraphEndsAtTick - now) / (double) total;
        switch (state) {
            case TELEGRAPH_CLEAVE -> com.solme.emberfall.combat.Fx.telegraphFill(
                    level, this.position(), CLEAVE_RADIUS, progress, com.solme.emberfall.combat.Fx.WARN_ORANGE);
            case TELEGRAPH_LAST_BLAZE -> com.solme.emberfall.combat.Fx.telegraphFill(
                    level, this.position(), LAST_BLAZE_RADIUS, progress, com.solme.emberfall.combat.Fx.WARN_RED);
            case TELEGRAPH_LUNGE -> {
                LivingEntity target = this.getTarget();
                if (target != null) {
                    Vec3 to = target.position().subtract(this.position());
                    double len = Math.min(to.length(), LUNGE_REACH);
                    if (len > 0.01) {
                        Vec3 end = this.position().add(to.normalize().scale(len));
                        com.solme.emberfall.combat.Fx.telegraphLine(level, this.position(), end, 1.0,
                                com.solme.emberfall.combat.Fx.WARN_ORANGE);
                    }
                }
            }
            default -> {}
        }
    }

    private void beginTelegraph(AbilityState next, int durationTicks) {
        state = next;
        telegraphEndsAtTick = this.level().getGameTime() + durationTicks;
        this.getNavigation().stop();
        ServerLevel level = (ServerLevel) this.level();
        level.sendParticles(ParticleTypes.LAVA, this.getX(), this.getY() + 1.2, this.getZ(),
                12, 0.3, 0.4, 0.3, 0.02);
        level.playSound(null, this.getX(), this.getY(), this.getZ(),
                SoundEvents.BLAZE_BURN, SoundSource.HOSTILE, 1.0F, 1.2F);
    }

    private void resolveTelegraph(ServerLevel level) {
        AbilityState resolving = state;
        state = AbilityState.IDLE;
        switch (resolving) {
            case TELEGRAPH_CLEAVE -> executeSolarCleave(level);
            case TELEGRAPH_LUNGE -> executeSolarLunge(level);
            case TELEGRAPH_LAST_BLAZE -> executeLastBlaze(level);
            default -> {}
        }
    }

    private void executeSolarCleave(ServerLevel level) {
        double radius = CLEAVE_RADIUS;
        AABB box = this.getBoundingBox().inflate(radius);
        List<Player> hit = level.getEntitiesOfClass(Player.class, box,
                p -> p.isAlive() && !p.isSpectator() && p.distanceTo(this) <= radius);
        DamageSource source = this.damageSources().mobAttack(this);
        for (Player player : hit) {
            player.hurtServer(level, source, (float) (9.0 * statMultiplier));
            player.igniteForSeconds(2.0F);
        }
        level.sendParticles(ParticleTypes.FLAME, this.getX(), this.getY() + 1.0, this.getZ(),
                40, radius * 0.4, 0.5, radius * 0.4, 0.06);
        level.playSound(null, this.getX(), this.getY(), this.getZ(),
                SoundEvents.PLAYER_ATTACK_SWEEP, SoundSource.HOSTILE, 1.0F, 0.7F);
    }

    private void executeSolarLunge(ServerLevel level) {
        LivingEntity target = this.getTarget();
        if (target == null) {
            return;
        }
        Vec3 toTarget = target.position().subtract(this.position());
        double len = toTarget.length();
        if (len > 0.001) {
            Vec3 dash = toTarget.normalize().scale(Math.min(len, LUNGE_REACH)).scale(0.9);
            this.setDeltaMovement(dash.x, 0.25, dash.z);
        }
        level.sendParticles(ParticleTypes.FLAME, this.getX(), this.getY() + 0.5, this.getZ(),
                20, 0.2, 0.2, 0.2, 0.1);
        level.playSound(null, this.getX(), this.getY(), this.getZ(),
                SoundEvents.BLAZE_SHOOT, SoundSource.HOSTILE, 1.0F, 0.9F);

        if (this.position().distanceTo(target.position()) <= 3.0) {
            DamageSource source = this.damageSources().mobAttack(this);
            target.hurtServer(level, source, (float) (10.0 * statMultiplier));
            target.igniteForSeconds(3.0F);
        }
    }

    private void executeLastBlaze(ServerLevel level) {
        double radius = LAST_BLAZE_RADIUS;
        AABB box = this.getBoundingBox().inflate(radius);
        List<Player> hit = level.getEntitiesOfClass(Player.class, box,
                p -> p.isAlive() && !p.isSpectator() && p.distanceTo(this) <= radius);
        DamageSource source = this.damageSources().mobAttack(this);
        for (Player player : hit) {
            player.hurtServer(level, source, (float) (16.0 * statMultiplier));
            player.igniteForSeconds(5.0F);
        }
        this.heal((float) (this.getMaxHealth() * 0.15));
        level.sendParticles(ParticleTypes.EXPLOSION, this.getX(), this.getY() + 1.0, this.getZ(),
                1, 0.0, 0.0, 0.0, 0.0);
        level.sendParticles(ParticleTypes.FLAME, this.getX(), this.getY() + 1.0, this.getZ(),
                80, radius * 0.5, 0.6, radius * 0.5, 0.08);
        level.playSound(null, this.getX(), this.getY(), this.getZ(),
                SoundEvents.GENERIC_EXPLODE, SoundSource.HOSTILE, 1.2F, 0.7F);
        EmberfallMod.LOGGER.info("Cinderbrand Reaver used Last Blaze at {}% HP", (int) (100 * LAST_BLAZE_HP_FRACTION));
    }

    private void spawnMagmaPlume(ServerLevel level, LivingEntity target) {
        BlockPos base = target.blockPosition();
        int pillars = 3;
        for (int i = 0; i < pillars; i++) {
            double angle = level.getRandom().nextDouble() * Math.PI * 2;
            double dist = 1.5 + level.getRandom().nextDouble() * 2.0;
            double x = base.getX() + 0.5 + Math.cos(angle) * dist;
            double z = base.getZ() + 0.5 + Math.sin(angle) * dist;
            level.sendParticles(ParticleTypes.LAVA, x, base.getY() + 0.2, z, 8, 0.15, 0.3, 0.15, 0.02);
            level.sendParticles(ParticleTypes.FLAME, x, base.getY() + 0.5, z, 12, 0.15, 0.6, 0.15, 0.03);
            ScorchedGround.spawn(level, new BlockPos((int) Math.floor(x), base.getY(), (int) Math.floor(z)),
                    50, 1.0);
        }
        level.playSound(null, base.getX(), base.getY(), base.getZ(),
                SoundEvents.LAVA_POP, SoundSource.HOSTILE, 1.0F, 0.8F);
    }

    private int emberTrailCooldown = 0;

    private void tickEmberTrail(ServerLevel level) {
        if (emberTrailCooldown > 0) {
            emberTrailCooldown--;
            return;
        }
        if (!this.onGround() || this.getTarget() == null) {
            return;
        }
        emberTrailCooldown = 6;
        level.sendParticles(ParticleTypes.SMALL_FLAME, this.getX(), this.getY() + 0.1, this.getZ(),
                2, 0.15, 0.05, 0.15, 0.01);
    }
}
