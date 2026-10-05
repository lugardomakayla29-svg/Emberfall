package com.solme.emberfall.entity;

import com.mojang.math.Transformation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Display;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Entity.RemovalReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.zombie.Zombie;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/**
 * The Devourer's Phase 2/3 "spitting out mini versions of itself" payoff -
 * a real, independent hostile mob (its own HP pool, not a redirect part
 * like a Hydra head) rather than another composite rig. A genuinely
 * separate creature that has to be found and killed on its own is a much
 * better fit for "the boss splits into adds you have to manage alongside
 * it" than another brain/segment chain would be, and it's far cheaper to
 * spawn a handful of these on a scripted phase event than to stand up a
 * second full composite rig per mini.
 *
 * Deliberately Zombie-based rather than a from-scratch AI (unlike
 * {@link DevourerBrain}, which needs a fully custom burrow state machine):
 * a mini's whole job is "smaller, weaker, chases and bites you", and
 * vanilla Zombie already does exactly that with real, battle-tested
 * pathfinding/target-acquisition/melee goals - reusing it needs zero new
 * AI code, only the dressing (see class javadoc technique already proven
 * by the retired HydraHead/{@link EliteHeads}): the Zombie body itself stays
 * fully invisible, and a smaller-scale "Worm" head display riding it
 * is the only thing a player sees, reading as a shrunken version of the
 * Devourer's own head.
 */
public class DevourerSpawn extends Zombie {
    private static final float DISPLAY_SCALE = 1.1F;

    private Display.ItemDisplay headDisplay;

    public DevourerSpawn(EntityType<? extends Zombie> type, Level level) {
        super(type, level);
        this.setInvisible(true);
    }

    public static AttributeSupplier.Builder createAttributes() {
        // Deliberately weaker than the main brain (createBossAttributes'
        // 260 HP) - a manageable add, not a second boss. Slightly faster
        // than a normal zombie (0.23) to read as aggressive/hunting.
        return Zombie.createAttributes()
                .add(Attributes.MAX_HEALTH, 25.0)
                .add(Attributes.MOVEMENT_SPEED, 0.32)
                .add(Attributes.ATTACK_DAMAGE, 4.0);
    }

    /** Spawns this mini's shrunken "Worm" head display. Call once right after adding to the level. */
    public void attachDisplay(ServerLevel level) {
        if (headDisplay != null) {
            return;
        }
        Display.ItemDisplay display = new Display.ItemDisplay(EntityType.ITEM_DISPLAY, level);
        display.setPos(this.getX(), this.getY(), this.getZ());
        display.setItemStack(EliteHeads.customHead(DevourerBrain.SANDWORM_HEAD_TEXTURE, "devourer_mini"));
        display.setBillboardConstraints(Display.BillboardConstraints.CENTER);
        display.setTransformation(new Transformation(
                new Vector3f(-DISPLAY_SCALE / 2.0F, -DISPLAY_SCALE / 2.0F, -DISPLAY_SCALE / 2.0F),
                new Quaternionf(),
                new Vector3f(DISPLAY_SCALE, DISPLAY_SCALE, DISPLAY_SCALE),
                new Quaternionf()));
        display.setNoGravity(true);
        display.setInvulnerable(true);
        level.addFreshEntity(display);
        display.startRiding(this, true, false);
        this.headDisplay = display;
    }

    @Override
    public net.minecraft.world.phys.Vec3 getPassengerRidingPosition(Entity passenger) {
        return this.position().add(0.0, this.getBbHeight() * 0.55, 0.0);
    }

    @Override
    public void remove(RemovalReason reason) {
        if (headDisplay != null && headDisplay.isAlive()) {
            headDisplay.discard();
        }
        super.remove(reason);
    }
}
