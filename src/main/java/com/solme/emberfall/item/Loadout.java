package com.solme.emberfall.item;

import net.minecraft.server.level.ServerPlayer;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * A player's run-scoped weapon loadout: up to {@link #MAX_SLOTS} weapons, each with its own cadence
 * and its own combo state. Nothing here is persistent: it is rebuilt every run, while how many slots a
 * player may use is a persistent shop unlock (see {@link SlotUnlocks}).
 *
 * Every weapon keeps its own streak, Steady Hand flag and Grave Anchor pile, so two weapons can never
 * corrupt each other's combo. {@link PlayerWeapon} exposes the same per-player methods it always had and
 * routes them to whichever slot is currently being dispatched (see {@link #beginDispatch}), which is what
 * lets the ~20 existing call sites stay untouched.
 */
public final class Loadout {
    public static final int MAX_SLOTS = 4;

    /** One weapon in the loadout and everything that belongs to that weapon alone. */
    public static final class Slot {
        final WeaponType weapon;
        int streak;
        boolean steadyHandArmed;
        PlayerWeapon.PileRecord pile;
        /** Game time at which this weapon may fire again. Replaces vanilla's single shared attack timer. */
        long nextReadyTick;
        /** Kills this weapon has made this run (paying Emberfall mobs only), the source of its level. */
        int kills;
        /** Ultimate meter, 0 to {@link WeaponGrowth#METER_MAX} plus overflow. Filled by this weapon's own play. */
        int meter;
        /** Ultimates fired by this weapon this run. */
        int ultimates;

        Slot(WeaponType weapon) {
            this.weapon = weapon;
        }

        public WeaponType weapon() {
            return weapon;
        }

        public long nextReadyTick() {
            return nextReadyTick;
        }

        public int kills() {
            return kills;
        }

        /** This weapon's level this run, 1 to {@link WeaponGrowth#MAX_LEVEL}. */
        public int level() {
            return WeaponGrowth.levelForKills(kills);
        }

        public int meter() {
            return meter;
        }

        /** How many times this weapon's ultimate has fired this run (shown by the debug readback and, later, the HUD). */
        public int ultimates() {
            return ultimates;
        }

        void countUltimate() {
            ultimates++;
        }

        public void setMeter(int value) {
            this.meter = Math.max(0, value);
        }

        /** Credits one kill. Returns true when it raised the level, so the caller can show the level-up. */
        public boolean addKill() {
            int before = level();
            kills++;
            return level() > before;
        }

        public void setNextReadyTick(long tick) {
            this.nextReadyTick = tick;
        }
    }

    private static final Map<UUID, Loadout> loadouts = new ConcurrentHashMap<>();

    private final List<Slot> slots = new ArrayList<>(MAX_SLOTS);
    /** Slot whose state the per-player {@link PlayerWeapon} methods currently read and write. */
    private int active;

    private Loadout() {}

    /** The player's loadout, created empty on first use. */
    public static Loadout of(ServerPlayer player) {
        return loadouts.computeIfAbsent(player.getUUID(), id -> new Loadout());
    }

    /** The loadout if one exists, without creating it. */
    public static Loadout peek(ServerPlayer player) {
        return loadouts.get(player.getUUID());
    }

    public static void discard(ServerPlayer player) {
        loadouts.remove(player.getUUID());
    }

    public int size() {
        return slots.size();
    }

    public Slot slot(int index) {
        return slots.get(index);
    }

    public boolean isEmpty() {
        return slots.isEmpty();
    }

    /** Index of the slot holding {@code weaponId}, or -1. */
    public int indexOf(String weaponId) {
        for (int i = 0; i < slots.size(); i++) {
            if (slots.get(i).weapon.id().equals(weaponId)) {
                return i;
            }
        }
        return -1;
    }

    public boolean has(String weaponId) {
        return indexOf(weaponId) >= 0;
    }

    /** Adds a weapon to the first free slot. Returns its index, or -1 if full or already owned. */
    public int add(WeaponType weapon, int allowedSlots) {
        if (has(weapon.id()) || slots.size() >= Math.min(allowedSlots, MAX_SLOTS)) {
            return -1;
        }
        slots.add(new Slot(weapon));
        return slots.size() - 1;
    }

    /** Replaces the weapon in {@code index}, wiping that slot's combo state. */
    public void replace(int index, WeaponType weapon) {
        slots.set(index, new Slot(weapon));
    }

    public List<WeaponType> weapons() {
        List<WeaponType> out = new ArrayList<>(slots.size());
        for (Slot s : slots) {
            out.add(s.weapon);
        }
        return out;
    }

    // ------------------------------------------------------------------ dispatch routing

    /** Points the per-player {@link PlayerWeapon} methods at {@code index} until {@link #endDispatch}. */
    public void beginDispatch(int index) {
        active = Math.max(0, Math.min(index, Math.max(0, slots.size() - 1)));
    }

    public void endDispatch() {
        active = 0;
    }

    // ------------------------------------------------------------------ who is acting (kill credit)

    /**
     * The weapon whose damage code is running RIGHT NOW on the server thread, or null. Every weapon path ends in the same
     * {@code playerAttack(player)} damage source, which carries no weapon identity, so the death hook cannot tell which
     * weapon landed a kill. Each weapon's damage code therefore sets this around its own hurt calls (see {@link #acting}),
     * and the death hook credits the slot it names. It is only ever read and written on the server thread, inside one call,
     * and is always cleared in a finally block, so it can never leak from one hit into the next.
     */
    private static Slot actingSlot;
    private static UUID actingPlayer;

    /** Runs {@code work} with {@code slotIndex} of {@code player}'s loadout named as the acting weapon. */
    public static void acting(ServerPlayer player, int slotIndex, Runnable work) {
        Loadout l = loadouts.get(player.getUUID());
        Slot previousSlot = actingSlot;
        UUID previousPlayer = actingPlayer;
        actingSlot = (l != null && slotIndex >= 0 && slotIndex < l.slots.size()) ? l.slots.get(slotIndex) : null;
        actingPlayer = player.getUUID();
        try {
            work.run();
        } finally {
            actingSlot = previousSlot;
            actingPlayer = previousPlayer;
        }
    }

    /** The level of the slot holding {@code weaponId} for {@code player}, or 1 when they do not carry it. Used where the slot being fired is not yet named acting (reach is read before the swing). */
    public static int levelOfWeapon(ServerPlayer player, String weaponId) {
        Loadout l = loadouts.get(player.getUUID());
        if (l == null) {
            return 1;
        }
        for (Slot sl : l.slots) {
            if (sl.weapon().id().equals(weaponId)) {
                return sl.level();
            }
        }
        return 1;
    }

    /** Index of the slot acting for {@code player} right now, or -1. Captured at fire time by anything that lands LATER. */
    public static int actingIndexFor(ServerPlayer player) {
        Slot s = actingFor(player);
        if (s == null) {
            return -1;
        }
        Loadout l = loadouts.get(player.getUUID());
        return l == null ? -1 : l.slots.indexOf(s);
    }

    /**
     * Wraps a callback that will run on a LATER tick (a bolt landing, a delayed nova) so it runs with the same weapon named as
     * acting as when it was fired. Without this a bow kill reaches the death hook after {@link #acting} has already returned,
     * finds no acting weapon, and is credited to nobody (found by weapon_growth_test: a far bow kill gave 0 kills). The slot is
     * captured by INDEX, not by reference, so a weapon swapped out before the bolt lands credits nothing instead of the wrong weapon.
     */
    public static <T> java.util.function.Consumer<T> deferred(ServerPlayer player, java.util.function.Consumer<T> callback) {
        final int index = actingIndexFor(player);
        final Slot captured = actingFor(player);
        return value -> {
            Loadout l = loadouts.get(player.getUUID());
            boolean stillThere = index >= 0 && l != null && index < l.slots.size() && l.slots.get(index) == captured;
            if (stillThere) {
                acting(player, index, () -> callback.accept(value));
            } else {
                callback.accept(value);
            }
        };
    }

    /** Same as {@link #deferred(ServerPlayer, java.util.function.Consumer)} for a two argument callback (a sweep bolt's position and victim). */
    public static <A, B> java.util.function.BiConsumer<A, B> deferred2(ServerPlayer player, java.util.function.BiConsumer<A, B> callback) {
        java.util.function.Consumer<Object[]> inner = deferred(player, args -> {
            @SuppressWarnings("unchecked") A a = (A) args[0];
            @SuppressWarnings("unchecked") B b = (B) args[1];
            callback.accept(a, b);
        });
        return (a, b) -> inner.accept(new Object[]{a, b});
    }

    /** The slot to credit for a kill made by {@code player}, or null when no weapon is acting for them. */
    public static Slot actingFor(ServerPlayer player) {
        return actingSlot != null && player.getUUID().equals(actingPlayer) ? actingSlot : null;
    }

    /** The slot the per-player state methods currently address, or null if the loadout is empty. */
    Slot activeSlot() {
        return slots.isEmpty() ? null : slots.get(Math.min(active, slots.size() - 1));
    }
}
