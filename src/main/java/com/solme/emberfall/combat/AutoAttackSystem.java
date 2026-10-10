package com.solme.emberfall.combat;

import com.solme.emberfall.EmberfallMod;
import com.solme.emberfall.entity.TrackedProjectiles;
import com.solme.emberfall.item.ModItems;
import com.solme.emberfall.item.Loadout;
import com.solme.emberfall.item.PlayerWeapon;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import com.solme.emberfall.item.WeaponMoveset;
import com.solme.emberfall.item.WeaponType;
import com.solme.emberfall.tome.CombatStats;
import com.solme.emberfall.world.Dimensions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.List;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.UUID;

/**
 * Design doc 4.4: the player never manually clicks to attack inside a run -
 * the game auto-targets the nearest live hostile in range and lands the hit
 * for them, every tick the player's weapon is fully "wound up".
 *
 * Weapon system: *what* the auto-attack actually does now branches on the
 * held weapon's {@link WeaponMoveset} (read straight off the mainhand item
 * via {@link ModItems#weaponFor}, no separate "equipped weapon" state to
 * keep in sync - if the item ever changes, behavior changes with it for
 * free, same as vanilla attack-speed already does). *When* it fires is
 * unchanged: {@code getAttackStrengthScale(0) >= 1} still gates every
 * moveset, melee or ranged, so a weapon's own ATTACK_SPEED attribute
 * (set in {@link ModItems}) is still the one and only cadence knob.
 *
 * MELEE_SINGLE deliberately still reuses vanilla combat end-to-end exactly
 * as before ({@code Player#attack(Entity)} - correct attribute-based
 * damage, criticals, knockback, sweep, sounds for free). The other 4
 * movesets build on top of that same foundation rather than replacing it:
 * MELEE_DUAL/MELEE_CLEAVE still call {@code player.attack(target)} for
 * their primary hit and only add extra effects around it; RANGED_SINGLE/
 * RANGED_AOE genuinely skip melee entirely and deal their own configured
 * {@link WeaponType#rangedDamage()} through a virtual
 * {@link TrackedProjectiles} bolt instead (same tool Blightfeather
 * Marksman's ranged kit already uses), with {@code hurtServer} + a
 * player-attack DamageSource so kills stay correctly player-attributed
 * (LevelingHandler's XP-orb level-up detection depends on that).
 */
public final class AutoAttackSystem {
    private static final double CLEAVE_RADIUS = 2.5;
    private static final double AOE_RADIUS = 3.5;
    private static final double PIERCE_RADIUS = 1.2; // capsule radius around the Hunting Bow's piercing-beam line
    private static final double SEEKING_PIERCE_RADIUS = 3.0; // Hunter's Instinct stack 2's wider "bent beam" capsule radius
    private static final double SEEKING_MIN_ANGLE_COS = 0.85; // ~32 degrees off dir - keeps it a forward cone, not an AOE
    private static final double SPARK_SEARCH_RADIUS = 9.0; // Arcane Convergence's seeking-spark search range from the Nova epicenter
    private static final int SPARK_MAX_COUNT = 2; // sparks per detonation - bounded so a chain can't snowball across a whole horde
    // Arcane Convergence stack 3's "kill-fueled" hop is allowed past the stack's own hop count as
    // long as the previous Nova in the chain actually killed something - self-limiting in practice
    // (it stops the moment a hop stops killing), but still needs a hard absolute ceiling regardless
    // of how many kills happen, so one lucky wipe can never turn into an unbounded recursive chain
    // eating server tick time - see the standing "prioritize performance/entity-count" instruction.
    private static final int ARCANE_CONVERGENCE_MAX_CHAIN_DEPTH = 5;
    private static final int COMBO_INTERVAL = 4; // every 4th qualifying hit/shot triggers the bonus

    // Twin Daggers' Rend: a stacking bleed carried entirely on vanilla's
    // own MobEffects.WITHER (harmful, ticks its own periodic damage, and -
    // unlike a hand-rolled UUID-keyed stack map - is stored ON the target
    // entity itself, so it can never leak: it's discarded for free the
    // instant the entity is, same as every other status effect the target
    // might have. amplifier 0..3 = stacks 1..4; each landed dagger hit
    // both refreshes the duration and bumps the amplifier by one (capped),
    // so sustained attacking on one target ramps it to max and holding it
    // there requires staying on that target - walk away and it expires in
    // REND_DURATION_TICKS with no external bookkeeping to clean up.
    private static final int REND_MAX_AMPLIFIER = 3;
    private static final int REND_DURATION_TICKS = 80; // 4s

    // War Halberd's Sunder: same self-cleaning stacking-effect pattern as
    // Rend above, just riding Emberfall's own custom ModEffects.SUNDER
    // (armor/toughness shred - see its javadoc) instead of vanilla WITHER.
    // Capped at 2 stacks (vs Rend's 4) - Sunder's payoff is making the
    // *cleave* itself (and every subsequent hit while it holds) chew
    // through armored elites faster, not a per-tick damage number, so it
    // doesn't need as many amplifier steps to matter.

    /**
     * Verified against the actual 1.21.11 LivingEntity.hurtServer bytecode: vanilla only
     * fully refreshes a target's hit-invulnerability window (and applies full damage) when
     * invulnerableTime has decayed to <= 10 ticks; while it's above that, a same-or-lower
     * amount is dropped entirely and even a bigger amount only applies the excess over
     * lastHurt. That window (0.5s) is meant to stop unrelated overlapping damage sources
     * from double-dipping - it is NOT meant to fight our own auto-attack system, which
     * already gates every swing on the weapon's own attackStrengthScale cooldown. Zeroing
     * invulnerableTime right before a landed swing makes each of OUR swings count as a
     * fresh hit for full, correct damage, without touching how other damage sources
     * (fire, fall, other mobs) interact with the target.
     */
    static void clearInvulnerabilityWindow(LivingEntity target) {
        target.invulnerableTime = 0;
    }
    static final int SUNDER_MAX_AMPLIFIER = 1;
    static final int SUNDER_DURATION_TICKS = 100; // 5s - halberd swings are slow, needs to outlast the gap

    // Bleeding Edge's bonus-swing-multiplier growth per current Rend stack, and its stack-2
    // lowered combo threshold (every 3rd landed hit instead of every 4th).
    private static final float BLEEDING_EDGE_BONUS_PER_REND_STACK = 0.25F;
    private static final int BLEEDING_EDGE_LOWERED_INTERVAL = 3;
    /** Bleeding Edge 3rd copy - radius an Empowered kill spreads the dying target's Rend
     * stacks to the nearest still-living hostile within. */
    private static final double BLEEDING_EDGE_BURST_RADIUS = 4.0;
    // Sundering Wake's cleave-radius growth per prior Sunder stack, and its stack-2 extra cleave target cap.
    private static final double SUNDERING_WAKE_RADIUS_PER_STACK = 0.75;
    // Steady Hand's Empowered Strike bonus (on top of the primary hit's own damage), and
    // stack 2's added self-buff duration.
    private static final float STEADY_HAND_BONUS_MULTIPLIER = 0.5F; // +50% of the primary hit's damage
    private static final int STEADY_HAND_STRENGTH_DURATION_TICKS = 60; // 3s Strength I, stack 2 only

    private AutoAttackSystem() {}

    public static void tickAll(MinecraftServer server) {
        for (ServerLevel level : com.solme.emberfall.world.RunManager.activeLevels()) {
            for (ServerPlayer player : level.players()) {
                // Only players actually inside a run: an in-place run shares its dimension with everyone else.
                if (com.solme.emberfall.world.RunManager.slotOf(player) != null) {
                    tickPlayer(level, player);
                }
            }
        }
    }

    private static void tickPlayer(ServerLevel level, ServerPlayer player) {
        if (player.isSpectator() || player.isDeadOrDying()) {
            return;
        }
        Loadout loadout = Loadout.peek(player);
        if (loadout == null || loadout.isEmpty()) {
            return;
        }
        long now = level.getGameTime();
        ItemStack shown = player.getMainHandItem().copy(); // slot 0's item, restored after every swing
        try {
            for (int i = 0; i < loadout.size(); i++) {
                Loadout.Slot slot = loadout.slot(i);
                if (now < slot.nextReadyTick()) {
                    continue; // this weapon is still on its own cooldown
                }
                fireSlot(level, player, loadout, i, slot, now);
            }
        } finally {
            // Whatever a swing did to the hand, the player is left holding exactly what they were.
            player.setItemSlot(EquipmentSlot.MAINHAND, shown);
        }
    }

    /**
     * The reach of {@code weapon} for this player: the live interaction-range attribute (which carries the character bonus and any
     * gear or tome reach) with the reach of the item that was ALREADY in the hand swapped for this weapon's own. The attribute cannot
     * simply be read after {@code setItemSlot}: a held item's attribute modifiers are applied by the entity's equipment tick, not
     * instantly, so right after the swap it still reports the PREVIOUS item's reach. Measured in weapon_growth_test: with a Halberd
     * (3.5) in slot 0 and a Hunting Bow (10) in slot 1, the bow slot read attr=3.5, so every ranged weapon after the first slot fired
     * at the first weapon's reach. Each weapon item contributes exactly {@code range - 3.0} (see ModItems), so the swap is exact.
     *
     * @param previouslyHeld the stack that was in the main hand when this tick began (slot 0's item)
     */
    static double weaponReach(ServerPlayer player, WeaponType weapon, ItemStack previouslyHeld) {
        double live = player.getAttributeValue(Attributes.ENTITY_INTERACTION_RANGE);
        WeaponType heldWeapon = null;
        for (WeaponType w : com.solme.emberfall.item.WeaponPool.ALL) {
            if (ModItems.itemFor(w) == previouslyHeld.getItem()) {
                heldWeapon = w;
                break;
            }
        }
        double heldContribution = heldWeapon == null ? 0.0 : heldWeapon.range() - 3.0;
        double reach = live - heldContribution + (weapon.range() - 3.0);
        if ("twin_daggers".equals(weapon.id())) {
            // The daggers' level widens their reach (3.0 to 5.0). The slot being fired is the one whose level counts.
            reach += DaggerSystem.reachBonus(com.solme.emberfall.item.Loadout.levelOfWeapon(player, "twin_daggers"));
        }
        if ("hunting_bow".equals(weapon.id())) {
            // The bow's level stretches its range (10 to 13). The slot being fired is the one whose level counts.
            reach += BowSystem.rangeBonus(com.solme.emberfall.item.Loadout.levelOfWeapon(player, "hunting_bow"));
        }
        if ("ashen_beacon".equals(weapon.id())) {
            // The beacon's level stretches its range (8 to 11). The slot being fired is the one whose level counts.
            reach += BeaconSystem.rangeBonus(com.solme.emberfall.item.Loadout.levelOfWeapon(player, "ashen_beacon"));
        }
        if ("gravechain".equals(weapon.id())) {
            // The chain's level stretches its reach (4.5 to 7.0). The slot being fired is the one whose level counts.
            reach += ChainSystem.rangeBonus(com.solme.emberfall.item.Loadout.levelOfWeapon(player, "gravechain"));
        }
        if ("arcane_staff".equals(weapon.id())) {
            // The staff's level stretches its range (9 to 11). The slot being fired is the one whose level counts.
            reach += StaffSystem.rangeBonus(com.solme.emberfall.item.Loadout.levelOfWeapon(player, "arcane_staff"));
        }
        return reach;
    }

    /**
     * Fires one weapon slot. The slot's item is put in the main hand for the duration of the swing, so vanilla's
     * own attack code reads that weapon's damage, speed and reach, and every bonus stacks exactly as it did
     * when there was only one weapon. Streak, Steady Hand and Grave Anchor state are routed to this slot.
     */
    private static void fireSlot(ServerLevel level, ServerPlayer player, Loadout loadout, int index,
                                 Loadout.Slot slot, long now) {
        WeaponType weapon = slot.weapon();
        WeaponMoveset moveset = weapon.moveset();
        // What the hand holds RIGHT NOW (the previous slot's weapon, or the player's own item): its reach is what the live
        // attribute still reports until the entity's next equipment tick. See weaponReach.
        ItemStack heldBeforeSwap = player.getMainHandItem();
        player.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(ModItems.itemFor(weapon)));
        loadout.beginDispatch(index);
        try {
            // Single source of truth for reach: the live attribute (ModItems bakes each weapon's
            // WeaponType#range into it as a modifier), so gear and buffs that change reach still apply.
            double range = weaponReach(player, weapon, heldBeforeSwap);
            LivingEntity target = findNearestHostile(level, player.position(), range);
            if (target == null) {
                PlayerWeapon.resetStreak(player);
                return; // nothing in reach: stays ready, so it fires the first tick something is
            }
            // Cadence: this weapon's own delay, read from the live attack speed while its item is held.
            int delay = Math.max(1, Math.round(player.getCurrentItemAttackStrengthDelay()));
            slot.setNextReadyTick(now + delay);

            // Measured around the swing so the indicator below shows only when something was really hurt.
            final float healthBeforeSwing = target.getHealth();
            final int streakBeforeSwing = PlayerWeapon.peekStreak(player);
            // Named as the acting weapon for the whole swing, so a kill made anywhere inside it (the vanilla hit, a cleave
            // victim, a nova, a bolt impact) is credited to THIS slot by WeaponKills.
            Loadout.acting(player, index, () -> {
            switch (moveset) {
                case MELEE_SINGLE -> meleeSingleWeapon(level, player, target);
                case MELEE_DUAL -> meleeDual(level, player, target);
                case MELEE_CLEAVE -> meleeCleave(level, player, target, range);
                case RANGED_SINGLE -> rangedSingle(level, player, target, weapon);
                case RANGED_AOE -> rangedAoe(level, player, target, weapon);
                case MELEE_HOOK -> meleeHook(level, player, target);
                case TOTEM -> {
                    // "One active at a time" (see WeaponMoveset#TOTEM javadoc) - while a main beacon
                    // is still live, this weapon's own cadence firing again is a no-op rather than
                    // yanking the existing beacon away and re-planting every single cycle.
                    if (!TotemWeaponSystem.hasMainBeaconActive(player.getUUID())) {
                        TotemWeaponSystem.deploy(level, player, target.position(), weapon);
                    }
                }
                case ORBIT -> { /* Handled entirely by OrbitWeaponSystem's own unconditional tick loop. */ }
            }
            });
            swingIndicator(level, player, target, moveset, range, healthBeforeSwing, streakBeforeSwing);
        } finally {
            loadout.endDispatch();
        }
    }

    /**
     * Draws the weapon's own swing shape, but only if the swing really damaged the target (its health dropped, or
     * it died). A miss shows nothing, so the particle is also a "you hit it" confirmation. The "empowered" flag is
     * read from the streak counter: it was reset to 0 by the swing that just consumed the combo, so a drop from a
     * high streak to a low one marks the empowered hit.
     */
    private static void swingIndicator(ServerLevel level, ServerPlayer player, LivingEntity target, WeaponMoveset moveset,
                                       double range, float healthBefore, int streakBefore) {
        boolean hurt = !target.isAlive() || target.getHealth() < healthBefore;
        if (!hurt) {
            return;
        }
        boolean empowered = streakBefore >= COMBO_INTERVAL - 1 && PlayerWeapon.peekStreak(player) < streakBefore;
        switch (moveset) {
            case MELEE_SINGLE -> WeaponFx.broadswordSwing(level, player, target, range, empowered);
            case MELEE_DUAL -> WeaponFx.daggerSlash(level, player, target, range, empowered);
            case MELEE_CLEAVE -> WeaponFx.halberdChop(level, player, target, range);
            case MELEE_HOOK -> WeaponFx.chainLine(level, player, target);
            default -> { /* ranged, totem and orbit weapons already show their own projectile or effect */ }
        }
    }

    /**
     * The Broadsword's level sweeps and ultimate (see {@link BroadswordSystem}), run after its plain hit. {@code damageDealt} is what the
     * hit really did, so crits, strength and every tome flow into the splash. A hit that did nothing (a miss, an immune target) does not
     * sweep, and the splash is never based on a zero.
     */
    private static void broadswordGrowth(ServerLevel level, ServerPlayer player, LivingEntity target, float damageDealt) {
        com.solme.emberfall.item.Loadout.Slot acting = com.solme.emberfall.item.Loadout.actingFor(player);
        if (damageDealt > 0.0F && acting != null && "broadsword".equals(acting.weapon().id())) {
            BroadswordSystem.afterHit(level, player, target, player.getAttributeValue(Attributes.ENTITY_INTERACTION_RANGE), damageDealt);
        }
    }

    private static void meleeSingle(ServerPlayer player, LivingEntity target) {
        float healthBefore = target.getHealth();
        clearInvulnerabilityWindow(target);
        player.attack(target);
        float damageDealt = Math.max(0.0F, healthBefore - target.getHealth());
        OnHitEffects.apply(player, target, damageDealt);
    }

    /**
     * Broadsword's own moveset is deliberately the plain vanilla hit with no built-in
     * hook (see WeaponMoveset#MELEE_SINGLE) - so unlike the other 4 weapons' Tomes,
     * which all branch off something the base moveset already tracks (Rend/Sunder
     * stacks, the existing homing/Nova cadence), Steady Hand has nothing to branch off
     * and instead originates its own player-side mechanic from scratch: a consecutive-
     * landed-hit streak (the same shared {@link PlayerWeapon} counter Twin Daggers/
     * Arcane Staff already use for their own "every 4th" cadence, naturally free for
     * Broadsword to use too since a player only ever holds one weapon at a time).
     * Every 4th landed hit is an Empowered Strike: a transient {@link #STEADY_HAND_BONUS_MULTIPLIER}
     * ATTACK_DAMAGE modifier is applied for that one swing only, so the bonus is baked into
     * the single vanilla hit rather than dealt as a separate follow-up hurtServer call - a
     * same-tick second hit on the same target was tried first and silently dealt 0 damage,
     * eaten by vanilla's own post-hit invulnerability window (any subsequent hit whose
     * amount is <= the just-recorded lastHurt is ignored outright, and a fractional bonus
     * always is - confirmed live via [SteadyHandTest] debug logging before this fix).
     * Named "Steady Hand" rather than "Momentum" to avoid colliding with the unrelated
     * pre-existing MOMENTUM SynergyTag (Swift Boots/Momentum Core/Sprinter's Edge's
     * move-speed family).
     * Stack 2 additionally grants a brief self Strength I on every Empowered Strike,
     * rewarding staying on the offensive with a real window of general damage-up
     * rather than a bigger one-off number - the "steady, dependable weapon that
     * rewards commitment" identity fitting Broadsword's baseline role.
     * Stack 3 gives the streak itself a payoff instead of just the hit: an Empowered
     * Strike that kills its target arms a one-shot flag (see {@link PlayerWeapon#armSteadyHand})
     * that forces the player's very next landed hit to be Empowered too, no 4-hit
     * rebuild - rewards using the burst to finish a target rather than always dumping
     * it into the beefiest thing on screen, and keeps paying out through a horde
     * instead of needing 4 fresh hits per kill. Deliberately NOT implemented by pre-
     * setting the streak counter itself to 3 - confirmed live that a genuine drought
     * (no target in range, the near-universal state between one kill and the next
     * target coming into range) resets that counter on every target-less tick, wiping
     * the value before it could ever be spent. The separate flag survives that gap.
     * Without the Tome, this is byte-for-byte the original plain {@link #meleeSingle}
     * behavior - no streak bookkeeping happens at all, so an un-upgraded Broadsword
     * costs nothing extra.
     */
    private static void meleeSingleWeapon(ServerLevel level, ServerPlayer player, LivingEntity target) {
        int steadyHandTier = CombatStats.of(player).steadyHandTier;
        if (steadyHandTier == 0) {
            float hpBefore = target.getHealth();
            meleeSingle(player, target);
            broadswordGrowth(level, player, target, Math.max(0.0F, hpBefore - target.getHealth()));
            return;
        }

        boolean forcedByArm = steadyHandTier >= 3 && PlayerWeapon.consumeSteadyHandArmed(player);
        boolean empowered;
        if (forcedByArm) {
            empowered = true;
            PlayerWeapon.resetStreak(player);
        } else {
            int streak = PlayerWeapon.incrementStreak(player);
            empowered = streak >= COMBO_INTERVAL;
            if (empowered) {
                PlayerWeapon.resetStreak(player);
            }
        }

        AttributeInstance damageAttr = empowered ? player.getAttribute(Attributes.ATTACK_DAMAGE) : null;
        Identifier modifierId = null;
        if (damageAttr != null) {
            modifierId = Identifier.fromNamespaceAndPath(EmberfallMod.MOD_ID, "steady_hand_empowered_" + player.getUUID());
            damageAttr.addTransientModifier(new AttributeModifier(modifierId, STEADY_HAND_BONUS_MULTIPLIER,
                    AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL));
        }

        float healthBefore = target.getHealth();
        clearInvulnerabilityWindow(target);
        player.attack(target);
        float damageDealt = Math.max(0.0F, healthBefore - target.getHealth());
        OnHitEffects.apply(player, target, damageDealt);

        if (damageAttr != null) {
            damageAttr.removeModifier(modifierId);
        }
        broadswordGrowth(level, player, target, damageDealt);

        if (empowered && damageDealt > 0.0F) {
            level.playSound(null, target.getX(), target.getY(), target.getZ(),
                    SoundEvents.PLAYER_ATTACK_STRONG, SoundSource.PLAYERS, 0.8F, 1.1F);
            level.sendParticles(ParticleTypes.CRIT, target.getX(), target.getY() + 1.0, target.getZ(),
                    12, 0.3, 0.4, 0.3, 0.05);
            if (steadyHandTier >= 2) {
                player.addEffect(new MobEffectInstance(MobEffects.STRENGTH, STEADY_HAND_STRENGTH_DURATION_TICKS, 0, false, true, true));
            }
            if (steadyHandTier >= 3 && !target.isAlive()) {
                PlayerWeapon.armSteadyHand(player);
                level.sendParticles(ParticleTypes.END_ROD, player.getX(), player.getY() + 1.0, player.getZ(),
                        10, 0.3, 0.4, 0.3, 0.05);
            }
        }
    }

    /**
     * Twin Daggers - "Rend": every landed hit stacks a Wither-based bleed on the target
     * (see REND_MAX_AMPLIFIER's javadoc). Every 4th landed hit additionally lands as an
     * Empowered swing carrying a flat +100% ATTACK_DAMAGE bonus for that one hit only -
     * NOT a second, separate {@code player.attack(target)} call. An actual second same-
     * tick hit on the same target was the original design and was tried first; it silently
     * dealt 0 bonus damage every time, eaten by vanilla's post-hit invulnerability window
     * (a subsequent hit whose amount is <= the just-recorded lastHurt is ignored outright,
     * and a same-target follow-up hit of equal or lesser power always is) - see
     * meleeSingleWeapon's javadoc for the identical root cause found on Steady Hand.
     * Folding the "free extra strike" into one boosted swing instead preserves the exact
     * same total damage contribution (hits 1-3 at 1x + hit 4 at 2x = 5x over 4 cycles,
     * identical to 4 normal hits + 1 genuinely-landing free hit) without the dead code path.
     * The Empowered swing still counts as landing Rend TWICE (once for itself, once for the
     * "free hit" it represents) so a full combo still climbs Rend stacks faster than steady
     * single hits, exactly as originally intended.
     *
     * Bleeding Edge (Tome): reads the target's Rend stacks BUILT UP BY EARLIER hits (before
     * this hit's own applyRend() call below overwrites it) to grow the Empowered swing's
     * bonus multiplier further - a dagger main-hand rewards a target that's already bleeding
     * heavily. Stack 2 additionally lowers the combo threshold from every 4th landed hit to
     * every 3rd, so Empowered swings come around more often. Stack 3 ("Rend Burst"): an
     * Empowered swing that kills its target spreads whatever Rend stacks that target had
     * onto the nearest surviving hostile within BLEEDING_EDGE_BURST_RADIUS - a dagger
     * finisher on a heavily-bled target passes that bleed straight to the next foe instead
     * of the stacks just being wasted on a kill. Deliberately its own self-contained radius
     * check here rather than reusing Steady Hand's re-arm-flag pattern: that mechanic pays
     * off the PLAYER's next hit regardless of target, this one pays off a DIFFERENT target
     * immediately, so there's no equivalent "gap" for a flag to need to survive.
     */
    private static void meleeDual(ServerLevel level, ServerPlayer player, LivingEntity target) {
        int bleedingEdgeTier = CombatStats.of(player).bleedingEdgeTier;
        int comboInterval = bleedingEdgeTier >= 2 ? BLEEDING_EDGE_LOWERED_INTERVAL : COMBO_INTERVAL;

        MobEffectInstance priorRend = target.getEffect(MobEffects.WITHER);
        int priorRendAmplifier = priorRend != null ? priorRend.getAmplifier() : -1;

        int streak = PlayerWeapon.incrementStreak(player);
        boolean empowered = streak >= comboInterval;
        if (empowered) {
            PlayerWeapon.resetStreak(player);
        }

        AttributeInstance damageAttr = empowered ? player.getAttribute(Attributes.ATTACK_DAMAGE) : null;
        Identifier modifierId = null;
        if (damageAttr != null) {
            float bonusMultiplier = 1.0F; // +100% baseline - folds in the "free extra strike"
            if (bleedingEdgeTier >= 1 && priorRendAmplifier >= 0) {
                bonusMultiplier += BLEEDING_EDGE_BONUS_PER_REND_STACK * (priorRendAmplifier + 1);
            }
            modifierId = Identifier.fromNamespaceAndPath(EmberfallMod.MOD_ID, "twin_daggers_empowered_" + player.getUUID());
            damageAttr.addTransientModifier(new AttributeModifier(modifierId, bonusMultiplier, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL));
        }

        int rendAmplifierBeforeThisHit = priorRendAmplifier;
        final float hpBeforeDagger = target.getHealth();
        meleeSingle(player, target);
        final float daggerDamage = Math.max(0.0F, hpBeforeDagger - target.getHealth());
        applyRend(target);
        if (empowered) {
            applyRend(target);
        }

        if (damageAttr != null) {
            damageAttr.removeModifier(modifierId);
        }

        if (bleedingEdgeTier >= 3 && empowered && !target.isAlive() && rendAmplifierBeforeThisHit >= 0) {
            burstRendToNearest(level, player, target, rendAmplifierBeforeThisHit);
        }
        // Levels: the flurry hops to foes near the target and the Blade Dance fires when the meter is full. The empowered hit is
        // doubled by the modifier above, so the base for the follow-ups is the plain hit (half of it when empowered).
        DaggerSystem.afterSwing(level, player, target, empowered ? daggerDamage * 0.5F : daggerDamage, meleeReach(player));
    }

    /** The acting dagger's current reach, for the follow-ups' search radius. */
    private static double meleeReach(ServerPlayer player) {
        return player.getAttributeValue(Attributes.ENTITY_INTERACTION_RANGE);
    }

    /** Bleeding Edge 3rd copy payoff: hands the dying target's Rend amplifier to the nearest
     * other living hostile within {@link #BLEEDING_EDGE_BURST_RADIUS}, refreshing its
     * duration too - see {@link #meleeDual}'s javadoc. */
    private static void burstRendToNearest(ServerLevel level, ServerPlayer player, LivingEntity deadTarget, int amplifier) {
        AABB box = new AABB(deadTarget.position(), deadTarget.position()).inflate(BLEEDING_EDGE_BURST_RADIUS);
        List<Mob> candidates = level.getEntitiesOfClass(Mob.class, box,
                mob -> mob != deadTarget && mob.isAlive() && isTargetable(mob));
        candidates.stream()
                .min(Comparator.comparingDouble(mob -> mob.position().distanceTo(deadTarget.position())))
                .ifPresent(nearest -> {
                    MobEffectInstance current = nearest.getEffect(MobEffects.WITHER);
                    int nextAmplifier = Math.max(amplifier, current != null ? current.getAmplifier() : 0);
                    nearest.addEffect(new MobEffectInstance(MobEffects.WITHER, REND_DURATION_TICKS, nextAmplifier));
                    level.sendParticles(ParticleTypes.CRIMSON_SPORE, nearest.getX(), nearest.getY() + 1.0, nearest.getZ(),
                            10, 0.3, 0.4, 0.3, 0.02);
                });
    }

    /** Refreshes/stacks Rend on a live target; no-ops harmlessly if it died from the hit that triggered it. */
    static void applyRend(LivingEntity target) {
        if (!target.isAlive()) {
            return;
        }
        MobEffectInstance current = target.getEffect(MobEffects.WITHER);
        int nextAmplifier = current != null ? Math.min(current.getAmplifier() + 1, REND_MAX_AMPLIFIER) : 0;
        target.addEffect(new MobEffectInstance(MobEffects.WITHER, REND_DURATION_TICKS, nextAmplifier));
    }

    /**
     * War Halberd: normal hit on the primary target, then the exact same measured damage
     * splashed onto other hostiles within cleave radius of the primary target's position -
     * a splash-around-the-hit-point cleave. Every landed hit (primary or cleaved) also
     * stacks "Sunder" (see {@link #applySunder}). Unlike Steady Hand/Bleeding Edge above,
     * the cleave splash hits are on DIFFERENT entities than the primary target, so there's
     * no same-target invulnerability conflict here - a genuine extra hurtServer call per
     * cleaved target is safe and correct as originally designed.
     *
     * Sundering Wake (Tome): read the target's Sunder stacks BUILT UP BY EARLIER hits
     * before this hit's own applySunder() call below overwrites it - so the radius/target-
     * count bonus always reflects "how sundered was this target already", not the stack
     * this very swing is about to add. Stack 3 ("Shattering Blow"): killing a target that
     * was already at max Sunder shatters its armor outward - every hostile within this
     * swing's cleave radius (not just the ones actually cleaved this swing, since
     * maxCleaveTargets caps damage hits, not a stack-application shockwave) is instantly
     * hit with max Sunder. Rewards fully committing the halberd's slow swings to one
     * target through to the kill rather than spreading Sunder thin across a pack.
     */
    private static void meleeCleave(ServerLevel level, ServerPlayer player, LivingEntity target, double range) {
        float healthBefore = target.getHealth();
        clearInvulnerabilityWindow(target);
        player.attack(target);
        float primaryDamage = Math.max(0.0F, healthBefore - target.getHealth());
        OnHitEffects.apply(player, target, primaryDamage);
        if (primaryDamage <= 0.0F) {
            return;
        }

        int sunderingWakeTier = CombatStats.of(player).sunderingWakeTier;
        MobEffectInstance priorSunder = target.getEffect(ModEffects.SUNDER);
        int priorSunderAmplifier = priorSunder != null ? priorSunder.getAmplifier() : -1;

        applySunder(target);

        double cleaveRadiusBonus = (sunderingWakeTier >= 1 && priorSunderAmplifier >= 0)
                ? (priorSunderAmplifier + 1) * SUNDERING_WAKE_RADIUS_PER_STACK
                : 0.0;
        // The halberd's level widens the base cleave (the Sundering Wake bonus above still adds on top).
        com.solme.emberfall.item.Loadout.Slot actingSlot = com.solme.emberfall.item.Loadout.actingFor(player);
        final boolean isHalberd = actingSlot != null && "war_halberd".equals(actingSlot.weapon().id());
        final int halberdLevel = isHalberd ? com.solme.emberfall.item.WeaponProgress.levelOf(player) : 1;
        final double cleaveRadius = (isHalberd ? HalberdSystem.cleaveRadius(halberdLevel) : CLEAVE_RADIUS) + cleaveRadiusBonus;
        // Show the real cleave reach (it grows with Sundering Wake stacks) so the player can see who is in range.
        Fx.impactRing(level, target.position(), cleaveRadius, ParticleTypes.CRIT);
        int baseCleaveTargets = isHalberd ? HalberdSystem.cleaveCount(halberdLevel, levelRandom(level)) : 2;
        int maxCleaveTargets = (sunderingWakeTier >= 2 && priorSunderAmplifier >= SUNDER_MAX_AMPLIFIER) ? baseCleaveTargets + 1 : baseCleaveTargets;

        AABB box = new AABB(target.position(), target.position()).inflate(cleaveRadius);
        List<Mob> others = level.getEntitiesOfClass(Mob.class, box,
                mob -> mob != target && mob.isAlive() && isTargetable(mob)
                        && mob.position().distanceTo(target.position()) <= cleaveRadius
                        && mob.position().distanceTo(player.position()) <= range + cleaveRadius);
        others.sort(Comparator.comparingDouble(mob -> mob.position().distanceTo(target.position())));

        int cleaved = 0;
        for (Mob other : others) {
            if (cleaved >= maxCleaveTargets) {
                break;
            }
            float otherHealthBefore = other.getHealth();
            clearInvulnerabilityWindow(other);
            other.hurtServer(level, player.damageSources().playerAttack(player), primaryDamage);
            float otherDamage = Math.max(0.0F, otherHealthBefore - other.getHealth());
            OnHitEffects.apply(player, other, otherDamage);
            if (otherDamage > 0.0F) {
                applySunder(other);
            }
            cleaved++;
        }

        if (sunderingWakeTier >= 3 && !target.isAlive() && priorSunderAmplifier >= SUNDER_MAX_AMPLIFIER) {
            shatterSunderNearby(level, target, cleaveRadius);
        }
        if (isHalberd && target instanceof Mob centre) {
            HalberdSystem.afterCleave(level, player, centre, primaryDamage);
        }
    }

    /** The level's random source as a plain {@link java.util.random.RandomGenerator} for the fractional-count helper. */
    static java.util.random.RandomGenerator levelRandom(ServerLevel level) {
        net.minecraft.util.RandomSource mc = level.getRandom();
        return new java.util.random.RandomGenerator() {
            @Override public long nextLong() { return mc.nextLong(); }
            @Override public double nextDouble() { return mc.nextDouble(); }
        };
    }

    /** Sundering Wake 3rd copy payoff: instantly applies max Sunder to every other living
     * hostile within {@code radius} of the just-killed target - see {@link #meleeCleave}'s
     * javadoc. Not a damage hit, so it isn't gated by maxCleaveTargets. */
    private static void shatterSunderNearby(ServerLevel level, LivingEntity deadTarget, double radius) {
        AABB box = new AABB(deadTarget.position(), deadTarget.position()).inflate(radius);
        List<Mob> nearby = level.getEntitiesOfClass(Mob.class, box,
                mob -> mob != deadTarget && mob.isAlive() && isTargetable(mob));
        for (Mob mob : nearby) {
            mob.addEffect(new MobEffectInstance(ModEffects.SUNDER, SUNDER_DURATION_TICKS, SUNDER_MAX_AMPLIFIER));
            level.sendParticles(ParticleTypes.CRIT, mob.getX(), mob.getY() + 1.0, mob.getZ(),
                    8, 0.3, 0.4, 0.3, 0.02);
        }
    }

    /** Refreshes/stacks Sunder on a live target; mirrors {@link #applyRend} but on ModEffects.SUNDER. */
    private static void applySunder(LivingEntity target) {
        if (!target.isAlive()) {
            return;
        }
        MobEffectInstance current = target.getEffect(ModEffects.SUNDER);
        int nextAmplifier = current != null ? Math.min(current.getAmplifier() + 1, SUNDER_MAX_AMPLIFIER) : 0;
        target.addEffect(new MobEffectInstance(ModEffects.SUNDER, SUNDER_DURATION_TICKS, nextAmplifier));
    }

    /**
     * Hunting Bow - "Piercing Volley" (inspired by SlopPack's Golden
     * Shortbow "Golden Volley" piercing beam, not ported verbatim): shots
     * 1-3 are a normal single-target bolt exactly as before. The 4th shot
     * skips the single-target hit entirely and instead resolves as an
     * instant line-trace straight through the original target and beyond
     * (out to the weapon's own range), hitting every live hostile within
     * PIERCE_RADIUS of that line - a real "shoot through the crowd" payoff
     * for a weapon that otherwise had no distinct identity beyond "ranged".
     * Uses the same COMBO_INTERVAL cadence as Twin Daggers' free strike and
     * Arcane Staff's Nova, and the same instant-AABB-sweep shape as
     * {@link #detonateNova} - a line capsule instead of a sphere - rather
     * than teaching {@link TrackedProjectiles} a new multi-hit bolt type it
     * doesn't otherwise need.
     */
    private static final double HOOK_PULL_STRENGTH = 0.65;
    private static final double HOOK_GATHER_PULL_STRENGTH = 0.9;
    private static final double HOOK_GATHER_RADIUS = 6.0;
    private static final int HOOK_GATHER_MAX_TARGETS = 4;
    private static final int GRAVE_ANCHOR_LOWERED_INTERVAL = 3;
    private static final int GRAVE_ANCHOR_ROOT_DURATION_TICKS = 30; // 1.5s
    private static final int GRAVE_ANCHOR_ROOT_AMPLIFIER = 6; // Slowness VII - reads as "rooted", not just slowed

    /**
     * Gravechain - a genuinely different identity from every other melee moveset
     * (design goal: fresh roguelike weapons, not more damage variants): the landed
     * hit itself is plain vanilla (identical to {@link #meleeSingle}), but every
     * landed hit also yanks the target horizontally toward the wielder with a small
     * upward lift (so friction/ground contact doesn't just eat the impulse) - a
     * gap-closer for kiting ranged Corrupted archetypes, not a bigger number.
     * Every 4th landed hit (same shared {@link PlayerWeapon} streak counter Twin
     * Daggers/Arcane Staff/Steady Hand all use for their own "every 4th" cadence)
     * additionally drags up to {@link #HOOK_GATHER_MAX_TARGETS} nearby hostiles
     * within {@link #HOOK_GATHER_RADIUS} into a pile at the primary target's
     * position - crowd control setup, not a bonus hit.
     */
    private static void meleeHook(ServerLevel level, ServerPlayer player, LivingEntity target) {
        float healthBefore = target.getHealth();
        clearInvulnerabilityWindow(target);
        player.attack(target);
        float damageDealt = Math.max(0.0F, healthBefore - target.getHealth());
        OnHitEffects.apply(player, target, damageDealt);
        if (damageDealt <= 0.0F) {
            PlayerWeapon.resetStreak(player);
            return;
        }

        pullToward(target, player.position(), HOOK_PULL_STRENGTH);
        level.playSound(null, target.getX(), target.getY(), target.getZ(),
                SoundEvents.TRIDENT_HIT, SoundSource.PLAYERS, 0.7F, 0.8F);
        // Levels (ChainSystem): meter, extra chains at other foes, and the Grave Vortex when the meter is full.
        ChainSystem.afterHit(level, player, target, damageDealt,
                4.5 + ChainSystem.rangeBonus(com.solme.emberfall.item.WeaponProgress.levelOf(player)));
        level.sendParticles(ParticleTypes.SOUL, target.getX(), target.getY() + 1.0, target.getZ(),
                8, 0.2, 0.3, 0.2, 0.02);

        // Grave Anchor (Tome): merges with Gravechain's own every-4th-hit Gather rather than
        // originating a new hook, same pattern Bleeding Edge/Sundering Wake use for their own
        // weapon Tomes. Tier 2 lowers the threshold to every 3rd hit (identical pattern to
        // Bleeding Edge's BLEEDING_EDGE_LOWERED_INTERVAL). Tier 3 ("Anchor Snap"): a kill on
        // any mob still inside the pile's root window - the anchor target or anything it
        // pulled in - snaps the chain down harder, refreshing the root on everyone else
        // still standing in that same pile instead of letting it tick down normally.
        int graveAnchorTier = CombatStats.of(player).graveAnchorTier;
        if (graveAnchorTier >= 3 && !target.isAlive()) {
            snapPileRootOnKill(level, player, target);
        }

        int gatherInterval = graveAnchorTier >= 2 ? GRAVE_ANCHOR_LOWERED_INTERVAL : COMBO_INTERVAL;

        int streak = PlayerWeapon.incrementStreak(player);
        if (streak >= gatherInterval) {
            PlayerWeapon.resetStreak(player);
            List<Mob> piled = gatherNearby(level, target, graveAnchorTier >= 1, com.solme.emberfall.item.WeaponProgress.levelOf(player));
            if (graveAnchorTier >= 3) {
                List<UUID> members = new ArrayList<>(piled.size() + 1);
                members.add(target.getUUID());
                for (Mob mob : piled) {
                    members.add(mob.getUUID());
                }
                PlayerWeapon.recordGraveAnchorPile(player, members, level.getGameTime() + GRAVE_ANCHOR_ROOT_DURATION_TICKS);
            }
        }
    }

    /**
     * Grave Anchor tier 3 ("Anchor Snap"): {@code killedMob} just died on a landed
     * Gravechain hit. If it's still within an active pile's root window (recorded by
     * {@link PlayerWeapon#recordGraveAnchorPile}), every other still-alive member of that
     * same pile gets its Slowness/root refreshed back to full duration - resolved through
     * {@code ServerLevel#getEntity(UUID)} rather than held direct references, since a
     * piled mob can die or despawn independently between the Gather and this kill.
     */
    private static void snapPileRootOnKill(ServerLevel level, ServerPlayer player, LivingEntity killedMob) {
        List<UUID> pile = PlayerWeapon.getGraveAnchorPile(player, level.getGameTime());
        if (pile.isEmpty() || !pile.contains(killedMob.getUUID())) {
            return;
        }
        int refreshed = 0;
        for (UUID id : pile) {
            if (id.equals(killedMob.getUUID())) {
                continue;
            }
            Entity entity = level.getEntity(id);
            if (entity instanceof Mob mob && mob.isAlive()) {
                mob.addEffect(new MobEffectInstance(MobEffects.SLOWNESS,
                        GRAVE_ANCHOR_ROOT_DURATION_TICKS, GRAVE_ANCHOR_ROOT_AMPLIFIER, false, true, true));
                level.sendParticles(ParticleTypes.SOUL, mob.getX(), mob.getY() + 1.0, mob.getZ(),
                        10, 0.25, 0.35, 0.25, 0.03);
                refreshed++;
            }
        }
        if (refreshed > 0) {
            level.playSound(null, killedMob.getX(), killedMob.getY(), killedMob.getZ(),
                    SoundEvents.EVOKER_CAST_SPELL, SoundSource.PLAYERS, 1.0F, 0.4F);
        }
    }

    /** Horizontal-only pull impulse toward {@code destination}, plus a small lift so ground friction doesn't eat it. */
    static void pullToward(LivingEntity mob, Vec3 destination, double strength) {
        Vec3 dir = destination.subtract(mob.position());
        Vec3 flat = new Vec3(dir.x, 0.0, dir.z);
        if (flat.lengthSqr() < 0.0001) {
            return;
        }
        Vec3 n = flat.normalize().scale(strength);
        mob.setDeltaMovement(mob.getDeltaMovement().add(n.x, 0.12, n.z));
    }

    /**
     * Gravechain's "every Nth hit" payoff: drags nearby hostiles into a pile at
     * {@code target}'s position. {@code root} (Grave Anchor Tome tier 1+) additionally
     * applies a brief near-total Slowness to every mob caught in the pull (and the
     * primary target itself), so the gather actually buys a usable window rather than
     * the pile immediately scattering again.
     */
    private static List<Mob> gatherNearby(ServerLevel level, LivingEntity target, boolean root, int chainLevel) {
        Vec3 gatherPoint = target.position();
        // The pile grows with the chain's level: radius 6 to 9, catches 4 to 8 (ChainSystem). The slot is acting while this runs.
        final double gatherRadius = ChainSystem.gatherRadius(chainLevel);
        AABB box = AABB.ofSize(gatherPoint, gatherRadius * 2, gatherRadius * 2, gatherRadius * 2);
        List<Mob> nearby = level.getEntitiesOfClass(Mob.class, box,
                mob -> mob != target && mob.isAlive() && isTargetable(mob)
                        && mob.position().distanceTo(gatherPoint) <= gatherRadius);
        nearby.sort(Comparator.comparingDouble(mob -> mob.position().distanceToSqr(gatherPoint)));

        int count = Math.min(nearby.size(), ChainSystem.gatherCap(chainLevel, level));
        List<Mob> piled = nearby.subList(0, count);
        for (Mob mob : piled) {
            pullToward(mob, gatherPoint, HOOK_GATHER_PULL_STRENGTH);
            level.sendParticles(ParticleTypes.SOUL, mob.getX(), mob.getY() + 1.0, mob.getZ(),
                    6, 0.2, 0.3, 0.2, 0.02);
        }
        if (root) {
            for (Mob mob : piled) {
                mob.addEffect(new MobEffectInstance(MobEffects.SLOWNESS,
                        GRAVE_ANCHOR_ROOT_DURATION_TICKS, GRAVE_ANCHOR_ROOT_AMPLIFIER, false, true, true));
            }
            target.addEffect(new MobEffectInstance(MobEffects.SLOWNESS,
                    GRAVE_ANCHOR_ROOT_DURATION_TICKS, GRAVE_ANCHOR_ROOT_AMPLIFIER, false, true, true));
        }
        if (count > 0) {
            level.playSound(null, gatherPoint.x, gatherPoint.y, gatherPoint.z,
                    SoundEvents.EVOKER_CAST_SPELL, SoundSource.PLAYERS, 1.0F, 0.6F);
        }
        return new ArrayList<>(piled);
    }

    private static void rangedSingle(ServerLevel level, ServerPlayer player, LivingEntity target, WeaponType weapon) {
        // Unlike meleeSingle/meleeCleave/meleeDual, nothing here routes through vanilla
        // Player#attack(), which is what normally calls resetAttackStrengthTicker() - without
        // this, getAttackStrengthScale(0) latches at 1.0 forever after the first windup and
        // tickPlayer's cooldown gate stops gating at all, firing a real shot every server tick
        // (20/s) instead of once per attack-speed cycle. Found via live [PierceTest] debug
        // logging during Piercing Volley verification - pierce was triggering ~4 times/sec.
        player.resetAttackStrengthTicker();
        int streak = PlayerWeapon.incrementStreak(player);
        boolean pierce = streak >= COMBO_INTERVAL;
        if (pierce) {
            PlayerWeapon.resetStreak(player);
        }
        // Hunter's Instinct (Tome, WEAPON category) stack 1: normal (non-pierce) shots steer
        // toward their target in flight instead of flying dead straight, so a target that
        // moves after the shot leaves the string no longer dodges it for free. Never applies
        // to the pierce shot itself here - that shot's own homing (stack 2) is a different,
        // wider-cone behavior handled inside piercingVolley, not steering the travelling bolt.
        boolean homing = !pierce && CombatStats.of(player).huntingBowHomingTier >= 1;
        // Levels (BowSystem): the level is read NOW, while the bow is the acting slot, and reused by the later impact.
        final int bowLevel = com.solme.emberfall.item.WeaponProgress.levelOf(player);
        final double bowReach = weapon.range() + BowSystem.rangeBonus(bowLevel);
        if (!pierce) {
            BowSystem.fireExtras(level, player, target, weapon.rangedDamage(), bowReach, bowLevel);
        }
        fireBolt(level, player, target, weapon, homing, impactTarget -> {
            if (pierce) {
                piercingVolley(level, player, impactTarget, weapon, 0);
            } else {
                float healthBefore = impactTarget.getHealth();
                clearInvulnerabilityWindow(impactTarget);
                impactTarget.hurtServer(level, player.damageSources().playerAttack(player), weapon.rangedDamage());
                float damageDealt = Math.max(0.0F, healthBefore - impactTarget.getHealth());
                OnHitEffects.apply(player, impactTarget, damageDealt);
                StaffSystem.burst(level, player, impactTarget, BowSystem.splashRadius(bowLevel),
                        weapon.rangedDamage() * (float) BowSystem.SPLASH_MULTIPLE, ParticleTypes.CRIT);
                BowSystem.afterImpact(level, player, impactTarget, damageDealt, bowReach);
            }
        });
    }

    private static final int HUNTERS_INSTINCT_CHAIN_MAX_DEPTH = 1;

    /**
     * Instant piercing-beam resolve for Hunting Bow's Piercing Volley - see {@link #rangedSingle}.
     *
     * @param chainDepth 0 for the volley fired directly from a landed 4th shot; 1 for the single
     *                    bonus re-volley Hunter's Instinct stack 3 ("Relentless Volley") grants when
     *                    a volley wipes out every hostile it pierced - capped at
     *                    {@link #HUNTERS_INSTINCT_CHAIN_MAX_DEPTH} so a lucky clear can't cascade
     *                    across the whole arena, same guard shape as {@link #detonateNova}'s
     *                    chainDepth param for Arcane Convergence.
     */
    private static void piercingVolley(ServerLevel level, ServerPlayer player, LivingEntity firstTarget, WeaponType weapon, int chainDepth) {
        Vec3 origin = player.getEyePosition();
        Vec3 dir = firstTarget.getEyePosition().subtract(origin).normalize();
        double reach = weapon.range();

        level.playSound(null, origin.x, origin.y, origin.z, SoundEvents.ARROW_SHOOT, SoundSource.PLAYERS, 1.0F, 0.7F);
        for (double d = 0; d <= reach; d += 0.75) {
            Vec3 p = origin.add(dir.scale(d));
            level.sendParticles(ParticleTypes.END_ROD, p.x, p.y, p.z, 1, 0.02, 0.02, 0.02, 0.0);
        }

        boolean seeking = CombatStats.of(player).huntingBowHomingTier >= 2;

        AABB box = new AABB(origin, origin).inflate(reach);
        List<Mob> pierced = level.getEntitiesOfClass(Mob.class, box, mob -> {
            if (!mob.isAlive() || !isTargetable(mob)) {
                return false;
            }
            // Line is anchored on eye-to-eye positions (origin is the player's eye, dir points at
            // firstTarget's eye), but mob.position() is feet - comparing feet against an eye-height
            // line put every mob ~1.5-1.8 blocks (its own eye height) off "its own" line, so even
            // firstTarget itself usually failed the PIERCE_RADIUS=1.2 check. Use each candidate's own
            // body-center point (same point used below for the impact particles) for both the
            // along-beam projection and the perpendicular-distance check instead.
            Vec3 bodyCenter = mob.position().add(0, mob.getBbHeight() * 0.5, 0);
            Vec3 toMob = bodyCenter.subtract(origin);
            double along = toMob.dot(dir);
            if (along <= 0 || along > reach) {
                return false;
            }
            if (distanceToLine(bodyCenter, origin, dir) <= BowSystem.pierceRadius(com.solme.emberfall.item.WeaponProgress.levelOf(player))) {
                return true;
            }
            // Hunter's Instinct stack 2: the volley "bends" to also catch targets that are
            // close to the line but not strictly on it - a wider radius gated by an angle
            // check (toMob within SEEKING_MAX_ANGLE_COS of dir) so it still reads as one
            // forward beam curving to a nearby enemy, not an omnidirectional AOE in disguise.
            if (!seeking) {
                return false;
            }
            double toMobLength = toMob.length();
            if (toMobLength <= 0.0001) {
                return false;
            }
            double cosAngle = toMob.dot(dir) / toMobLength;
            return cosAngle >= SEEKING_MIN_ANGLE_COS && distanceToLine(bodyCenter, origin, dir) <= SEEKING_PIERCE_RADIUS;
        });

        for (Mob mob : pierced) {
            Vec3 impactPos = mob.position().add(0, mob.getBbHeight() * 0.5, 0);
            level.sendParticles(ParticleTypes.CRIT, impactPos.x, impactPos.y, impactPos.z, 6, 0.2, 0.2, 0.2, 0.0);
            float healthBefore = mob.getHealth();
            clearInvulnerabilityWindow(mob);
            mob.hurtServer(level, player.damageSources().playerAttack(player), weapon.rangedDamage());
            float damageDealt = Math.max(0.0F, healthBefore - mob.getHealth());
            OnHitEffects.apply(player, mob, damageDealt);
        }

        // Hunter's Instinct stack 3 ("Relentless Volley"): a volley that wipes out every hostile
        // it pierced immediately re-fires at the next nearest surviving hostile in range - one
        // bonus volley per original shot (chainDepth guard), not an unbounded cascade.
        if (CombatStats.of(player).huntingBowHomingTier >= 3 && chainDepth < HUNTERS_INSTINCT_CHAIN_MAX_DEPTH
                && !pierced.isEmpty() && pierced.stream().noneMatch(Mob::isAlive)) {
            AABB nextBox = new AABB(player.position(), player.position()).inflate(reach);
            List<Mob> nextTargets = level.getEntitiesOfClass(Mob.class, nextBox,
                    mob -> mob.isAlive() && isTargetable(mob)
                            && mob.position().distanceTo(player.position()) <= reach);
            nextTargets.stream()
                    .min(Comparator.comparingDouble(mob -> mob.position().distanceToSqr(player.position())))
                    .ifPresent(nextTarget -> piercingVolley(level, player, nextTarget, weapon, chainDepth + 1));
        }
    }

    /** Shortest distance from {@code point} to the infinite line through {@code origin} in direction {@code dir} (unit vector). */
    static double distanceToLine(Vec3 point, Vec3 origin, Vec3 dir) {
        Vec3 toPoint = point.subtract(origin);
        double along = toPoint.dot(dir);
        Vec3 closest = origin.add(dir.scale(along));
        return point.distanceTo(closest);
    }

    /** Arcane Staff: same bolt as Hunting Bow, but every 4th shot detonates in an AoE nova instead. */
    private static void rangedAoe(ServerLevel level, ServerPlayer player, LivingEntity target, WeaponType weapon) {
        // Same missing-cooldown-reset bug as rangedSingle - see its comment.
        player.resetAttackStrengthTicker();
        int streak = PlayerWeapon.incrementStreak(player);
        boolean nova = streak >= COMBO_INTERVAL;
        if (nova) {
            PlayerWeapon.resetStreak(player);
        }
        // Levels (StaffSystem): read while the staff is the acting slot, reused by every later impact.
        final int staffLevel = com.solme.emberfall.item.WeaponProgress.levelOf(player);
        final double staffReach = weapon.range() + StaffSystem.rangeBonus(staffLevel);
        final boolean novaShot = nova;
        BoltImpact impact = impactTarget -> {
            if (novaShot) {
                detonateNova(level, player, impactTarget.position(), weapon, 0);
                StaffSystem.afterImpact(level, player, weapon.rangedDamage() * 1.5F, staffReach);
            } else {
                float healthBefore = impactTarget.getHealth();
                clearInvulnerabilityWindow(impactTarget);
                impactTarget.hurtServer(level, player.damageSources().playerAttack(player), weapon.rangedDamage());
                float damageDealt = Math.max(0.0F, healthBefore - impactTarget.getHealth());
                OnHitEffects.apply(player, impactTarget, damageDealt);
                StaffSystem.burst(level, player, impactTarget, StaffSystem.burstRadius(staffLevel),
                        weapon.rangedDamage() * (float) StaffSystem.BURST_MULTIPLE, StaffSystem.TRAIL);
                StaffSystem.afterImpact(level, player, damageDealt, staffReach);
            }
        };
        fireBolt(level, player, target, weapon, false, impact);
        // Extra bolts: each flies at ANOTHER foe and, being a normal landed bolt, can roll its own mini detonation.
        int extra = StaffSystem.extraBolts(staffLevel, level, player);
        if (extra > 0) {
            List<Mob> others = StaffSystem.foesIn(level, player.position(), staffReach, target);
            // Echo: a lone foe is bolted again too (measured: every extra bolt used to go to OTHER foes, so one target never felt the level).
            for (LivingEntity victim : Echo.victims(target, others, extra)) {
                fireBolt(level, player, victim, weapon, false, impact);
            }
        }
    }

    /**
     * @param chainDepth 0 for the primary Nova from a landed shot; 1+ for a secondary
     *                   Nova triggered by an Arcane Convergence seeking spark (see below).
     *                   Each hop shrinks the blast and softens the damage so a chain can't
     *                   out-damage a direct hit, and is normally hard-capped by the Tome's own
     *                   stack count (arcaneConvergenceTier), except for stack 3's kill-fueled
     *                   bonus hop which can push past that count - see the recursion guard below,
     *                   still absolutely capped by {@link #ARCANE_CONVERGENCE_MAX_CHAIN_DEPTH}.
     */
    private static void detonateNova(ServerLevel level, ServerPlayer player, Vec3 loc, WeaponType weapon, int chainDepth) {
        double baseRadius = StaffSystem.novaRadius(com.solme.emberfall.item.WeaponProgress.levelOf(player));
        double radius = chainDepth == 0 ? baseRadius : baseRadius * 0.7;
        float damageMultiplier = chainDepth == 0 ? 1.5F : 0.9F;
        level.playSound(null, loc.x, loc.y, loc.z, SoundEvents.GENERIC_EXPLODE, SoundSource.PLAYERS,
                chainDepth == 0 ? 1.0F : 0.7F, chainDepth == 0 ? 1.3F : 1.6F);
        level.sendParticles(ParticleTypes.EXPLOSION, loc.x, loc.y, loc.z, chainDepth == 0 ? 3 : 2, 0.6, 0.4, 0.6, 0.02);
        // The puff above is small; the ring shows the blast's true radius (chained novas are 30% smaller).
        Fx.impactRing(level, loc, radius, ParticleTypes.WITCH);

        AABB box = AABB.ofSize(loc, radius * 2, radius * 2, radius * 2);
        List<Mob> hit = level.getEntitiesOfClass(Mob.class, box,
                mob -> mob.isAlive() && isTargetable(mob) && mob.position().distanceTo(loc) <= radius);
        for (Mob mob : hit) {
            float healthBefore = mob.getHealth();
            clearInvulnerabilityWindow(mob);
            mob.hurtServer(level, player.damageSources().playerAttack(player), weapon.rangedDamage() * damageMultiplier);
            float damageDealt = Math.max(0.0F, healthBefore - mob.getHealth());
            OnHitEffects.apply(player, mob, damageDealt);
        }

        // Arcane Convergence (Tome): fire seeking sparks at nearby enemies this blast
        // didn't already reach, chaining into a smaller Nova on each one that connects.
        // Depth is gated by the player's own owned stack count (not a fixed constant), so
        // stack 1 = one hop of sparks off the primary Nova, stack 2 = those secondary
        // Novas get one further hop - never runs away regardless of stack count, and the
        // virtual-bolt TrackedProjectiles sparks cost no real entities either way.
        int convergenceTier = CombatStats.of(player).arcaneConvergenceTier;
        boolean stackAllowsHop = convergenceTier > chainDepth;
        // Arcane Convergence stack 3 ("Convergent Ruin"): if this Nova killed at least one
        // enemy, it earns one bonus spark even past the stack's own hop count - a kill-fueled
        // continuation of the chain rather than a fixed extra hop, distinct from stack 2's flat
        // "+1 hop always" behavior. Merges with the seeking-spark mechanic itself rather than
        // adding a separate proc, same pattern every other weapon Tome's 3rd tier follows.
        boolean killFueledHop = convergenceTier >= 3 && !stackAllowsHop
                && hit.stream().anyMatch(mob -> !mob.isAlive());
        if ((stackAllowsHop || killFueledHop) && chainDepth < ARCANE_CONVERGENCE_MAX_CHAIN_DEPTH) {
            List<Mob> hitSet = hit;
            AABB sparkBox = AABB.ofSize(loc, SPARK_SEARCH_RADIUS * 2, SPARK_SEARCH_RADIUS * 2, SPARK_SEARCH_RADIUS * 2);
            List<Mob> sparkTargets = level.getEntitiesOfClass(Mob.class, sparkBox, mob ->
                    mob.isAlive() && isTargetable(mob) && !hitSet.contains(mob)
                            && mob.position().distanceTo(loc) <= SPARK_SEARCH_RADIUS);
            sparkTargets.sort(Comparator.comparingDouble(mob -> mob.position().distanceTo(loc)));
            // A kill-fueled bonus hop only earns a single spark (a small "echo"), not the full
            // per-detonation spark count - the stack-driven hops keep their usual budget.
            int sparkBudget = stackAllowsHop ? SPARK_MAX_COUNT : 1;
            int sparkCount = Math.min(sparkBudget, sparkTargets.size());
            for (int i = 0; i < sparkCount; i++) {
                Mob sparkTarget = sparkTargets.get(i);
                Vec3 dir = sparkTarget.getEyePosition().subtract(loc).normalize().scale(1.4);
                level.sendParticles(ParticleTypes.WITCH, loc.x, loc.y, loc.z, 4, 0.2, 0.2, 0.2, 0.0);
                TrackedProjectiles.launchHoming(level, loc, dir, player, sparkTarget, 0.35, 1.0, 40,
                        ParticleTypes.SOUL_FIRE_FLAME, impactPos ->
                                detonateNova(level, player, impactPos, weapon, chainDepth + 1),
                        () -> {});
            }
        }
    }

    private interface BoltImpact {
        void onImpact(LivingEntity target);
    }

    private static void fireBolt(ServerLevel level, ServerPlayer player, LivingEntity target, WeaponType weapon, boolean homing, BoltImpact rawImpact) {
        // The impact runs on a later tick, outside the swing, so it must re-enter the weapon that fired it (see Loadout#deferred).
        java.util.function.Consumer<LivingEntity> credited = Loadout.deferred(player, rawImpact::onImpact);
        BoltImpact onImpact = credited::accept;
        Vec3 eye = player.getEyePosition();
        Vec3 dir = target.getEyePosition().subtract(eye).normalize().scale(1.6);
        level.playSound(null, eye.x, eye.y, eye.z, SoundEvents.ARROW_SHOOT, SoundSource.PLAYERS, 0.8F, 1.1F);
        final net.minecraft.core.particles.ParticleOptions trail = "arcane_staff".equals(weapon.id()) ? StaffSystem.TRAIL : ParticleTypes.CRIT;
        if (homing) {
            // Same steering math/strength as Blightfeather Marksman's Homing Volley
            // (see TrackedProjectiles/BlightfeatherMarksman) - reused, not reinvented,
            // for Hunter's Instinct stack 1. onExpire deliberately does nothing: a target
            // that dies or is lost mid-flight should just have its bolt vanish, same as
            // the straight-shot path below already does via the isAlive() guard.
            TrackedProjectiles.launchHoming(level, eye, dir, player, target, 0.18, 1.0, 40,
                    trail, impactPos -> {
                        level.sendParticles(ParticleTypes.CRIT, impactPos.x, impactPos.y, impactPos.z, 6, 0.2, 0.2, 0.2, 0.0);
                        onImpact.onImpact(target);
                    }, () -> {});
        } else {
            TrackedProjectiles.launchAtTarget(level, eye, dir, player, target, 1.0, 40,
                    trail, impactPos -> {
                        if (!target.isAlive()) {
                            return;
                        }
                        level.sendParticles(ParticleTypes.CRIT, impactPos.x, impactPos.y, impactPos.z, 6, 0.2, 0.2, 0.2, 0.0);
                        onImpact.onImpact(target);
                    });
        }
    }

    /** Nearest live emberfall-namespaced Mob within range of origin, or null. */
    public static LivingEntity findNearestHostile(ServerLevel level, Vec3 origin, double range) {
        AABB box = new AABB(origin, origin).inflate(range);
        List<Mob> candidates = level.getEntitiesOfClass(Mob.class, box,
                mob -> mob.isAlive() && isTargetable(mob)
                        && mob.position().distanceTo(origin) <= range);
        if (candidates.isEmpty()) {
            return null;
        }
        candidates.sort(Comparator.comparingDouble(mob -> mob.position().distanceTo(origin)));
        return candidates.get(0);
    }

    /**
     * v1: any Mob whose EntityType is registered under the emberfall
     * namespace counts as "ours" to auto-attack. Keeps this system decoupled
     * from any specific enemy class - new enemy types just need to be
     * registered under emberfall: to be picked up automatically.
     */
    /** The scoreboard tag the Devour sets on a mob the Broodtide has swallowed and clears when it spits it out. One marker, no new entity. */
    public static final String SWALLOWED_TAG = "emberfall_swallowed";

    /**
     * Whether a weapon, chain, splash or bot may PICK this mob as a target: {@link #isEmberfallHostile} and not swallowed. A swallowed mob is hidden and invulnerable inside the
     * Broodtide, but it must still COUNT toward the wave cap and the arena containment, so those call sites keep {@link #isEmberfallHostile}. Measured: while one mob was hidden,
     * the visible mob beside it lost 92% less damage, because the halberd kept committing to the hidden one.
     */
    public static boolean isTargetable(Mob mob) {
        return isEmberfallHostile(mob) && !mob.getTags().contains(SWALLOWED_TAG);
    }

    public static boolean isEmberfallHostile(Mob mob) {
        if (mob instanceof com.solme.emberfall.entity.Testificate) {
            return false; // the friendly merchant is in our namespace but is never a target: no weapon swing, chain or splash may pick him
        }
        if (BuiltInRegistries.ENTITY_TYPE.getKey(mob.getType()).getNamespace().equals("emberfall")) {
            return true;
        }
        // Summons are vanilla types (the Umbral Magus's Thrall and Colossus, the Bonecaller's horses and raised dead), so the
        // namespace alone skipped them and no weapon could hurt them. Anything standing in an active run that is not a
        // player and not a tamed companion is on the expedition team, exactly as RunMobTeam decides it.
        return com.solme.emberfall.world.RunMobTeam.isTeamMob(mob);
    }
}
