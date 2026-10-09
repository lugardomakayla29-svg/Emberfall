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
    private boolean defeated = false;
    private double curseMultiplier = 1.0;
    /** Party scaling's damage correction (PartyHealth.applyBoss): below 1.0 only when a big party's health would pass the attribute ceiling. */
    private float partyDamageFactor = 1.0F;
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
        setBase(Attributes.MAX_HEALTH, BossTuning.broodtideHealth() * curseMultiplier * partyHealthMultiplier);
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
     * Records the party health multiplier that {@code PartyHealth.applyBoss} just applied to the MAX_HEALTH attribute, so {@link #applySize} keeps it.
     * Read back from the attribute: the ratio of the current max to the unscaled base, which is exact however the wrapper computed it.
     */
    public void rememberPartyScaling() {
        double unscaled = BossTuning.broodtideHealth() * curseMultiplier;
        double now = this.getAttributeBaseValue(Attributes.MAX_HEALTH);
        this.partyHealthMultiplier = unscaled > 0.0 ? Math.max(1.0, now / unscaled) : 1.0;
    }

    public void setPartyDamageFactor(float factor) {
        this.partyDamageFactor = Math.max(0.0001F, Math.min(1.0F, factor));
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
        float scaled = com.solme.emberfall.boss.BroodtideRules.damageTaken(amount, fightTick, partyDamageFactor);
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
        if (!this.level().isClientSide() && this.getSize() > 1) {
            super.setSize(1, false);
        }
        super.remove(reason);
    }
}
