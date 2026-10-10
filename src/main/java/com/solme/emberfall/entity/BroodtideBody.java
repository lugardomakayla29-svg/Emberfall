package com.solme.emberfall.entity;

import com.solme.emberfall.boss.BossTuning;
import com.solme.emberfall.boss.TideClock;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Slime;
import net.minecraft.world.level.Level;

/**
 * The Broodtide's body (docs/PLAN_broodtide.md sections 1, 2 and 5): a real {@link Slime} that does not walk, whose armour follows the Tide.
 * It is the single hittable Mob of the fight (the auto-attack picks the nearest Mob, so v1 keeps exactly one), and it replaces the Ember Guardian's brain.
 *
 * <p>Engine facts this class depends on (from {@link PinkSlime}, which verified them against the 1.21.11 bytecode):
 * {@code Slime.setSize} clamps to 1..127 and OVERWRITES max health, speed and damage before healing to full, so every size change goes through
 * {@link #applySize}, which puts the boss stats back and keeps the health fraction; {@code Slime.remove} splits a dying slime of size above 1 into
 * copies of its own type, which would spawn smaller bosses, so {@link #remove} drops the size to 1 first; a Slime jumps by itself on a timer, so the
 * jump is disabled and horizontal motion is zeroed every tick (a rooted body).</p>
 *
 * <p>Stats come from {@link BossTuning} (owner, 2026-10-09: +35% on every stat), so the boost cannot be forgotten here.
 * The Tide: in Flood the body takes {@link TideClock#FLOOD_ARMOUR} of the damage, never zero (no silent immunity).</p>
 *
 * Tested headless, look unverified: this is the logic body only. The renderer, Grab, Devour and the phases come in later slices.
 */
public class BroodtideBody extends Slime {
    /** Body size in slime units. A vanilla slime box is 0.52 x size wide, so 6 gives a 3.1 block body. PROPOSAL: the plan says "giant". */
    public static final int BODY_SIZE = 6;

    private long fightTick = 0;
    /** The Grab and the phase latch. Created when the fight starts (the slot is known), null for a body loaded without a fight. */
    private com.solme.emberfall.boss.BroodtideGrabber grabber;
    /** The Devour: reaches for a horde mob, hides it, spits it out as a Brood-Kin. Null for a body loaded without a fight. */
    private com.solme.emberfall.boss.BroodtideDevourer devourer;
    /** The kraken's arms: 40 displays allocated once in {@link #startFight}, discarded on every exit path in {@link #remove}. Null for a body loaded without a fight. */
    private BroodtideArms arms;
    private boolean defeated = false;
    private double curseMultiplier = 1.0;
    /** The curse's share of the pool the 1024 attribute ceiling cannot hold (BroodtideRules.overflowFactor); recomputed on every resize, multiplied with the party factor. */
    private float overflowDamageFactor = 1.0F;
    private static final boolean LOG_PARTY_HP = Boolean.getBoolean("emberfall.logPartyHp");
    /** The health multiplier party scaling applied, remembered so a later {@link #applySize} (a phase resize) reproduces it instead of erasing it. */
    private double partyHealthMultiplier = 1.0;

    public BroodtideBody(EntityType<? extends BroodtideBody> type, Level level) {
        super(type, level);
    }

    public static AttributeSupplier.Builder createBossAttributes() {
        return Mob.createMobAttributes()
                .add(Attributes.MAX_HEALTH, BossTuning.broodtideHealth())
                .add(Attributes.MOVEMENT_SPEED, 0.0)               // rooted: it never walks
                .add(Attributes.ATTACK_DAMAGE, BossTuning.broodtideDamage())
                .add(Attributes.FOLLOW_RANGE, 64.0)
                .add(Attributes.KNOCKBACK_RESISTANCE, 1.0);         // a rooted boss is not shoved around
    }

    /** Sets the size and puts the boss stats back. {@code Slime.setSize} overwrites them, so the health FRACTION is captured and restored. */
    public void applySize(int size, double healthFraction) {
        super.setSize(size, false);
        // The pool the curse (and party) WANT; the attribute holds at most 1024, the rest comes back as a damage factor (same idea as PartyHealth.applyBoss).
        double wantedPool = BossTuning.broodtideHealth() * curseMultiplier * partyHealthMultiplier;
        setBase(Attributes.MAX_HEALTH, com.solme.emberfall.boss.BroodtideRules.attributeFor(wantedPool));
        this.overflowDamageFactor = com.solme.emberfall.boss.BroodtideRules.overflowFactor(wantedPool);
        setBase(Attributes.MOVEMENT_SPEED, 0.0);
        setBase(Attributes.ATTACK_DAMAGE, BossTuning.broodtideDamage() * curseMultiplier);
        this.setHealth((float) (this.getMaxHealth() * com.solme.emberfall.boss.BroodtideRules.clampedFraction(healthFraction)));
    }

    private void setBase(net.minecraft.core.Holder<net.minecraft.world.entity.ai.attributes.Attribute> attr, double value) {
        AttributeInstance inst = this.getAttribute(attr);
        if (inst != null) {
            inst.setBaseValue(value);
        }
    }

    /** Boss Curse shrine multiplier, applied once before the boss enters the world. Stacks on the base boost (it multiplies the boosted numbers). */
    public void applyCurse(double statMultiplier) {
        this.curseMultiplier = Math.max(1.0, statMultiplier);
        applySize(BODY_SIZE, 1.0);
    }

    /**
     * Party scaling for {@code partySize} players. The body owns the WHOLE pool (base x curse x party), so the 1024 attribute ceiling is applied once, to the true
     * total, by {@link #applySize}: the part the attribute cannot hold comes back as a damage factor. (The old read-back of the attribute would have seen an
     * already-clamped base and under-counted a cursed boss.) Call after {@link #applyCurse}; a later phase resize keeps the multiplier.
     */
    public void applyParty(int partySize) {
        this.partyHealthMultiplier = Math.max(1.0, com.solme.emberfall.world.PartyScaling.bossHealthMultiplier(partySize));
        applySize(BODY_SIZE, 1.0);
        if (LOG_PARTY_HP) {   // same line and fields PartyHealth.applyBoss logs, so party_boss_grade.sh keeps working
            com.solme.emberfall.EmberfallMod.LOGGER.info("PARTYBOSS type={} effective={} attribute={} damageFactor={}",
                    net.minecraft.core.registries.BuiltInRegistries.ENTITY_TYPE.getKey(this.getType()),
                    String.format("%.1f", BossTuning.broodtideHealth() * curseMultiplier * partyHealthMultiplier),
                    String.format("%.1f", this.getMaxHealth()), String.format("%.4f", overflowDamageFactor));
        }
    }


    /** Called by the fight wrapper right after construction: full size, full health. */
    public void initBoss() {
        applySize(BODY_SIZE, 1.0);
        this.setPersistenceRequired();
        this.setNoGravity(false);
    }

    /** The vanilla contact damage reads getAttackDamage(); setSize does not touch it after our re-apply. */
    @Override
    protected float getAttackDamage() {
        return (float) (BossTuning.broodtideDamage() * curseMultiplier);
    }

    /**
     * The hitbox is the whole two-slime column (owner, 2026-10-10: a lower slime facing away and an upper one facing the player), so a shot at the upper
     * head lands. The vanilla box is {@code 0.52 x size} on every side; this keeps the width and stacks a second slime of height, less the glue overlap the
     * renderer sinks it by ({@link ChainGlue#OVERLAP}). One entity, one hitbox, two drawn slimes.
     */
    @Override
    public net.minecraft.world.entity.EntityDimensions getDefaultDimensions(net.minecraft.world.entity.Pose pose) {
        net.minecraft.world.entity.EntityDimensions one = super.getDefaultDimensions(pose);
        return net.minecraft.world.entity.EntityDimensions.scalable(one.width(), columnHeight(one.height()))
                .withEyeHeight(columnHeight(one.height()) * 0.8F);
    }

    /** Height of the two-slime column given the height of one slime. Pure, so a check can pin it. */
    public static float columnHeight(float oneSlime) {
        return oneSlime * (2.0F - (float) ChainGlue.OVERLAP);
    }

    /** A Slime picks its own jump delay and hops; a rooted body never does. */
    @Override
    protected int getJumpDelay() {
        return com.solme.emberfall.boss.BroodtideRules.ROOTED_JUMP_DELAY;
    }

    @Override
    public boolean isPushable() {
        return false;
    }

    @Override
    protected void customServerAiStep(ServerLevel level) {
        super.customServerAiStep(level);
        fightTick++;
        // Rooted: kill any horizontal drift every tick, keep the vertical so it still settles on the ground.
        net.minecraft.world.phys.Vec3 v = this.getDeltaMovement();
        if (v.x != 0.0 || v.z != 0.0) {
            this.setDeltaMovement(com.solme.emberfall.boss.BroodtideRules.rootedHorizontal(v.x), v.y, com.solme.emberfall.boss.BroodtideRules.rootedHorizontal(v.z));
        }
        this.setTarget(this.getTarget());   // keep vanilla targeting; the boss only needs a target for contact damage
        if (grabber != null) {
            grabber.tick(level, this, fightTick, this.getMaxHealth() > 0 ? this.getHealth() / this.getMaxHealth() : 1.0);
        }
        if (devourer != null && grabber != null) {
            devourer.tick(level, this, fightTick, grabber.phase());
        }
        if (com.solme.emberfall.boss.BroodtideMouth.redrawsAt(fightTick)) {
            drawMouth(level);
        }
        if (arms != null && grabber != null) {
            arms.tick(this.position(), grabber.phase(), TideClock.stateAt(fightTick), TideClock.ticksInto(fightTick));
        }
    }

    /**
     * The particle mouth on the upper slime's face (owner, 2026-10-10). Faces the nearest player, so it always looks at whoever the head is turned to; its
     * shape changes every {@link com.solme.emberfall.boss.BroodtideMouth#HOLD_TICKS} ticks. A fixed {@link com.solme.emberfall.boss.BroodtideMouth#POINTS}
     * particles per redraw, one redraw per {@link com.solme.emberfall.boss.BroodtideMouth#REDRAW_TICKS} ticks. UNSEEN: no one has looked at it on a real client.
     */
    private void drawMouth(ServerLevel level) {
        net.minecraft.world.entity.player.Player nearest = level.getNearestPlayer(this, 64.0);
        if (nearest == null) {
            return;
        }
        double dx = nearest.getX() - this.getX();
        double dz = nearest.getZ() - this.getZ();
        double len = Math.hypot(dx, dz);
        if (len < 1.0E-6) {
            return;
        }
        double fx = dx / len, fz = dz / len;           // unit vector from the body toward the player
        double rx = -fz, rz = fx;                      // the mouth's "right" as seen by the player looking at the body
        double face = this.getBbWidth() * 0.5 + 0.35;   // just in front of the upper slime's face
        double cy = this.getY() + this.getBbHeight() * 0.62;   // the upper slime's mouth height (below its eyes)
        double radius = this.getBbWidth() * 0.16;
        int shape = com.solme.emberfall.boss.BroodtideMouth.shapeAt(fightTick);
        double phase = (fightTick % 40L) / 40.0;
        double[][] pts = com.solme.emberfall.boss.BroodtideMouth.points(shape, phase);
        for (double[] q : pts) {
            double px = this.getX() + fx * face + rx * q[0] * radius;
            double pz = this.getZ() + fz * face + rz * q[0] * radius;
            level.sendParticles(net.minecraft.core.particles.ParticleTypes.ITEM_SLIME, px, cy + q[1] * radius, pz, 1, 0.0, 0.0, 0.0, 0.0);
        }
    }

    /** Binds this body to a run slot and starts its Grab. Called once by the fight wrapper right after the spawn. */
    public void startFight(int slot) {
        this.grabber = new com.solme.emberfall.boss.BroodtideGrabber(slot);
        this.devourer = new com.solme.emberfall.boss.BroodtideDevourer();
        if (this.level() instanceof ServerLevel sl) {
            this.arms = new BroodtideArms(sl, this.position());
        }
    }

    public BroodtideArms arms() {
        return arms;
    }

    public com.solme.emberfall.boss.BroodtideGrabber grabber() {
        return grabber;
    }

    public com.solme.emberfall.boss.BroodtideDevourer devourer() {
        return devourer;
    }

    /** How many ticks the fight has run: the Tide's clock. */
    public long fightTick() {
        return fightTick;
    }

    /** The Tide state right now, from the pure clock. */
    public TideClock.State tide() {
        return TideClock.stateAt(fightTick);
    }

    /**
     * Damage taken is scaled by the Tide: x0.35 in Flood, x1.0 in Ebb, never zero (plan section 2, "never immune"). Done here, at the one place all
     * damage passes through, so every weapon and the party-damage path are covered with no per-weapon code.
     */
    @Override
    public boolean hurtServer(ServerLevel level, DamageSource source, float amount) {
        // /kill and void damage bypass invulnerability: they must still work, so neither the Tide nor the party factor touches them.
        if (source.is(net.minecraft.tags.DamageTypeTags.BYPASSES_INVULNERABILITY)) {
            return super.hurtServer(level, source, amount);
        }
        float scaled = com.solme.emberfall.boss.BroodtideRules.damageTaken(amount, fightTick, overflowDamageFactor);
        return super.hurtServer(level, source, scaled);
    }

    /** True only after a genuine death (the fight wrapper reads this before it marks the boss defeated). */
    public boolean wasDefeated() {
        return defeated;
    }

    @Override
    public void die(DamageSource source) {
        this.defeated = true;
        super.die(source);
    }

    /** Vanilla splits a dying slime of size above 1 into copies of its own type; that would spawn smaller bosses, so the size drops to 1 first. */
    @Override
    public void remove(Entity.RemovalReason reason) {
        if (grabber != null && this.level() instanceof ServerLevel sl) {
            grabber.clearAll(sl);   // no player keeps Slowness / Mining Fatigue from a boss that is gone
        }
        if (devourer != null && this.level() instanceof ServerLevel sl) {
            devourer.clearAll(sl);   // every swallowed mob is given back first: none may stay hidden, invulnerable and frozen in a boss that is gone
        }
        if (arms != null) {
            arms.discard();   // every exit path: death, discard, unload, run teardown. No orphan display may outlive the boss.
            arms = null;
        }
        if (!this.level().isClientSide() && this.getSize() > 1) {
            super.setSize(1, false);
        }
        super.remove(reason);
    }
}
