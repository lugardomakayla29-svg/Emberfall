package com.solme.emberfall.entity;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.monster.MagmaCube;
import net.minecraft.world.level.Level;

/**
 * The second, decorative magma cube of a Fodder {@link TikiMagma} totem: a REAL {@link MagmaCube} so it
 * looks exactly like the cube underneath it (a display entity cannot draw a mob model).
 *
 * <p>It is pure decoration, so everything that costs tick time or could affect play is stripped:
 * no goals (nothing to evaluate), no contact damage, no hopping, no pushing, not targetable in effect,
 * and damage aimed at it is forwarded to the owning {@link TikiMagma}, which owns the one real health pool.
 * The owner places it every tick (see {@link TikiMagma#tick}), so it never rides the mob: a passenger would
 * make {@code isVehicle()} true, which vanilla melee goals for some mobs check (Slime's does not, but this
 * keeps the rule uniform with {@link MobRig}).
 */
public class TikiCube extends MagmaCube {
    private TikiMagma owner;

    public TikiCube(EntityType<? extends MagmaCube> type, Level level) {
        super(type, level);
        this.setNoAi(true);
        this.setSilent(true);
        this.setNoGravity(true);
        this.noPhysics = true;
        this.setInvulnerable(false); // damage is redirected in hurtServer, not swallowed by invulnerability
        this.setPersistenceRequired();
    }

    void bind(TikiMagma owner) {
        this.owner = owner;
    }

    @Override
    protected void registerGoals() {
        // Deliberately empty: a decorative cube runs no AI.
    }

    @Override
    protected boolean isDealsDamage() {
        return false;
    }

    @Override
    public boolean isPushable() {
        return false;
    }

    @Override
    public void push(net.minecraft.world.entity.Entity entity) {
        // Never shoves or is shoved.
    }

    @Override
    public boolean hurtServer(ServerLevel level, DamageSource source, float amount) {
        if (owner != null && owner.isAlive()) {
            return owner.hurtServer(level, source, amount);
        }
        return false;
    }

    @Override
    public void tick() {
        super.tick();
        // If the owner is gone (death, discard, unload) this cube must not linger.
        if (!this.level().isClientSide() && (owner == null || owner.isRemoved())) {
            this.discard();
        }
    }
}
