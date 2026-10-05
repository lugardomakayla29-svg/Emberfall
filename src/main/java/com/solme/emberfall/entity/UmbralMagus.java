package com.solme.emberfall.entity;

import com.solme.emberfall.EmberfallMod;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Witch;
import net.minecraft.world.entity.monster.skeleton.WitherSkeleton;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.List;

/**
 * Ported and refined from SlopPack's UmbralMagus (a Witch-based Corrupted
 * archetype: shadow caster/summoner with a homing Arcane Orb volley, a
 * close-range Defensive Burst, two tiers of temporary undead summons, and
 * a signature Cauldron ritual: a pot of pink liquid rises, boils over, and a
 * {@link PinkSlime} climbs out and grows to 2.6 blocks).
 *
 * KEPT (verified against the SlopPack source before porting, per the
 * user's "recheck for bugs first" instruction):
 * - The exact ability priority chain: Cauldron (highest priority, 30s
 *   cooldown) checked first; if not ready, falls through to Defensive
 *   Burst (only within 4 blocks, 14s), then Undead Skirmishers (18s),
 *   then the Umbral Colossus set-piece summon (28s), with the Arcane
 *   Orb volley as the guaranteed 6s-cooldown fallback.
 * - Arcane Orb volley (2-3 homing bolts), Defensive Burst's knockback
 *   nova + self Resistance, both summon tiers with their stitched-slime
 *   cosmetic decoration and timed self-destruct, and the full Cauldron
 *   ritual phase timing (the liquid rises over ~1 s, a knockback/damage
 *   burst at tick 20, the liquid swells and boils over, then the Pink
 *   Slime spawns at tick 45). CHANGED from the port: the water cauldron
 *   blocks became one pink block display, and the stitched Frankenstein
 *   Slime became the growing PinkSlime.
 *
 * FIXED (real bug, found by inspection, not guessed): SlopPack's
 * findNearestPlayer computed a candidate's distance into one local and
 * then compared a DIFFERENT, never-assigned local against the running
 * best distance - decompiled Java that could not have compiled as
 * written, meaning the class file's real bytecode does the sane thing
 * (compare the just-computed distance) and CFR simply mis-rendered the
 * decompiled source; confirmed by checking CinderbrandReaver's own
 * findNearestPlayer in the same jar, which decompiles cleanly to that
 * exact "compare the freshly computed distance" logic. Not applicable
 * here anyway - this port uses the same vanilla getTarget() targeting
 * already established by Blightfeather Marksman and Cinderbrand Reaver
 * instead of re-implementing a nearest-player search.
 *
 * DEVIATION (deliberate, not a fidelity loss): SlopPack equipped a
 * "Prism Core" item + a full mage armor set as mainhand/armor. Witch's
 * own vanilla aiStep() (not the performRangedAttack hook this class
 * overrides) independently drives its own potion-drinking self-buffs by
 * writing directly into the mainhand slot and clearing it afterwards -
 * fighting that private, unconditional vanilla behavior to keep a
 * cosmetic held item equipped isn't worth it for a decorative prop with
 * no gameplay effect. The vanilla self-buff drinking (healing/swiftness/
 * fire resistance/water breathing under the usual vanilla conditions)
 * is left intact - it's harmless, and thematically it reads fine on a
 * caster boss. Visual identity instead comes from the Dark Mage head
 * (see {@link EliteHeads}), custom name, and this kit's own VFX/SFX.
 */
public class UmbralMagus extends Witch {
    private static final double BASE_HEALTH = 80.0;
    /** Larger than fodder, smaller than the Sentinel (3.0). A humanoid above 1.0 no longer fits a 2 high door; the arena is open ground. */
    private static final float ELITE_SCALE = 1.25F;
    private static final double BASE_ATTACK_DAMAGE = 4.0;
    private static final double DEFENSIVE_TRIGGER_RANGE_SQ = 16.0; // 4 blocks

    private static final int BEAM_COOLDOWN_TICKS = 120;         // 6s
    private static final int DEFENSIVE_COOLDOWN_TICKS = 280;    // 14s
    private static final int SKIRMISHERS_COOLDOWN_TICKS = 360;  // 18s
    private static final int BIG_SETPIECE_COOLDOWN_TICKS = 560; // 28s
    private static final int CAULDRON_COOLDOWN_TICKS = 600;     // 30s
    private static final int STAR_LOB_COOLDOWN_TICKS = 200;     // 10s
    private static final int STAR_LOB_TELEGRAPH_TICKS = 16;
    /** Sideways gap between the three landing spots, so standing still is punished but there is room to step out. */
    private static final double STAR_SPREAD = 3.5;

    private static final double ORB_DAMAGE = 4.5;
    private static final double ORB_HOMING_STRENGTH = 0.18;
    private static final double ORB_SPEED = 0.5;
    private static final int ORB_MAX_TICKS = 40;
    /** Gate for the SlopPack "awakened tier" bonus slow+fatigue - this mod has no discrete tier system, so a high statMultiplier stands in for it. */
    private static final double AWAKENED_STAT_THRESHOLD = 1.3;

    private double statMultiplier = 1.0;

    /** Half-extents of the two square blasts (AABB.ofSize is edge length, so half of it). Warning and damage share these. */
    private static final double DEFENSIVE_HALF_EXTENT = 4.0;
    private static final double CAULDRON_HALF_EXTENT = 5.0;
    private static final int DEFENSIVE_TELEGRAPH_TICKS = 10;
    /** The cauldron blast lands on this tick of the ritual. */
    private static final int CAULDRON_BLAST_TICK = 20;

    private enum AbilityState { IDLE, TELEGRAPH_BEAM, TELEGRAPH_DEFENSIVE, TELEGRAPH_SKIRMISHERS, TELEGRAPH_BIGSETPIECE, TELEGRAPH_STARLOB, CAULDRON }

    private AbilityState state = AbilityState.IDLE;
    private long telegraphEndsAtTick = 0L;

    private long nextBeamAtTick = 0L;
    private long nextDefensiveAtTick = 0L;
    private long nextSkirmishersAtTick = 0L;
    private long nextBigSetpieceAtTick = 0L;
    private long nextCauldronAtTick = 0L;
    private long nextStarLobAtTick = 0L;

    // Star Bit Lob: the landing spots are fixed when the wind-up starts, so the warning never lies.
    private final java.util.List<Vec3> starLandings = new java.util.ArrayList<>();
    private final java.util.List<StarBitLob.Colour> starColours = new java.util.ArrayList<>();

    // Staggered Arcane Orb volley (4 ticks apart, matching SlopPack's fireArcaneOrbVolley).
    private int pendingOrbs = 0;
    private long nextOrbAtTick = 0L;
    private LivingEntity orbTarget;
    private int orbIndex = 0;

    // Cauldron ritual state.
    private BlockPos cauldronPos;
    private BlockState originalCauldronBlock;
    private int cauldronTicks = 0;
    private LivingEntity cauldronTarget;

    public UmbralMagus(EntityType<? extends Witch> type, Level level) {
        super(type, level);
    }

    @Override
    public void performRangedAttack(LivingEntity target, float distanceFactor) {
        // Suppressed: this class's ability layer replaces the vanilla potion-throw entirely.
    }

    public static UmbralMagus spawn(ServerLevel level, BlockPos pos, double statMultiplier) {
        UmbralMagus magus = new UmbralMagus(ModEntities.UMBRAL_MAGUS, level);
        magus.statMultiplier = statMultiplier;
        magus.setPos(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5);
        magus.setYRot(level.getRandom().nextFloat() * 360.0F);
        AttributeInstance eliteScale = magus.getAttribute(Attributes.SCALE);
        if (eliteScale != null) {
            eliteScale.setBaseValue(ELITE_SCALE);
        }

        AttributeInstance health = magus.getAttribute(Attributes.MAX_HEALTH);
        if (health != null) {
            double hp = BASE_HEALTH * statMultiplier;
            health.setBaseValue(hp);
            magus.setHealth((float) hp);
        }
        AttributeInstance dmg = magus.getAttribute(Attributes.ATTACK_DAMAGE);
        if (dmg != null) {
            dmg.setBaseValue(BASE_ATTACK_DAMAGE * statMultiplier);
        }

        MobNames.apply(magus, "Umbral Magus", MobNames.Tier.ARCANE);
        magus.setPersistenceRequired();

        magus.setItemSlot(EquipmentSlot.HEAD, EliteHeads.darkMageHead());
        magus.setDropChance(EquipmentSlot.HEAD, 0.0F);

        level.addFreshEntity(magus);
        level.sendParticles(ParticleTypes.SOUL_FIRE_FLAME, magus.getX(), magus.getY() + 1.0, magus.getZ(),
                20, 0.4, 0.6, 0.4, 0.02);
        level.playSound(null, magus.getX(), magus.getY(), magus.getZ(),
                SoundEvents.WITCH_AMBIENT, SoundSource.HOSTILE, 1.0F, 0.6F);
        return magus;
    }

    @Override
    protected void customServerAiStep(ServerLevel level) {
        super.customServerAiStep(level);
        if (!this.isAlive()) {
            return;
        }
        long now = level.getGameTime();

        if (pendingOrbs > 0 && now >= nextOrbAtTick && orbTarget != null && orbTarget.isAlive()) {
            launchOrb(level, orbTarget, orbIndex++);
            pendingOrbs--;
            nextOrbAtTick = now + 4L;
        }

        if (state == AbilityState.CAULDRON) {
            tickCauldron(level);
            return;
        }
        if (state != AbilityState.IDLE) {
            if (now >= telegraphEndsAtTick) {
                resolveTelegraph(level);
            } else if (now % 4 == 0) {
                drawTelegraph(level, now);
            }
            return; // frozen mid-telegraph either way
        }

        LivingEntity target = this.getTarget();
        if (target == null || !target.isAlive()) {
            return;
        }

        if (now >= nextCauldronAtTick) {
            nextCauldronAtTick = now + CAULDRON_COOLDOWN_TICKS;
            beginCauldron(level, target);
            return;
        }
        double distSq = this.distanceToSqr(target);
        if (distSq < DEFENSIVE_TRIGGER_RANGE_SQ && now >= nextDefensiveAtTick) {
            nextDefensiveAtTick = now + DEFENSIVE_COOLDOWN_TICKS;
            beginTelegraph(level, AbilityState.TELEGRAPH_DEFENSIVE, DEFENSIVE_TELEGRAPH_TICKS);
            return;
        }
        if (now >= nextStarLobAtTick && distSq > DEFENSIVE_TRIGGER_RANGE_SQ) {
            nextStarLobAtTick = now + STAR_LOB_COOLDOWN_TICKS;
            beginTelegraph(level, AbilityState.TELEGRAPH_STARLOB, STAR_LOB_TELEGRAPH_TICKS);
            return;
        }
        if (now >= nextSkirmishersAtTick) {
            nextSkirmishersAtTick = now + SKIRMISHERS_COOLDOWN_TICKS;
            beginTelegraph(level, AbilityState.TELEGRAPH_SKIRMISHERS, 10);
            return;
        }
        if (now >= nextBigSetpieceAtTick) {
            nextBigSetpieceAtTick = now + BIG_SETPIECE_COOLDOWN_TICKS;
            beginTelegraph(level, AbilityState.TELEGRAPH_BIGSETPIECE, 12);
            return;
        }
        if (now >= nextBeamAtTick) {
            nextBeamAtTick = now + BEAM_COOLDOWN_TICKS;
            beginTelegraph(level, AbilityState.TELEGRAPH_BEAM, 10);
        }
    }

    /**
     * Shows the real danger while the Magus winds up. The Defensive Burst is a square blast around the
     * Magus (an AABB, so a square outline, not a ring) that fills toward impact. The orb volley is homing,
     * so there is no lane to dodge; it marks the target instead.
     */
    private void drawTelegraph(ServerLevel level, long now) {
        switch (state) {
            case TELEGRAPH_DEFENSIVE -> {
                double progress = 1.0 - Math.max(0L, telegraphEndsAtTick - now) / (double) DEFENSIVE_TELEGRAPH_TICKS;
                com.solme.emberfall.combat.Fx.telegraphSquareFill(level, this.position(), DEFENSIVE_HALF_EXTENT,
                        progress, com.solme.emberfall.combat.Fx.WARN_ORANGE);
            }
            case TELEGRAPH_BEAM -> {
                LivingEntity target = this.getTarget();
                if (target != null && target.isAlive()) {
                    com.solme.emberfall.combat.Fx.telegraphTarget(level, target, com.solme.emberfall.combat.Fx.WARN_ORANGE);
                }
            }
            case TELEGRAPH_STARLOB -> {
                for (int i = 0; i < starLandings.size(); i++) {
                    Vec3 at = starLandings.get(i);
                    com.solme.emberfall.combat.Fx.telegraphRing(level, at, StarBitLob.LANDING_WARN_RADIUS, starColours.get(i).rgbForWarning());
                }
            }
            default -> {}
        }
    }

    private void beginTelegraph(ServerLevel level, AbilityState next, int durationTicks) {
        state = next;
        telegraphEndsAtTick = level.getGameTime() + durationTicks;
        Vec3 loc = this.position().add(0.0, 1.6, 0.0);
        switch (next) {
            case TELEGRAPH_BEAM -> {
                level.sendParticles(ParticleTypes.WITCH, loc.x, loc.y, loc.z, 15, 0.3, 0.3, 0.3, 0.02);
                level.playSound(null, loc.x, loc.y, loc.z, SoundEvents.EVOKER_PREPARE_ATTACK, SoundSource.HOSTILE, 1.0F, 0.8F);
            }
            case TELEGRAPH_DEFENSIVE -> {
                level.sendParticles(ParticleTypes.WITCH, this.getX(), this.getY() + 1.0, this.getZ(),
                        15, 0.5, 0.5, 0.5, 0.05);
                level.playSound(null, this.getX(), this.getY(), this.getZ(),
                        SoundEvents.BREWING_STAND_BREW, SoundSource.HOSTILE, 1.0F, 0.5F);
            }
            case TELEGRAPH_SKIRMISHERS -> {
                level.sendParticles(ParticleTypes.SOUL, loc.x, loc.y - 0.1, loc.z, 20, 0.4, 0.4, 0.4, 0.05);
                level.playSound(null, loc.x, loc.y, loc.z, SoundEvents.VEX_CHARGE, SoundSource.HOSTILE, 1.0F, 0.5F);
            }
            case TELEGRAPH_STARLOB -> {
                chooseStarLandings(level);
                level.sendParticles(ParticleTypes.END_ROD, loc.x, loc.y + 0.4, loc.z, 18, 0.35, 0.35, 0.35, 0.04);
                level.playSound(null, loc.x, loc.y, loc.z, SoundEvents.ALLAY_AMBIENT_WITH_ITEM, SoundSource.HOSTILE, 1.0F, 1.2F);
            }
            case TELEGRAPH_BIGSETPIECE -> {
                level.sendParticles(ParticleTypes.REVERSE_PORTAL, this.getX(), this.getY() + 1.8, this.getZ(),
                        30, 0.5, 0.5, 0.5, 0.02);
                level.playSound(null, this.getX(), this.getY(), this.getZ(),
                        SoundEvents.WITHER_SPAWN, SoundSource.HOSTILE, 0.8F, 0.6F);
            }
            default -> {}
        }
    }

    private void resolveTelegraph(ServerLevel level) {
        AbilityState resolving = state;
        state = AbilityState.IDLE;
        LivingEntity target = this.getTarget();
        switch (resolving) {
            case TELEGRAPH_BEAM -> {
                if (target != null) {
                    EmberfallMod.LOGGER.info("Umbral Magus[id={}] fired Arcane Orb volley", this.getId());
                    fireArcaneOrbVolley(level, target);
                }
            }
            case TELEGRAPH_DEFENSIVE -> {
                EmberfallMod.LOGGER.info("Umbral Magus[id={}] used Defensive Burst", this.getId());
                triggerDefensiveBurst(level);
            }
            case TELEGRAPH_SKIRMISHERS -> {
                if (target != null) {
                    EmberfallMod.LOGGER.info("Umbral Magus[id={}] summoned Undead Skirmishers", this.getId());
                    triggerUndeadSkirmishers(level, target);
                }
            }
            case TELEGRAPH_STARLOB -> {
                EmberfallMod.LOGGER.info("Umbral Magus[id={}] lobbed Star Bits", this.getId());
                throwStarBits(level);
            }
            case TELEGRAPH_BIGSETPIECE -> {
                if (target != null) {
                    EmberfallMod.LOGGER.info("Umbral Magus[id={}] summoned Umbral Colossus", this.getId());
                    triggerBigSetpieceSummon(level, target);
                }
            }
            default -> {}
        }
    }

    // ---- Arcane Orb volley ----

    private void fireArcaneOrbVolley(ServerLevel level, LivingEntity target) {
        int count = 2 + (this.getRandom().nextBoolean() ? 1 : 0);
        level.playSound(null, this.getX(), this.getY(), this.getZ(),
                SoundEvents.ILLUSIONER_CAST_SPELL, SoundSource.HOSTILE, 1.0F, 0.6F);
        pendingOrbs = count;
        orbTarget = target;
        orbIndex = 0;
        nextOrbAtTick = level.getGameTime();
    }

    private void launchOrb(ServerLevel level, LivingEntity target, int index) {
        Vec3 origin = this.getEyePosition();
        Vec3 toTarget = target.getEyePosition().subtract(origin);
        Vec3 velocity = toTarget.lengthSqr() > 0.001
                ? toTarget.normalize().scale(ORB_SPEED)
                : this.getLookAngle().normalize().scale(ORB_SPEED);

        boolean awakened = statMultiplier >= AWAKENED_STAT_THRESHOLD;
        int particleColor = index % 2 == 0 ? 0xFFFFFF : 0xE0C020;
        level.sendParticles(ParticleTypes.SOUL, origin.x, origin.y, origin.z, 6, 0.1, 0.1, 0.1, 0.0);

        TrackedProjectiles.launchHoming(level, origin, velocity, this, target, ORB_HOMING_STRENGTH,
                0.6, ORB_MAX_TICKS, new DustParticleOptions(particleColor, 0.9F),
                hitPos -> {
                    DamageSource source = this.damageSources().mobAttack(this);
                    target.hurtServer(level, source, (float) (ORB_DAMAGE * statMultiplier));
                    if (awakened) {
                        target.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, 60, 1));
                        target.addEffect(new MobEffectInstance(MobEffects.MINING_FATIGUE, 60, 1));
                    }
                    shatterOrb(level, hitPos);
                },
                () -> {});
    }

    private void shatterOrb(ServerLevel level, Vec3 at) {
        level.sendParticles(ParticleTypes.SOUL, at.x, at.y, at.z, 14, 0.25, 0.25, 0.25, 0.02);
        level.sendParticles(ParticleTypes.SMOKE, at.x, at.y, at.z, 10, 0.2, 0.2, 0.2, 0.02);
        level.playSound(null, at.x, at.y, at.z, SoundEvents.ILLUSIONER_MIRROR_MOVE, SoundSource.HOSTILE, 0.8F, 0.7F);
    }

    // ---- Star Bit Lob (adapted from SlopPack's Star Bit / Amethyst Steve) ----

    /**
     * Picks three landing spots: one on the target and one to each side of it, on the ground. Fixed now so
     * the wind-up warning shows exactly where the stars will come down.
     */
    private void chooseStarLandings(ServerLevel level) {
        starLandings.clear();
        starColours.clear();
        LivingEntity target = this.getTarget();
        if (target == null || !target.isAlive()) {
            return;
        }
        Vec3 centre = target.position();
        Vec3 away = centre.subtract(this.position());
        Vec3 dir = new Vec3(away.x, 0.0, away.z);
        dir = dir.lengthSqr() > 0.0001 ? dir.normalize() : this.getLookAngle();
        Vec3 side = new Vec3(-dir.z, 0.0, dir.x);
        StarBitLob.Colour[] all = StarBitLob.Colour.values();
        java.util.List<StarBitLob.Colour> pool = new java.util.ArrayList<>(java.util.List.of(all));
        java.util.Collections.shuffle(pool, new java.util.Random(this.getRandom().nextLong()));
        double[] offsets = {0.0, -STAR_SPREAD, STAR_SPREAD};
        for (int i = 0; i < offsets.length; i++) {
            Vec3 spot = centre.add(side.scale(offsets[i]));
            starLandings.add(groundAt(level, spot));
            starColours.add(pool.get(i));
        }
    }

    /** Drops a point straight down to the top of the nearest solid block below it (up to 8 blocks), else keeps its height. */
    private static Vec3 groundAt(ServerLevel level, Vec3 p) {
        BlockPos pos = BlockPos.containing(p.x, p.y + 1.0, p.z);
        for (int i = 0; i < 10; i++) {
            BlockPos below = pos.below(i);
            if (!level.isLoaded(below)) {
                break;
            }
            if (!level.getBlockState(below).getCollisionShape(level, below).isEmpty()) {
                return new Vec3(p.x, below.getY() + 1.05, p.z);
            }
        }
        return new Vec3(p.x, p.y + 0.05, p.z);
    }

    private void throwStarBits(ServerLevel level) {
        Vec3 from = this.getEyePosition().add(0.0, 0.4, 0.0);
        for (int i = 0; i < starLandings.size(); i++) {
            StarBitLob.launch(level, this, starColours.get(i), statMultiplier, from, starLandings.get(i));
        }
        starLandings.clear();
        starColours.clear();
    }

    // ---- Defensive Burst ----

    private void triggerDefensiveBurst(ServerLevel level) {
        Vec3 loc = this.position();
        level.sendParticles(ParticleTypes.REVERSE_PORTAL, loc.x, loc.y + 1.0, loc.z, 40, 3.0, 1.0, 3.0, 0.05);
        level.playSound(null, loc.x, loc.y, loc.z, SoundEvents.EVOKER_CAST_SPELL, SoundSource.HOSTILE, 1.0F, 0.6F);
        this.addEffect(new MobEffectInstance(MobEffects.RESISTANCE, 40, 2));

        AABB box = AABB.ofSize(loc, DEFENSIVE_HALF_EXTENT * 2, 4.0, DEFENSIVE_HALF_EXTENT * 2);
        DamageSource source = this.damageSources().mobAttack(this);
        List<Player> hit = level.getEntitiesOfClass(Player.class, box, Player::isAlive);
        for (Player player : hit) {
            player.hurtServer(level, source, (float) (3.5 * statMultiplier));
            Vec3 push = player.position().subtract(loc);
            if (push.lengthSqr() > 0.001) {
                Vec3 n = push.normalize().scale(1.2);
                player.setDeltaMovement(n.x, 0.4, n.z);
            }
        }
    }

    // ---- Undead Skirmishers ----

    private void triggerUndeadSkirmishers(ServerLevel level, LivingEntity target) {
        int count = 1 + this.getRandom().nextInt(3);
        Vec3 base = this.position();
        for (int i = 0; i < count; i++) {
            double angle = Math.PI * 2 / count * i;
            Vec3 spawnAt = base.add(Math.cos(angle) * 2.0, 0.0, Math.sin(angle) * 2.0);
            int roll = this.getRandom().nextInt(3);
            LivingEntity minion = switch (roll) {
                case 0 -> EntityType.ZOMBIE.create(level, EntitySpawnReason.MOB_SUMMONED);
                case 1 -> EntityType.SKELETON.create(level, EntitySpawnReason.MOB_SUMMONED);
                default -> EntityType.HUSK.create(level, EntitySpawnReason.MOB_SUMMONED);
            };
            if (minion == null) {
                continue;
            }
            minion.setPos(spawnAt.x, spawnAt.y, spawnAt.z);
            MobNames.apply(minion, "Umbral Thrall", MobNames.Tier.CORRUPTED);
            AttributeInstance minionHealth = minion.getAttribute(Attributes.MAX_HEALTH);
            if (minionHealth != null) {
                minionHealth.setBaseValue(15.0);
                minion.setHealth(15.0F);
            }
            if (minion instanceof Mob mob) {
                mob.setTarget(target);
            }
            level.addFreshEntity(minion);
            level.sendParticles(ParticleTypes.SOUL_FIRE_FLAME, spawnAt.x, spawnAt.y + 0.5, spawnAt.z,
                    10, 0.2, 0.5, 0.2, 0.02);
            UmbralMinionRunner.register(level, this, minion, 240, 1, 0.35);
        }
        level.playSound(null, base.x, base.y, base.z, SoundEvents.ZOMBIE_INFECT, SoundSource.HOSTILE, 1.0F, 0.7F);
    }

    // ---- Umbral Colossus (big set-piece) ----

    private void triggerBigSetpieceSummon(ServerLevel level, LivingEntity target) {
        Vec3 loc = this.position();
        Vec3 spawnAt = loc.add(this.getLookAngle().scale(2.5));
        WitherSkeleton colossus = EntityType.WITHER_SKELETON.create(level, EntitySpawnReason.MOB_SUMMONED);
        if (colossus == null) {
            return;
        }
        colossus.setPos(spawnAt.x, loc.y, spawnAt.z);
        MobNames.apply(colossus, "Umbral Colossus", MobNames.Tier.CORRUPTED);
        AttributeInstance colossusHealth = colossus.getAttribute(Attributes.MAX_HEALTH);
        if (colossusHealth != null) {
            colossusHealth.setBaseValue(40.0);
            colossus.setHealth(40.0F);
        }
        AttributeInstance colossusDmg = colossus.getAttribute(Attributes.ATTACK_DAMAGE);
        if (colossusDmg != null) {
            colossusDmg.setBaseValue(6.0);
        }
        colossus.setTarget(target);
        level.addFreshEntity(colossus);

        level.sendParticles(ParticleTypes.FLAME, loc.x, loc.y + 1.0, loc.z, 25, 0.3, 0.8, 0.3, 0.05);
        level.playSound(null, loc.x, loc.y, loc.z, SoundEvents.WITHER_SKELETON_DEATH, SoundSource.HOSTILE, 1.2F, 0.5F);
        UmbralMinionRunner.register(level, this, colossus, 400, 2, 0.5);
    }

    // ---- Cauldron ritual -> Pink Slime ----

    private void beginCauldron(ServerLevel level, LivingEntity target) {
        state = AbilityState.CAULDRON;
        cauldronTarget = target;
        cauldronTicks = 0;

        Vec3 dir = this.getLookAngle();
        Vec3 aheadPos = this.position().add(dir.x * 1.5, 0.0, dir.z * 1.5);
        cauldronPos = BlockPos.containing(aheadPos.x, this.position().y, aheadPos.z);
        // In the player's real world this spot can hold anything (a chest, a sign, a crop). Only ever
        // overwrite empty air / a plain replaceable plant on top of solid ground; otherwise skip the
        // ritual visual entirely rather than destroy player property. The Pink Slime still spawns.
        BlockState here = level.getBlockState(cauldronPos);
        boolean safeSpot = (here.isAir() || here.canBeReplaced())
                && level.getBlockEntity(cauldronPos) == null
                && level.getBlockState(cauldronPos.below()).blocksMotion();
        if (!safeSpot) {
            cauldronPos = null;
            originalCauldronBlock = null;
            EmberfallMod.LOGGER.info("Umbral Magus[id={}] Cauldron ritual: no safe spot, running without the prop", this.getId());
            return;
        }
        originalCauldronBlock = level.getBlockState(cauldronPos);
        com.solme.emberfall.world.RunManager.setBlockJournaled(level, cauldronPos, Blocks.CAULDRON.defaultBlockState());
        EmberfallMod.LOGGER.info("Umbral Magus[id={}] began Cauldron ritual at {}", this.getId(), cauldronPos);
    }

    /** The pink liquid in the pot: ONE block display, animated by interpolation, always removed with the ritual. */
    private net.minecraft.world.entity.Display.BlockDisplay cauldronLiquid;
    private static final float LIQUID_WIDTH = 0.72F;           // the pot's hollow is 12/16 = 0.75 wide
    private static final float LIQUID_FLOOR = 0.25F;           // inner floor is 4/16 above the block's base
    private static final float LIQUID_RIM = 0.75F;             // height from the floor up to the rim (1.0)
    private static final float LIQUID_BULGE = 1.05F;           // overflow above the rim
    private static final int PINK_LIQUID = 0xF173B8;          // matches the rosie slime skin
    private static final BlockState LIQUID_STATE = Blocks.PINK_STAINED_GLASS.defaultBlockState();

    private void tickCauldron(ServerLevel level) {
        if (!this.isAlive()) {
            restoreCauldronBlock(level);
            state = AbilityState.IDLE;
            return;
        }
        cauldronTicks++;
        Vec3 fx = cauldronPos != null
                ? new Vec3(cauldronPos.getX() + 0.5, cauldronPos.getY() + 0.15, cauldronPos.getZ() + 0.5)
                : this.position().add(this.getLookAngle().x * 1.5, 0.15, this.getLookAngle().z * 1.5);

        // The blast lands on tick CAULDRON_BLAST_TICK. Show its square, filling toward impact, every 4 ticks.
        if (cauldronTicks < CAULDRON_BLAST_TICK && cauldronTicks % 4 == 0) {
            com.solme.emberfall.combat.Fx.telegraphSquareFill(level, fx, CAULDRON_HALF_EXTENT,
                    cauldronTicks / (double) CAULDRON_BLAST_TICK, com.solme.emberfall.combat.Fx.WARN_ORANGE);
        }
        if (cauldronTicks == 1 && cauldronPos != null) {
            Vec3 base = new Vec3(cauldronPos.getX() + 0.5, cauldronPos.getY() + LIQUID_FLOOR, cauldronPos.getZ() + 0.5);
            cauldronLiquid = com.solme.emberfall.entity.BlockDisplayUtil.spawnColumn(level, base, LIQUID_STATE,
                    LIQUID_WIDTH, 0.05F, 0);
            com.solme.emberfall.entity.BlockDisplayUtil.resize(level, cauldronLiquid, LIQUID_STATE, LIQUID_WIDTH,
                    LIQUID_RIM, 19);                            // rises slowly to the rim over ~1 s
            level.playSound(null, fx.x, fx.y, fx.z, SoundEvents.BUCKET_FILL, SoundSource.HOSTILE, 1.0F, 0.6F);
        }
        if (cauldronTicks <= 15) {
            // pink bubbles climb while it fills
            level.sendParticles(new DustParticleOptions(PINK_LIQUID, 1.1F), fx.x, fx.y + 0.1 + cauldronTicks * 0.045, fx.z,
                    6, 0.18, 0.05, 0.18, 0.0);
            if (cauldronTicks % 5 == 0) {
                level.playSound(null, fx.x, fx.y, fx.z, SoundEvents.BUBBLE_COLUMN_UPWARDS_AMBIENT, SoundSource.HOSTILE, 0.8F, 1.2F);
            }
        }
        if (cauldronTicks == 16) {
            level.playSound(null, fx.x, fx.y, fx.z, SoundEvents.WITCH_CELEBRATE, SoundSource.HOSTILE, 1.6F, 0.7F);
            level.playSound(null, fx.x, fx.y, fx.z, SoundEvents.WITCH_AMBIENT, SoundSource.HOSTILE, 1.4F, 0.5F);
            level.playSound(null, fx.x, fx.y, fx.z, SoundEvents.WITCH_AMBIENT, SoundSource.HOSTILE, 1.2F, 1.3F);
        }
        if (cauldronTicks == CAULDRON_BLAST_TICK) {
            level.sendParticles(ParticleTypes.EXPLOSION_EMITTER, fx.x, fx.y + 0.3, fx.z, 1, 0.0, 0.0, 0.0, 0.0);
            AABB box = AABB.ofSize(fx, CAULDRON_HALF_EXTENT * 2, 6.0, CAULDRON_HALF_EXTENT * 2);
            DamageSource source = this.damageSources().mobAttack(this);
            List<Player> hit = level.getEntitiesOfClass(Player.class, box, Player::isAlive);
            for (Player player : hit) {
                player.hurtServer(level, source, (float) (4.0 * statMultiplier));
                Vec3 push = player.position().subtract(fx);
                if (push.lengthSqr() > 0.001) {
                    Vec3 n = push.normalize().scale(1.5);
                    player.setDeltaMovement(n.x, 0.5, n.z);
                }
            }
        }
        if (cauldronTicks == 21 && cauldronLiquid != null) {
            com.solme.emberfall.entity.BlockDisplayUtil.resize(level, cauldronLiquid, LIQUID_STATE, LIQUID_WIDTH,
                    LIQUID_BULGE, 24);                          // swells above the rim until the slime appears
        }
        if (cauldronTicks > 20 && cauldronTicks <= 45 && cauldronPos != null) {
            double top = cauldronPos.getY() + LIQUID_FLOOR + (cauldronTicks < 33 ? LIQUID_RIM : LIQUID_BULGE);
            // hot boiling: bubbles pop on the surface and steam rises
            level.sendParticles(new DustParticleOptions(PINK_LIQUID, 1.3F), fx.x, top, fx.z, 8, 0.22, 0.05, 0.22, 0.0);
            level.sendParticles(ParticleTypes.BUBBLE_POP, fx.x, top + 0.05, fx.z, 6, 0.25, 0.05, 0.25, 0.02);
            level.sendParticles(ParticleTypes.CAMPFIRE_COSY_SMOKE, fx.x, top + 0.1, fx.z, 1, 0.2, 0.05, 0.2, 0.02);
            if (cauldronTicks >= 33) {
                // overflow: pink runs down all four outer walls
                for (int side = 0; side < 4; side++) {
                    double ox = (side == 0 ? 0.42 : side == 1 ? -0.42 : 0.0);
                    double oz = (side == 2 ? 0.42 : side == 3 ? -0.42 : 0.0);
                    level.sendParticles(new DustParticleOptions(PINK_LIQUID, 1.0F), fx.x + ox, cauldronPos.getY() + 0.5 + (cauldronTicks % 6) * -0.07,
                            fx.z + oz, 1, 0.04, 0.05, 0.04, 0.0);
                }
            }
            level.sendParticles(ParticleTypes.ITEM_SLIME, fx.x, top, fx.z, 3, 0.2, 0.1, 0.2, 0.02);
            if (cauldronTicks % 8 == 0) {
                level.playSound(null, fx.x, fx.y, fx.z, SoundEvents.SLIME_BLOCK_BREAK, SoundSource.HOSTILE, 1.2F, 0.5F);
            }
        }
        if (cauldronTicks >= 45) {
            level.playSound(null, fx.x, fx.y, fx.z, SoundEvents.SLIME_SQUISH, SoundSource.HOSTILE, 1.6F, 0.5F);
            level.playSound(null, fx.x, fx.y, fx.z, SoundEvents.WITCH_CELEBRATE, SoundSource.HOSTILE, 1.3F, 0.9F);
            LivingEntity target = cauldronTarget != null && cauldronTarget.isAlive() ? cauldronTarget : this.getTarget();
            PinkSlime.spawn(level, fx, target, statMultiplier);
            EmberfallMod.LOGGER.info("Umbral Magus[id={}] Cauldron ritual completed, spawned Pink Slime", this.getId());
            restoreCauldronBlock(level);
            state = AbilityState.IDLE;
        }
    }

    private void restoreCauldronBlock(ServerLevel level) {
        if (cauldronLiquid != null) {
            cauldronLiquid.discard();                          // a display has no journal, so it must be removed here
            cauldronLiquid = null;
        }
        if (cauldronPos != null && originalCauldronBlock != null) {
            com.solme.emberfall.world.RunManager.setBlockJournaled(level, cauldronPos, originalCauldronBlock);
        }
        cauldronPos = null;
        originalCauldronBlock = null;
    }

    @Override
    public void remove(Entity.RemovalReason reason) {
        UmbralMinionRunner.cleanupForOwner(this.getUUID());
        StarBitLob.clearFor(this);
        if (state == AbilityState.CAULDRON && this.level() instanceof ServerLevel level) {
            restoreCauldronBlock(level);
        }
        super.remove(reason);
    }
}
