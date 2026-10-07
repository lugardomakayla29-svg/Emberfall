package com.solme.emberfall.bot;

import java.util.Random;

/**
 * One EmberTester's temperament, free of engine types so it can be proven on its own.
 *
 * <p>Every bot is dealt four archetype-weighted traits from its own seed (its UUID), so the same bot always plays the same way and
 * a party of bots does not behave as one body. The four archetypes follow the combat roles real players fall into: the
 * {@link Kind#RUSHER} closes in and keeps hitting, the {@link Kind#COWARD} hangs back and runs when hurt, the
 * {@link Kind#GUARDIAN} holds ground beside its allies, and the {@link Kind#DIPLOMAT} reads the fight and calls others onto its target.
 * Within an archetype every number still varies a little, so two Rushers are not clones.
 *
 * <p>All traits are 0..1 except the distances (blocks) and the reaction delay (ticks).
 */
public final class BotPersonality {
    public enum Kind { RUSHER, COWARD, GUARDIAN, DIPLOMAT }

    private final Kind kind;
    private final double boldness;      // 1 = wades in, 0 = keeps its distance
    private final double caution;       // 1 = retreats early when hurt
    private final double loyalty;       // 1 = stays close to allies
    private final double sociability;   // 1 = greets and rallies others
    private final double agility;       // 1 = strafes and jumps a lot
    private final int reactionTicks;    // delay before it reacts to a new situation

    private BotPersonality(Kind kind, double boldness, double caution, double loyalty, double sociability, double agility, int reactionTicks) {
        this.kind = kind;
        this.boldness = boldness;
        this.caution = caution;
        this.loyalty = loyalty;
        this.sociability = sociability;
        this.agility = agility;
        this.reactionTicks = reactionTicks;
    }

    /** Deals a personality from a seed. The same seed always gives the same bot. */
    public static BotPersonality fromSeed(long seed) {
        // java.util.Random seeded with small, consecutive numbers gives a poorly mixed first draw (measured: only 2 of 4 kinds were
        // ever dealt for seeds 0..3999), so the seed goes through a 64-bit finaliser (SplitMix64) before it is used.
        Random r = new Random(mix(seed));
        Kind k = Kind.values()[r.nextInt(Kind.values().length)];
        return of(k, r);
    }

    private static long mix(long z) {
        z += 0x9E3779B97F4A7C15L;
        z = (z ^ (z >>> 30)) * 0xBF58476D1CE4E5B9L;
        z = (z ^ (z >>> 27)) * 0x94D049BB133111EBL;
        return z ^ (z >>> 31);
    }

    /** A personality of a chosen kind (the traits inside the kind's band still vary with {@code r}). */
    public static BotPersonality of(Kind k, Random r) {
        return switch (k) {
            case RUSHER -> new BotPersonality(k, band(r, 0.80, 1.00), band(r, 0.00, 0.25), band(r, 0.10, 0.40), band(r, 0.20, 0.50), band(r, 0.70, 1.00), 4 + r.nextInt(5));
            case COWARD -> new BotPersonality(k, band(r, 0.00, 0.30), band(r, 0.75, 1.00), band(r, 0.50, 0.80), band(r, 0.20, 0.50), band(r, 0.50, 0.85), 5 + r.nextInt(6));
            case GUARDIAN -> new BotPersonality(k, band(r, 0.40, 0.65), band(r, 0.40, 0.65), band(r, 0.80, 1.00), band(r, 0.30, 0.60), band(r, 0.25, 0.55), 7 + r.nextInt(6));
            case DIPLOMAT -> new BotPersonality(k, band(r, 0.35, 0.60), band(r, 0.35, 0.60), band(r, 0.60, 0.85), band(r, 0.80, 1.00), band(r, 0.45, 0.75), 6 + r.nextInt(5));
        };
    }

    private static double band(Random r, double lo, double hi) {
        return lo + (hi - lo) * r.nextDouble();
    }

    public Kind kind() { return kind; }
    public double boldness() { return boldness; }
    public double caution() { return caution; }
    public double loyalty() { return loyalty; }
    public double sociability() { return sociability; }
    public double agility() { return agility; }
    public int reactionTicks() { return reactionTicks; }

    /**
     * How far from a foe this bot likes to stand: its weapon's stand-off plus a temperament offset. A bold bot stands inside its
     * reach and a timid one well outside it (never below 1.5 blocks, the old floor).
     */
    public double standOff(double weaponStandOff) {
        double offset = (0.5 - boldness) * 6.0; // -3 (bold) .. +3 (timid) blocks
        return Math.max(1.5, weaponStandOff + offset);
    }

    /** Health fraction (0..1) below which this bot turns and runs. A rusher barely flees; a coward flees early. */
    public double fleeBelow() {
        return 0.05 + caution * 0.45; // 0.05 .. 0.50
    }

    /** True when the bot should break off: it is hurt past its own limit. */
    public boolean shouldFlee(double healthFraction) {
        return healthFraction < fleeBelow();
    }

    /** Share of ticks spent strafing sideways rather than straight on (0..0.85). */
    public double strafeShare() {
        return 0.15 + agility * 0.70;
    }

    /** Average ticks between jumps while fighting (a hop at about this rate). */
    public int jumpEveryTicks() {
        return (int) Math.round(120 - agility * 90); // 30 .. 120
    }

    /** How hard a bot is pulled to keep within this many blocks of its nearest ally. */
    public double allyLeash() {
        return 4.0 + (1.0 - loyalty) * 14.0; // 4 (loyal) .. 18 (loner) blocks
    }
}
