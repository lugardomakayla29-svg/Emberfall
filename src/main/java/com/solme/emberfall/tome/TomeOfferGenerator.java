package com.solme.emberfall.tome;

import net.minecraft.server.level.ServerPlayer;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

import net.minecraft.util.RandomSource;

/**
 * Design doc 7.4: "Offers should be weighted to avoid presenting duplicates
 * of already-maxed items and to lightly favor completing a synergy tag the
 * player has already started investing in - enough to make builds feel
 * intentional without removing all randomness."
 */
public final class TomeOfferGenerator {
    private static final double SYNERGY_WEIGHT_BONUS = 2.0; // per tag the player already owns 1-2 of

    /**
     * Design doc Section 8 (Greed/Challenge Shrines): "a guaranteed
     * high-rarity Tome offer" - see {@link com.solme.emberfall.shrine.ShrineType}'s
     * javadoc for why this maps onto "guarantee a WEAPON-category Tome"
     * rather than a rarity tier that doesn't exist anywhere in this system.
     * Consumed (removed) the next time this player's offers are generated,
     * whether or not a WEAPON candidate was actually available to honor it.
     */
    private static final Set<UUID> guaranteedWeaponOffer = ConcurrentHashMap.newKeySet();

    private TomeOfferGenerator() {}

    public static void markNextOfferGuaranteedWeapon(ServerPlayer player) {
        guaranteedWeaponOffer.add(player.getUUID());
    }

    /** Drops any pending guarantee when a player leaves a run, so a shrine reward never carries into the next run. */
    public static void clear(ServerPlayer player) {
        guaranteedWeaponOffer.remove(player.getUUID());
    }

    /** Read-only view for the debug command: is a guaranteed weapon offer still pending for this player? */
    public static boolean hasGuaranteedWeaponOffer(ServerPlayer player) {
        return guaranteedWeaponOffer.contains(player.getUUID());
    }

    /** Picks 3 distinct Tomes to offer, weighted per 7.4. Returns fewer than 3 only if the pool runs out. */
    public static List<Tome> generateOffers(ServerPlayer player) {
        Set<String> banished = PlayerTomeCharges.banishedTomeIds(player);
        List<Tome> candidates = new ArrayList<>();
        for (Tome tome : TomePool.ALL) {
            if (banished.contains(tome.id())) {
                continue; // permanently excluded for this run via a spent Banish charge
            }
            if (tome.isWeaponGated() && !com.solme.emberfall.item.PlayerWeapon.isRunning(player, tome.requiresWeaponId())) {
                continue; // a weapon capstone is only worth offering to a player running that weapon
            }
            if (!PlayerBuild.canTake(player, tome.id())) {
                continue; // a NEW tome needs a free tome slot; one the player already holds just stacks
            }
            if (PlayerBuild.stacksOf(player, tome.id()) < tome.maxStacks()) {
                candidates.add(tome);
            }
        }

        List<Tome> offers = new ArrayList<>();
        RandomSource random = player.getRandom();
        int remaining = Math.min(3, candidates.size());

        if (guaranteedWeaponOffer.remove(player.getUUID()) && remaining > 0) {
            List<Tome> weaponCandidates = candidates.stream()
                    .filter(t -> t.category() == TomeCategory.WEAPON)
                    .collect(Collectors.toList());
            if (!weaponCandidates.isEmpty()) {
                Tome picked = weightedPick(weaponCandidates, player, random);
                offers.add(picked);
                candidates.remove(picked);
                remaining--;
            }
        }

        for (int i = 0; i < remaining; i++) {
            Tome picked = weightedPick(candidates, player, random);
            offers.add(picked);
            candidates.remove(picked);
        }
        return offers;
    }

    private static Tome weightedPick(List<Tome> candidates, ServerPlayer player, RandomSource random) {
        double totalWeight = 0.0;
        double[] weights = new double[candidates.size()];
        for (int i = 0; i < candidates.size(); i++) {
            weights[i] = weightOf(candidates.get(i), player);
            totalWeight += weights[i];
        }
        double roll = random.nextDouble() * totalWeight;
        double cumulative = 0.0;
        for (int i = 0; i < candidates.size(); i++) {
            cumulative += weights[i];
            if (roll < cumulative) {
                return candidates.get(i);
            }
        }
        return candidates.get(candidates.size() - 1); // floating point fallback
    }

    private static double weightOf(Tome tome, ServerPlayer player) {
        double weight = 1.0;
        for (SynergyTag tag : tome.tags()) {
            int owned = PlayerBuild.tagCount(player, tag);
            if (owned >= 1 && owned < 3) {
                weight += SYNERGY_WEIGHT_BONUS; // already invested, not yet complete - nudge toward finishing it
            }
        }
        return weight;
    }
}
