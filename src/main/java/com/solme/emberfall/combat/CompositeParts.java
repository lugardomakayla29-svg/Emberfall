package com.solme.emberfall.combat;

import com.solme.emberfall.EmberfallMod;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;

import java.util.UUID;

/**
 * Design doc 6.1/6.3: the generic half of the composite-entity technique -
 * "mark parts with a custom component/NBT tag pointing back to their
 * brain's UUID so the redirect logic is a simple lookup." Any boss/elite
 * built from multiple riding entities (Section 6.2's Centaur/Hydra/Siege
 * Golem recipes) calls {@link #markAsPart} on every non-brain LivingEntity
 * part; this class's single global damage listener does the rest, for
 * every composite creature in the mod, without each boss reimplementing
 * its own damage redirect.
 *
 * Fabric's data-attachment API (not NBT/PDC) is deliberately used here:
 * this link only needs to exist for the lifetime of one boss fight, never
 * needs to survive a save/reload, and never needs to sync to clients, so a
 * plain non-persistent, non-synced attachment is the leanest correct tool -
 * see AttachmentRegistry.create (no persistenceCodec = transient by default).
 */
public final class CompositeParts {
    private static final AttachmentType<UUID> BRAIN_LINK =
            AttachmentRegistry.create(EmberfallMod.id("composite_brain"));

    private CompositeParts() {}

    /** Marks {@code part} as a non-brain piece of a composite creature whose real HP lives on {@code brain}. */
    public static void markAsPart(LivingEntity part, LivingEntity brain) {
        part.setAttached(BRAIN_LINK, brain.getUUID());
    }

    public static boolean isPart(LivingEntity entity) {
        return entity.hasAttached(BRAIN_LINK);
    }

    /**
     * Removes the brain-link. Callers MUST call this before killing/damaging
     * a part directly through the normal damage pipeline (e.g. via
     * {@code LivingEntity.kill}) for a reason OTHER than the redirect - e.g.
     * a scripted phase event like HydraBrain's head-loss. Without this,
     * {@code kill()}'s own internal damage call would re-enter the ALLOW_DAMAGE
     * listener below and redirect a second, unintended hit back onto the
     * brain (this bit the Hydra rig during testing: a scripted head kill
     * one-shot the brain and crashed the server with a reentrant
     * ConcurrentModificationException from HydraBrain's own bookkeeping).
     */
    public static void unmark(LivingEntity part) {
        part.removeAttached(BRAIN_LINK);
    }

    /** Resolves a marked part back to its brain in the given level, or null if the brain is gone/not loaded. */
    public static LivingEntity brainOf(LivingEntity part, ServerLevel level) {
        UUID brainId = part.getAttached(BRAIN_LINK);
        if (brainId == null) {
            return null;
        }
        return level.getEntity(brainId) instanceof LivingEntity living ? living : null;
    }

    /** Registers the one global redirect listener. Call once at mod init. */
    public static void register() {
        ServerLivingEntityEvents.ALLOW_DAMAGE.register((entity, source, amount) -> {
            if (!isPart(entity)) {
                return true; // not a composite part - normal vanilla damage handling
            }
            if (!(entity.level() instanceof ServerLevel level)) {
                return true;
            }
            LivingEntity brain = brainOf(entity, level);
            if (brain == null || !brain.isAlive()) {
                // Brain already dead/unloaded - let the stray part take the hit itself
                // rather than silently no-op the damage (e.g. lets it be cleaned up normally).
                return true;
            }
            // A composite creature must never be able to damage itself via
            // its own parts - observed live during testing: a Ravager brain
            // that's stuck pathing performs its vanilla roar/charge, which
            // is an AOE that hits its own mounted anchors/heads (always in
            // range - they're riding it); redirecting that straight back to
            // the same brain made it kill its own heads and itself with no
            // player involved at all. Any hit whose responsible entity is
            // the brain itself, or another part of this same composite, is
            // voided outright instead of redirected.
            if (source.getEntity() instanceof LivingEntity attacker
                    && (attacker == brain || (isPart(attacker) && brainOf(attacker, level) == brain))) {
                return false;
            }
            if (brain != entity) {
                brain.hurtServer(level, source, amount);
            }
            return false; // cancel damage on the part itself - it has no real HP pool
        });
    }
}
