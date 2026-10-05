package com.solme.emberfall.combat;

import com.mojang.math.Transformation;
import com.solme.emberfall.EmberfallMod;
import net.minecraft.core.component.DataComponents;
import net.minecraft.util.Brightness;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Display;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.UUID;

/**
 * The Ashen Beacon's Pyre Nova centrepiece: a real lantern BLOCK model that hangs over the beacon, SPINS and BOBS while the nova rings
 * erupt, with a purple countdown above it and dark and red embers streaming off it.
 *
 * ONE entity per lantern, one lantern per player, alive only for {@link #LIFETIME_TICKS}. It is a Block Display (no hitbox, no AI, no
 * pathing). Its motion costs one NBT reload every {@link #STEP_TICKS} ticks, not one packet a tick: the client animates between poses.
 *
 * SPIN: a full turn is sent as steps of 90 degrees. Measured on the real {@code Transformation.slerp}: 190 and 270 degrees run
 * BACKWARDS and 360 does not move at all, so no single step may reach 180.
 *
 * CLEANUP: {@link #clear(UUID)} on leave and disconnect, {@link #clearAll()} at run end, and the tick drops a lantern whose owner is gone,
 * whose level unloaded, or whose display was removed. Nothing can leave a lantern floating.
 */
public final class PyreLantern {
    public static final int LIFETIME_TICKS = 80;       // 4 seconds, long enough for all 4 nova rings (24 ticks) and to be seen
    public static final int STEP_TICKS = 9;            // one pose change per 9 ticks: 90 degrees a step is 360 in 36 ticks (1.8 s)
    public static final float STEP_DEGREES = 90.0F;    // strictly under 180, see the class comment
    public static final float SIZE = 1.1F;
    public static final float LIFT_LOW = 2.0F;
    public static final float LIFT_HIGH = 2.5F;
    /** The model lives in the resource pack (assets/emberfall/items/pyre_lantern.json), so any stack can wear it: no new item is registered. */
    private static ItemStack lanternStack() {
        ItemStack stack = new ItemStack(Items.PAPER);
        stack.set(DataComponents.ITEM_MODEL, EmberfallMod.id("pyre_lantern"));
        return stack;
    }
    private static final DustParticleOptions DARK = new DustParticleOptions(0x2A0A14, 1.2F);
    private static final DustParticleOptions RED = new DustParticleOptions(0xC01818, 1.1F);
    private static final boolean TEST_MODE = Boolean.getBoolean("emberfall.testMode");

    private static final class Lantern {
        final UUID ownerId;
        final ServerLevel level;
        final Vec3 centre;
        final long endsAt;
        Display.ItemDisplay display;
        float yaw;
        boolean high;
        long nextStep;

        Lantern(UUID ownerId, ServerLevel level, Vec3 centre, long endsAt) {
            this.ownerId = ownerId;
            this.level = level;
            this.centre = centre;
            this.endsAt = endsAt;
        }
    }

    private static final List<Lantern> ACTIVE = new ArrayList<>();

    private PyreLantern() {}

    /** Raises a lantern over {@code beacon}; replaces any lantern this player already has. */
    public static void raise(ServerLevel level, ServerPlayer owner, Vec3 beacon) {
        clear(owner.getUUID());
        Vec3 centre = beacon.add(0.0, LIFT_LOW, 0.0);
        long now = level.getGameTime();
        Lantern l = new Lantern(owner.getUUID(), level, beacon, now + LIFETIME_TICKS);
        Display.ItemDisplay d = new Display.ItemDisplay(EntityType.ITEM_DISPLAY, level);
        d.setPos(beacon.x, beacon.y, beacon.z);
        d.setItemStack(lanternStack());
        d.setBrightnessOverride(new Brightness(15, 15));    // full light: it glows in a dark arena
        d.setViewRange(2.0F);
        d.setNoGravity(true);
        d.setInvulnerable(true);
        d.addTag("emberfall_run");                           // run teardown sweeps any stray display
        d.setCustomName(label(LIFETIME_TICKS));
        d.setCustomNameVisible(true);
        d.setTransformationInterpolationDuration(STEP_TICKS);
        d.setTransformation(pose(0.0F, LIFT_LOW));
        level.addFreshEntity(d);
        l.display = d;
        l.nextStep = now + STEP_TICKS;
        ACTIVE.add(l);
        if (TEST_MODE) {
            com.solme.emberfall.EmberfallMod.LOGGER.info("LANTERN_TEST raise x={} y={} z={} lives={}", String.format("%.1f", centre.x),
                    String.format("%.1f", centre.y), String.format("%.1f", centre.z), LIFETIME_TICKS);
        }
    }

    /**
     * The lantern model is drawn about the ORIGIN of its item display (an item model is centred on 0,0,0 by the renderer, unlike a block
     * display whose 0..1 cube starts at the corner), so turning it about Y spins it in place. Lifted by {@code lift}, sized by {@link #SIZE}.
     */
    private static Transformation pose(float yawDegrees, float lift) {
        return new Transformation(new Vector3f(0.0F, lift, 0.0F),
                new Quaternionf().rotateY((float) java.lang.Math.toRadians(yawDegrees)), new Vector3f(SIZE, SIZE, SIZE), new Quaternionf());
    }

    private static Component label(int ticksLeft) {
        int seconds = (ticksLeft + 19) / 20;
        return Component.literal("Pyre Lantern " + seconds + "s").withStyle(Style.EMPTY.withColor(0xB04CFF).withBold(true));
    }

    /** One tick for every live lantern: pose steps, countdown, embers, expiry. Called from TotemWeaponSystem.tickAll. */
    public static void tickAll() {
        if (ACTIVE.isEmpty()) {
            return;
        }
        Iterator<Lantern> it = ACTIVE.iterator();
        while (it.hasNext()) {
            Lantern l = it.next();
            long now = l.level.getGameTime();
            if (now >= l.endsAt || l.display == null || l.display.isRemoved()
                    || l.level.getServer().getPlayerList().getPlayer(l.ownerId) == null) {
                discard(l);
                it.remove();
                continue;
            }
            Vec3 at = l.centre.add(0.0, l.high ? LIFT_HIGH : LIFT_LOW, 0.0);
            if (now >= l.nextStep) {
                l.nextStep = now + STEP_TICKS;
                l.yaw = (l.yaw + STEP_DEGREES) % 360.0F;
                l.high = !l.high;
                l.display.setTransformationInterpolationDelay(0);
                l.display.setTransformation(pose(l.yaw, l.high ? LIFT_HIGH : LIFT_LOW));
                l.display.setCustomName(label((int) (l.endsAt - now)));
                if (TEST_MODE) {
                    com.solme.emberfall.EmberfallMod.LOGGER.info("LANTERN_TEST step yaw={} high={} left={}", (int) l.yaw, l.high, l.endsAt - now);
                }
            }
            // dark and red embers streaming off it, kept small: 4 dust and 1 flame a tick per lantern
            double a = now * 0.6;
            l.level.sendParticles(DARK, at.x + java.lang.Math.cos(a) * 0.5, at.y, at.z + java.lang.Math.sin(a) * 0.5, 2, 0.15, 0.3, 0.15, 0.0);
            l.level.sendParticles(RED, at.x - java.lang.Math.cos(a) * 0.5, at.y + 0.1, at.z - java.lang.Math.sin(a) * 0.5, 2, 0.15, 0.3, 0.15, 0.0);
            l.level.sendParticles(ParticleTypes.FLAME, at.x, at.y - 0.3, at.z, 1, 0.1, 0.1, 0.1, 0.01);
        }
    }

    private static void discard(Lantern l) {
        if (l.display != null && !l.display.isRemoved()) {
            l.display.discard();
        }
        if (TEST_MODE) {
            com.solme.emberfall.EmberfallMod.LOGGER.info("LANTERN_TEST discard");
        }
    }

    /** Removes this player's lantern now (leave, disconnect, a new nova). */
    public static void clear(UUID ownerId) {
        Iterator<Lantern> it = ACTIVE.iterator();
        while (it.hasNext()) {
            Lantern l = it.next();
            if (l.ownerId.equals(ownerId)) {
                discard(l);
                it.remove();
            }
        }
    }

    /** Removes every lantern (run end). */
    public static void clearAll() {
        for (Lantern l : ACTIVE) {
            discard(l);
        }
        ACTIVE.clear();
    }

    public static int active() {
        return ACTIVE.size();
    }
}
