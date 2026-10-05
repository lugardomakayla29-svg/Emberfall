package com.solme.emberfall.tome;

import com.solme.emberfall.EmberfallMod;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.animal.wolf.Wolf;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Design doc 7.2's Summon tag: real player-owned allies backing the Loyal
 * Hound / Spectral Hound Tomes (see TomePool), not a placeholder. Built on
 * a plain tamed vanilla Wolf - taming already gives correct, complete
 * owner-following / owner-defends-you / attacks-your-target AI for free
 * (TamableAnimal + Wolf's own goal selectors), so there's no need for a
 * bespoke AI class here; this class's job is spawning, tracking (so the
 * Summon@3 synergy bonus and run-end cleanup can find them again later),
 * and the synergy stat bump itself.
 *
 * Tracked per-owner rather than per-Tome: a companion outlives the single
 * onApply call that created it (it's a real entity wandering the arena),
 * so PlayerBuild's run-scoped stack-count map can't be the source of truth
 * for "which entities exist" the way it is for stat Tomes. Cleanup runs via
 * the same {@link PlayerBuild#addCleanup} registry stat Tomes use, so
 * companions are reliably discarded when the run ends, matching design doc
 * 10.1's "build state is in-run only" contract.
 */
public final class PlayerCompanions {
    private static final Map<UUID, List<UUID>> companionsByOwner = new ConcurrentHashMap<>();

    private static final double BASE_HEALTH = 20.0;
    private static final double BASE_ATTACK_DAMAGE = 4.0;

    private PlayerCompanions() {}

    /** Spawns one new tamed companion for {@code owner}, already following/defending them. */
    public static Wolf spawnCompanion(ServerPlayer owner, String customName, boolean spectral) {
        ServerLevel level = (ServerLevel) owner.level();
        Wolf wolf = new Wolf(EntityType.WOLF, level);
        wolf.setPos(owner.getX(), owner.getY(), owner.getZ());
        wolf.finalizeSpawn(level, level.getCurrentDifficultyAt(owner.blockPosition()), EntitySpawnReason.MOB_SUMMONED, null);
        wolf.tame(owner);
        wolf.setOrderedToSit(false);
        wolf.setPersistenceRequired();
        wolf.setCustomName(Component.literal(customName));
        wolf.setCustomNameVisible(true);
        if (spectral) {
            wolf.setGlowingTag(true);
        }

        AttributeInstance maxHealth = wolf.getAttribute(Attributes.MAX_HEALTH);
        if (maxHealth != null) {
            maxHealth.setBaseValue(BASE_HEALTH);
            wolf.setHealth((float) BASE_HEALTH);
        }
        AttributeInstance attackDamage = wolf.getAttribute(Attributes.ATTACK_DAMAGE);
        if (attackDamage != null) {
            attackDamage.setBaseValue(BASE_ATTACK_DAMAGE);
        }

        level.addFreshEntity(wolf);

        companionsByOwner.computeIfAbsent(owner.getUUID(), k -> new CopyOnWriteArrayList<>()).add(wolf.getUUID());
        if (SynergyEffects.summonBonusActive(owner)) {
            applySynergyBonus(wolf);
        }
        int reliquaryStacks = PlayerBuild.stacksOf(owner, "reliquary_shard");
        if (reliquaryStacks > 0) {
            applyReliquaryBonus(wolf, reliquaryStacks);
        }
        // Registered on every spawn (idempotent - see dismissAll): guarantees a cleanup is
        // wired up even if this is this owner's first companion this run.
        PlayerBuild.addCleanup(owner, PlayerCompanions::dismissAll);
        return wolf;
    }

    /** Re-applies the Summon@3 stat bump to every companion this owner currently has alive (design doc 7.2). */
    public static void applySynergyBonusToAll(ServerPlayer owner) {
        List<UUID> ids = companionsByOwner.get(owner.getUUID());
        if (ids == null) {
            return;
        }
        ServerLevel level = (ServerLevel) owner.level();
        for (UUID id : ids) {
            if (level.getEntityInAnyDimension(id) instanceof Wolf wolf && wolf.isAlive()) {
                applySynergyBonus(wolf);
            }
        }
    }

    /**
     * Reliquary Shard (Tome, Summon tag): a real standalone stat buff on every owned
     * companion, scaling with stack count - called both on every new pick (retroactively,
     * for companions that already exist) and from spawnCompanion (for companions summoned
     * after the shard is already owned). Uses a single modifier id per wolf per stat and
     * removes-then-re-adds it so a later pick (higher stack count) correctly replaces the
     * earlier amount instead of stacking additively on top of itself.
     */
    public static void applyReliquaryBonusToAll(ServerPlayer owner, int stacks) {
        List<UUID> ids = companionsByOwner.get(owner.getUUID());
        if (ids == null) {
            return;
        }
        ServerLevel level = (ServerLevel) owner.level();
        for (UUID id : ids) {
            if (level.getEntityInAnyDimension(id) instanceof Wolf wolf && wolf.isAlive()) {
                applyReliquaryBonus(wolf, stacks);
            }
        }
    }

    private static void applyReliquaryBonus(Wolf wolf, int stacks) {
        Identifier healthId = Identifier.fromNamespaceAndPath(EmberfallMod.MOD_ID, "reliquary_health_" + wolf.getUUID());
        AttributeInstance maxHealth = wolf.getAttribute(Attributes.MAX_HEALTH);
        if (maxHealth != null) {
            maxHealth.removeModifier(healthId);
            maxHealth.addPermanentModifier(new AttributeModifier(healthId, 0.20 * stacks, AttributeModifier.Operation.ADD_MULTIPLIED_BASE));
            wolf.setHealth(wolf.getMaxHealth());
        }
        Identifier damageId = Identifier.fromNamespaceAndPath(EmberfallMod.MOD_ID, "reliquary_damage_" + wolf.getUUID());
        AttributeInstance attackDamage = wolf.getAttribute(Attributes.ATTACK_DAMAGE);
        if (attackDamage != null) {
            attackDamage.removeModifier(damageId);
            attackDamage.addPermanentModifier(new AttributeModifier(damageId, 1.0 * stacks, AttributeModifier.Operation.ADD_VALUE));
        }
    }

    private static void applySynergyBonus(Wolf wolf) {
        Identifier healthModId = Identifier.fromNamespaceAndPath(EmberfallMod.MOD_ID, "summon3_health_" + wolf.getUUID());
        AttributeInstance maxHealth = wolf.getAttribute(Attributes.MAX_HEALTH);
        if (maxHealth != null && !maxHealth.hasModifier(healthModId)) {
            maxHealth.addPermanentModifier(new AttributeModifier(healthModId, 0.5, AttributeModifier.Operation.ADD_MULTIPLIED_BASE));
            wolf.setHealth(wolf.getMaxHealth());
        }
        Identifier damageModId = Identifier.fromNamespaceAndPath(EmberfallMod.MOD_ID, "summon3_damage_" + wolf.getUUID());
        AttributeInstance attackDamage = wolf.getAttribute(Attributes.ATTACK_DAMAGE);
        if (attackDamage != null && !attackDamage.hasModifier(damageModId)) {
            attackDamage.addPermanentModifier(new AttributeModifier(damageModId, 2.0, AttributeModifier.Operation.ADD_VALUE));
        }
    }

    /** Discards every companion this owner has (run end - see PlayerBuild.clear javadoc). Safe to call with none left. */
    private static void dismissAll(ServerPlayer owner) {
        List<UUID> ids = companionsByOwner.remove(owner.getUUID());
        if (ids == null) {
            return;
        }
        ServerLevel level = (ServerLevel) owner.level();
        for (UUID id : ids) {
            Entity entity = level.getEntityInAnyDimension(id);
            if (entity != null) {
                entity.discard();
            }
        }
    }
}
