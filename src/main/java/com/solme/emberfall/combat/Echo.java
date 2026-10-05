package com.solme.emberfall.combat;

import com.solme.emberfall.item.WeaponGrowth;

import java.util.ArrayList;
import java.util.List;

/**
 * The shared rule behind every weapon's extra strikes ("echoes"). Measured before this existed: all eight weapons dealt EXACTLY the same damage to a
 * lone foe at level 1 and level 10, because every extra arrow, bolt, sweep, hop and chain went to OTHER foes. An echo may now land on the primary
 * target itself once the other foes run out, and a per-level damage lift makes each strike hit harder, so levelling always shows against a boss.
 */
public final class Echo {
    /** Each echo strike is this much stronger per level above 1: 1.00 at level 1 to 1.45 at level 10. */
    public static final double LIFT_PER_LEVEL = 0.05;

    private Echo() {}

    /** The damage multiple for an echo strike of a weapon at {@code level}. */
    public static double lift(int level) {
        return WeaponGrowth.scale(1.0, LIFT_PER_LEVEL, level);
    }

    /**
     * Picks {@code count} victims for echo strikes: the other foes first (nearest first, as given), then the primary again for whatever strikes
     * remain, so a lone foe is struck as many times as a crowd. {@code count} of 0 or less gives an empty list.
     */
    public static <T> List<T> victims(T primary, List<? extends T> others, int count) {
        List<T> out = new ArrayList<>();
        if (count <= 0) {
            return out;
        }
        for (T other : others) {
            if (out.size() >= count) {
                break;
            }
            out.add(other);
        }
        while (out.size() < count) {
            out.add(primary);
        }
        return out;
    }
}
