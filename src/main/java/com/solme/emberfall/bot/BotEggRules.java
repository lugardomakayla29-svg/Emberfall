package com.solme.emberfall.bot;

/**
 * What the EmberTester spawn egg does, as pure decisions with no game types so they can be checked without a server.
 *
 * <p>A bot is a stand-in for a real player, so it must raise a run's difficulty exactly as a real one would. The director freezes the
 * party size once, {@code freezeAtTick} ticks into the run (WaveDirector.PARTY_FREEZE_AT_TICK), so a bot that joins AFTER that moment
 * would add a body but change nothing. That would be a half-working feature, so it is refused with a reason instead.
 */
public final class BotEggRules {
    private BotEggRules() {}

    /** The most bodies a run may hold, real or bot. Same number as PartyScaling.MAX_PARTY; BotEggRulesCheck keeps them equal. */
    public static final int MAX_PARTY = 10;

    public enum Verdict { OK, NOT_OPERATOR, NO_RUN, PARTY_ALREADY_FROZEN, PARTY_FULL, NAME_TAKEN }

    /**
     * @param operator       whether the user may use operator items
     * @param inRun          whether the user is currently in a run
     * @param elapsedTicks   ticks since that run started (ignored when not in a run)
     * @param freezeAtTick   the tick at which the director counts the party for good
     * @param partySize      bodies in the run right now
     * @param nameTaken      whether the chosen bot name is already online
     */
    public static Verdict decide(boolean operator, boolean inRun, long elapsedTicks, long freezeAtTick, int partySize, boolean nameTaken) {
        if (!operator) return Verdict.NOT_OPERATOR;
        if (!inRun) return Verdict.NO_RUN;
        if (elapsedTicks >= freezeAtTick) return Verdict.PARTY_ALREADY_FROZEN;
        if (partySize >= MAX_PARTY) return Verdict.PARTY_FULL;
        if (nameTaken) return Verdict.NAME_TAKEN;
        return Verdict.OK;
    }

    /** The sentence a player sees for a refusal. null for OK. */
    public static String message(Verdict v, long freezeAtTick) {
        return switch (v) {
            case OK -> null;
            case NOT_OPERATOR -> "Only operators can call an EmberTester.";
            case NO_RUN -> "Start an expedition first: an EmberTester joins the run you are in.";
            case PARTY_ALREADY_FROZEN -> "Too late: the party is counted " + (freezeAtTick / 20) + " seconds into a run. Use the egg in the first seconds.";
            case PARTY_FULL -> "The party is full (" + MAX_PARTY + ").";
            case NAME_TAKEN -> "That EmberTester is already online.";
        };
    }

    /** Next free bot name: EmberTester1, EmberTester2, ... skipping any in {@code taken}. */
    public static String nextName(java.util.Set<String> taken) {
        for (int i = 1; i <= 99; i++) {
            String n = "EmberTester" + i;
            if (!taken.contains(n)) return n;
        }
        return null;
    }
}
