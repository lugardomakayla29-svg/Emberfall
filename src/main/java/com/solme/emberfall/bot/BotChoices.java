package com.solme.emberfall.bot;

import java.util.List;
import java.util.Set;

/**
 * The EmberTester's judgment, kept free of engine types so every rule can be proven on its own. Each method receives plain
 * data (what the offer screen showed) and returns an index, or -1 to skip. The rules are deliberately simple and
 * explainable: a bot that plays like a sensible newcomer is a better test partner than an optimiser, and it must not
 * out-shop or out-build a human in the same party.
 */
public final class BotChoices {
    private BotChoices() {}

    /** One tome on offer: its id, its synergy tags (upper case names) and whether it is a PASSIVE (survival) tome. */
    public record TomeOffer(String id, Set<String> tags, boolean passive) {}

    /**
     * Picks a tome. Score: +3 for each synergy tag it shares with tomes already owned (snowballing one build),
     * +4 for a passive when health is below half (survival first), -100 if it is not allowed (slots full for a new tome).
     * Ties go to the lowest id so the choice is stable and testable. Returns -1 when every offer is disallowed.
     */
    public static int pickTome(List<TomeOffer> offers, Set<String> ownedTags, boolean lowHealth, java.util.function.Predicate<String> allowed) {
        int best = -1;
        int bestScore = Integer.MIN_VALUE;
        for (int i = 0; i < offers.size(); i++) {
            TomeOffer o = offers.get(i);
            if (!allowed.test(o.id())) {
                continue;
            }
            int score = 0;
            for (String t : o.tags()) {
                if (ownedTags.contains(t)) {
                    score += 3;
                }
            }
            if (lowHealth && o.passive()) {
                score += 4;
            }
            if (score > bestScore || (score == bestScore && best >= 0 && o.id().compareTo(offers.get(best).id()) < 0)) {
                best = i;
                bestScore = score;
            }
        }
        return best;
    }

    /** One weapon on offer: its id and its moveset archetype. */
    public record WeaponOffer(String id, String moveset) {}

    /**
     * Picks a weapon. A starting pick takes the first offer. Later it never takes a weapon it already owns, and prefers an
     * archetype it does not have yet (variety covers more situations). Returns -1 (skip) when nothing new is on offer.
     */
    public static int pickWeapon(List<WeaponOffer> offers, Set<String> ownedIds, Set<String> ownedMovesets, boolean starting) {
        if (offers.isEmpty()) {
            return -1;
        }
        if (starting) {
            return 0;
        }
        int fallback = -1;
        for (int i = 0; i < offers.size(); i++) {
            WeaponOffer o = offers.get(i);
            if (ownedIds.contains(o.id())) {
                continue;
            }
            if (!ownedMovesets.contains(o.moveset())) {
                return i;
            }
            if (fallback < 0) {
                fallback = i;
            }
        }
        return fallback;
    }

    /** One shop or merchant line: its price and whether the player can afford it right now. */
    public record Priced(String id, long price, boolean affordable) {}

    /**
     * Picks what to buy: the cheapest affordable line, but only if the bot keeps a reserve afterwards (so it can still
     * pay for a reroll or a chest). Returns -1 to buy nothing.
     */
    public static int pickPurchase(List<Priced> lines, long gold, long reserve) {
        int best = -1;
        for (int i = 0; i < lines.size(); i++) {
            Priced p = lines.get(i);
            if (!p.affordable() || p.price() > gold - reserve) {
                continue;
            }
            if (best < 0 || p.price() < lines.get(best).price()) {
                best = i;
            }
        }
        return best;
    }

    /** Shrines: the first enabled option, or -1 if none is enabled. */
    public static int pickEnabled(List<Boolean> enabled) {
        for (int i = 0; i < enabled.size(); i++) {
            if (enabled.get(i)) {
                return i;
            }
        }
        return -1;
    }
}
