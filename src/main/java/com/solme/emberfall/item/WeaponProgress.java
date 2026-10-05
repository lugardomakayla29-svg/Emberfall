package com.solme.emberfall.item;

import com.solme.emberfall.combat.MobPresentation;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Mob;

/**
 * Turns a weapon's play into growth: a kill raises its level, every hit and kill fills its ultimate meter, and the
 * weapon's own ultimate fires by itself when the meter is full. The numbers live in {@link WeaponGrowth}; this class is the
 * bridge to the game (the loadout slot, the action bar, the sound).
 *
 * Which weapon acts is read from {@link Loadout#actingFor}, set by every weapon's damage code (see {@link Loadout#acting}),
 * so a kill is credited to the weapon that landed it even though every weapon shares one damage source.
 */
public final class WeaponProgress {
    /** Meter gained by a plain hit, a kill of each tier (filler, veteran, elite or boss). Ten elite kills fill it from empty. */
    static final int GAIN_HIT = 6;
    static final int GAIN_KILL_FILLER = 25;
    static final int GAIN_KILL_VETERAN = 60;
    static final int GAIN_KILL_ELITE = 140;

    private WeaponProgress() {}

    /** A paying kill by {@code killer}: credits the acting weapon, levels it up, and fills its meter. */
    public static void onKill(ServerPlayer killer, Mob victim) {
        Loadout.Slot slot = Loadout.actingFor(killer);
        if (slot == null) {
            return; // not landed by a weapon (a fall, a hazard, a tome effect): nothing to credit
        }
        boolean levelled = slot.addKill();
        int gain = switch (MobPresentation.tierOf(victim)) {
            case 0 -> GAIN_KILL_FILLER;
            case 1 -> GAIN_KILL_VETERAN;
            default -> GAIN_KILL_ELITE;
        };
        slot.setMeter(WeaponGrowth.fill(slot.meter(), gain));
        if (levelled && killer.level() instanceof ServerLevel level) {
            killer.displayClientMessage(Component.literal("\u00A76" + slot.weapon().displayName() + " \u00A7elevel " + slot.level()), true);
            level.playSound(null, killer.getX(), killer.getY(), killer.getZ(),
                    SoundEvents.PLAYER_LEVELUP, SoundSource.PLAYERS, 0.5F, 1.4F);
        }
    }

    /** A landed hit by the acting weapon: a small meter gain. Called from the weapon's own hit code. */
    public static void onHit(ServerPlayer player) {
        Loadout.Slot slot = Loadout.actingFor(player);
        if (slot != null) {
            slot.setMeter(WeaponGrowth.fill(slot.meter(), GAIN_HIT));
        }
    }

    /** A landed hit worth {@code gain} meter, for a weapon whose single hits are small and many (the Ashen Beacon's living flames). */
    public static void onHit(ServerPlayer player, int gain) {
        Loadout.Slot slot = Loadout.actingFor(player);
        if (slot != null && gain > 0) {
            slot.setMeter(WeaponGrowth.fill(slot.meter(), gain));
        }
    }

    /** The acting weapon's level, or 1 when none is acting. Weapon code uses this to size its radius, counts and damage. */
    public static int levelOf(ServerPlayer player) {
        Loadout.Slot slot = Loadout.actingFor(player);
        return slot == null ? 1 : slot.level();
    }

    /** True when the acting weapon's meter is full. The weapon then calls {@link #consumeUltimate} and plays its ultimate. */
    public static boolean ultimateReady(ServerPlayer player) {
        Loadout.Slot slot = Loadout.actingFor(player);
        return slot != null && WeaponGrowth.ready(slot.meter());
    }

    /** Empties the meter (keeping overflow). Returns false, changing nothing, if the meter was not full. */
    public static boolean consumeUltimate(ServerPlayer player) {
        Loadout.Slot slot = Loadout.actingFor(player);
        if (slot == null || !WeaponGrowth.ready(slot.meter())) {
            return false;
        }
        slot.setMeter(WeaponGrowth.afterFire(slot.meter()));
        slot.countUltimate();
        String name = WeaponPool.ultimateName(slot.weapon().id());
        if (name != null) {
            // Every weapon's ultimate fires through here, so one line tells the player what just happened (they last 3 to 11 s now).
            player.displayClientMessage(Component.literal("\u00A7d\u00A7l" + name + "\u00A7r \u00A77unleashed"), true);
        }
        return true;
    }
}
