package com.solme.emberfall.combat;

import com.solme.emberfall.item.WeaponGrowth;

import java.util.ArrayList;
import java.util.List;

/**
 * The Gravechain's ultimate, second half: GRAVE LEGION. Every foe that dies inside the Grave Vortex is raised as a spectral REVENANT that
 * fights for the player until the vortex ends.
 *
 * ZERO EXTRA ENTITIES. A real raised mob would be a team mob: the arena's RunMobTeam vetoes mob-on-mob damage and re-targets anything that
 * hunts a fellow mob, and every extra entity costs a tick. A revenant is a record in a small list, drawn with particles and striking through
 * the same {@code Loadout.acting} path as the chain, so its kills count for the chain, feed its meter and trigger tomes.
 *
 * The decisions here are pure (no Minecraft types) so they can be unit-checked: how many may stand, how hard they hit, how each mob kind fights.
 */
public final class GraveLegion {
    /** Most revenants standing at once in one vortex. Bounds both the work per tick and the particles sent. */
    public static final int CAP_BASE = 4;
    public static final double CAP_PER_LEVEL = 8.0 / 9.0;      // 4 at level 1 up to 12 at level 10
    /** A revenant strikes for this share of the chain hit, growing with the level. */
    public static final double STRIKE_BASE = 0.6;
    public static final double STRIKE_PER_LEVEL = 0.04;        // 0.60 at level 1 up to 0.96 at level 10
    /** A revenant stands this many strike steps before it crumbles, so the legion never outlives the vortex. */
    public static final int LIFE_STEPS = 10;

    /** How a raised foe fights, by what it was in life. */
    public enum Kind {
        /** Zombies, husks, drowned, pink slime and the rest: a plain lunge for the whole strike. */
        BRUTE(1.00, 1, 0x6FA86F),
        /** Skeletons, strays, bogged: strikes from further out, a little weaker. */
        ARCHER(0.80, 1, 0xE6E6D2),
        /** Spiders: two quick bites for half a strike each, so the same total but twice the hits (and twice the tome procs). */
        BITER(0.50, 2, 0x5A3A5A),
        /** Witches, illagers, casters: a curse that lands on two foes. */
        CASTER(0.70, 2, 0xB06AE0),
        /** Big mobs (bosses excluded): one slow heavy blow. */
        TITAN(1.60, 1, 0xC04020);

        public final double share;      // multiple of the revenant strike
        public final int hits;          // separate blows per strike step
        public final int colour;        // RGB of its soul fire

        Kind(double share, int hits, int colour) {
            this.share = share;
            this.hits = hits;
            this.colour = colour;
        }
    }

    private GraveLegion() {}

    /** How many revenants may stand at this level: 4 at level 1, 12 at level 10. */
    public static int cap(int level) {
        return (int) Math.round(WeaponGrowth.scale(CAP_BASE, CAP_PER_LEVEL, level));
    }

    /** The share of the chain's hit one revenant strike deals at this level. */
    public static double strikeShare(int level) {
        return WeaponGrowth.scale(STRIKE_BASE, STRIKE_PER_LEVEL, level);
    }

    /** Picks how a foe fights from its entity id path (the part after "emberfall:" / "minecraft:") and its max health. Pure. */
    public static Kind kindOf(String idPath, double maxHealth) {
        String id = idPath == null ? "" : idPath;
        if (maxHealth >= 60.0) {
            return Kind.TITAN;
        }
        if (id.contains("skeleton") || id.contains("stray") || id.contains("bogged")) {
            return Kind.ARCHER;
        }
        if (id.contains("spider")) {
            return Kind.BITER;
        }
        if (id.contains("witch") || id.contains("illager") || id.contains("evoker") || id.contains("mage") || id.contains("magus")
                || id.contains("necromancer") || id.contains("caller")) {
            return Kind.CASTER;
        }
        return Kind.BRUTE;
    }

    /**
     * Who may be raised: adds {@code kind} to {@code standing} when there is room and returns true. A full legion raises nothing, and the
     * caller must not grow the list past the cap. The list holds only kinds, so it is cheap to copy and to test.
     */
    public static boolean raise(List<Kind> standing, Kind kind, int level) {
        if (standing.size() >= cap(level)) {
            return false;
        }
        standing.add(kind);
        return true;
    }

    /** The damage of ONE blow of a revenant of this kind, given the chain's hit damage. */
    public static float blow(Kind kind, double hitDamage, int level) {
        return (float) (hitDamage * strikeShare(level) * kind.share / kind.hits);
    }

    /** The total damage one revenant deals in one strike step: its blows added up. */
    public static float strikeTotal(Kind kind, double hitDamage, int level) {
        return blow(kind, hitDamage, level) * kind.hits;
    }

    /** Count by kind, for the test and the debug line. */
    public static int countOf(List<Kind> standing, Kind kind) {
        int n = 0;
        for (Kind k : standing) {
            if (k == kind) {
                n++;
            }
        }
        return n;
    }

    public static List<Kind> newLegion() {
        return new ArrayList<>();
    }
}
