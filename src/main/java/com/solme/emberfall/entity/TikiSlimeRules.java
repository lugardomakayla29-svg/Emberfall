package com.solme.emberfall.entity;

/**
 * The Tiki Slime's rules as PURE DATA (no Minecraft types), from docs/design/TIKI_REPLACEMENT.md (owner decision 2026-10-07 18:15 CT: a SLIME version of
 * Tiki Magma, no ice, no frost, no burrowing). The mob itself does not exist yet; this is the first build step ("Pure TikiSlimeRules ... pure check first").
 *
 * Every number below is copied from code that exists today, with the source named, or from the design doc. Where the doc gives NO number (hit points,
 * damage), this class has none either: it does not invent them.
 *
 * Tested headless, look unverified. Nothing here was seen or heard.
 */
public final class TikiSlimeRules {
    private TikiSlimeRules() {}

    public enum Tier { FODDER, ELITE, CORRUPTED }

    public enum Move { HOP, BOUNCE_SHRIEK, GOO_TRAIL, MASK_SPIT, GOO_JET, SPLIT_TOTEM, SWALLOW_MASK }

    /** Masks on the pole: 1 / 3 / 4. TikiMagma.ELITE_SEGMENT_COUNT = 3 (line 89), CORRUPTED_SEGMENT_COUNT = 4 (line 96); the fodder topper is one. */
    public static int masks(Tier t) {
        return switch (t) {
            case FODDER -> 1;
            case ELITE -> 3;
            case CORRUPTED -> 4;
        };
    }

    /** Spawn weight of the Tiki in the horde roll: WaveDirector.TIKI_SPAWN_WEIGHT = 0.15 (line 608). Unchanged by the slime. */
    public static final double SPAWN_WEIGHT = 0.15;

    /** The Tiki's slot in the elite switch: WaveDirector line 443 and 582 ("case 5"), out of ELITE_COUNT = 9 (line 140). Unchanged by the slime. */
    public static final int ELITE_SLOT = 5;
    public static final int ELITE_COUNT = 9;

    /** The share of Elite spawns that become Corrupted: TikiMagma.CORRUPTED_CHANCE = 0.2 (line 93, a placeholder pacing number there too). */
    public static final double CORRUPTED_CHANCE = 0.2;

    // ---- Split Totem (design doc, idea 5): Corrupted only, at 50% and 25% health, one small Tiki Slime each, hard cap 2 ----
    public static final int SPLIT_CAP = 2;
    public static final double[] SPLIT_AT_HEALTH_FRACTION = {0.50, 0.25};

    /** How many small Tiki Slimes a Corrupted may have shed once its health is at {@code fraction} of max; never more than {@link #SPLIT_CAP}. Others shed none. */
    public static int shedsAt(Tier t, double fraction) {
        if (t != Tier.CORRUPTED) {
            return 0;
        }
        int n = 0;
        for (double threshold : SPLIT_AT_HEALTH_FRACTION) {
            if (fraction <= threshold) {
                n++;
            }
        }
        return Math.min(n, SPLIT_CAP);
    }

    /** True if a shed may happen now: the tier allows it and fewer than the cap have been shed already. */
    public static boolean maySplit(Tier t, int alreadyShed) {
        return t == Tier.CORRUPTED && alreadyShed < SPLIT_CAP;
    }

    // ---- Wind-ups, in ticks (20 per second). Existing values are quoted from the code they come from. ----
    /** The least any move may wind up for: 0.5 s (design doc: "every move gets a wind-up of at least 0.5 s"). */
    public static final int MIN_WINDUP_TICKS = 10;

    /** TikiVoice.SHRIEK_WINDUP_TICKS = 18 (line 34). The Bounce Shriek replaces the fire shriek and keeps its wind-up. */
    public static final int SHRIEK_WINDUP_TICKS = 18;
    /** TikiVoice.LASER_WINDUP_TICKS = 22 (line 38). The Goo Jet replaces the laser and keeps its wind-up (doc, Decisions 1). */
    public static final int JET_WINDUP_TICKS = 22;
    /** PinkSlime.SPIT_WINDUP = 14 (line 72). The Mask Spit uses the Pink Slime spit pattern (doc, idea 3). */
    public static final int SPIT_WINDUP_TICKS = 14;
    /** Moves that are not an attack at all and so have no wind-up to check: the hop is movement, the trail is a passive. */
    public static final int NO_WINDUP = 0;

    /** The wind-up of a move. The resolve tick of a move is its start tick plus this, for the telegraph AND the hit, so they cannot disagree. */
    public static int windup(Move m) {
        return switch (m) {
            case BOUNCE_SHRIEK -> SHRIEK_WINDUP_TICKS;
            case GOO_JET -> JET_WINDUP_TICKS;
            case MASK_SPIT -> SPIT_WINDUP_TICKS;
            case SPLIT_TOTEM, SWALLOW_MASK -> JET_WINDUP_TICKS; // PROPOSAL: the doc gives these no number; they borrow the jet's so none is under 0.5 s
            case HOP, GOO_TRAIL -> NO_WINDUP;
        };
    }

    /** True for a move that hurts or changes a player or mob, so it needs a wind-up. The hop and the trail are not attacks with a telegraph. */
    public static boolean isAttack(Move m) {
        return m != Move.HOP && m != Move.GOO_TRAIL;
    }

    /** The tick a move's telegraph ends and its hit lands: one expression for both. */
    public static long resolveTick(Move m, long startTick) {
        return startTick + windup(m);
    }

    /** Which moves a tier has (design doc, Tiers table): the Fodder has no spit and no jet; only the Corrupted sheds or swallows. */
    public static boolean has(Tier t, Move m) {
        return switch (m) {
            case HOP, BOUNCE_SHRIEK, GOO_TRAIL -> true;
            case MASK_SPIT, GOO_JET -> t == Tier.ELITE || t == Tier.CORRUPTED;
            case SPLIT_TOTEM, SWALLOW_MASK -> t == Tier.CORRUPTED;
        };
    }
}
