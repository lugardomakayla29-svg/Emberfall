package com.solme.emberfall.world;

import com.solme.emberfall.EmberfallMod;
import com.solme.emberfall.progression.RunRewardCalculator;
import com.solme.emberfall.wave.WaveDirector;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;

/**
 * Found via live bot testing (2.4 / 10.1): neither death nor disconnect ever
 * called {@link RunManager#leavePlayer} or tore down that slot's arena, so
 * an abandoned run's Wave Director kept spawning hostiles forever (up to
 * the hard cap) with nobody left to fight them - a real, observed resource
 * leak, not a hypothetical one. Also meant a player's in-run XP snapshot
 * was never restored on death (10.1 requires in-run XP not to persist) -
 * currently masked because vanilla's own death handling already zeroes XP
 * by default, but LevelingHandler.exitRun should still be the one doing
 * that restoration, not an accidental side effect of an unrelated vanilla
 * mechanic.
 *
 * This is intentionally minimal: unregister the player from the run, and if
 * that empties the slot, stop its Wave Director and tear the arena down.
 * The actual death/run-summary screen (Section 11) is a separate, later
 * feature - this only stops the leak.
 *
 * Found via live bot testing (disconnect-and-reconnect case): a player who
 * disconnects mid-run leaves their stored login position inside the
 * expedition dimension. If that was the slot's last player, the arena gets
 * torn down (blocks cleared back to air) while they're offline. Their next
 * login then drops them into what is now an empty void at that same
 * position - a real "fell out of the world" death observed live, which
 * (correctly, but pointlessly punishingly) re-triggers vanilla's own
 * death-XP-loss on a player who did nothing wrong. rescuePlayerIfOrphaned
 * closes this: on join, anyone in the expedition dimension who isn't a
 * currently-recorded member of an active slot (arena torn down, or any
 * other reason their run no longer exists) is relocated to the overworld's
 * spawn before anything else can happen to them.
 */
public final class RunEndHandler {
    private static final boolean TEST_MODE = Boolean.getBoolean("emberfall.testMode");

    private RunEndHandler() {}

    public static void register() {
        // Before vanilla empties the inventory: a run weapon must never land on the ground where anyone could take
        // it, and the item the run displaced has to be back in the inventory so vanilla treats it like any other.
        ServerLivingEntityEvents.ALLOW_DEATH.register((entity, damageSource, amount) -> {
            if (entity instanceof ServerPlayer player && RunManager.slotOf(player) != null) {
                // A run player never really dies: the lethal hit is cancelled, the run ends on the spot and the
                // player is left ALIVE where they fell. No vanilla death screen, no totem-style effect, no
                // dropped inventory, no lost XP. The run-end screen shows what the run earned instead.
                // Totem of Returning (relic): a once-per-run save. The death is cancelled and the run CONTINUES.
                if (com.solme.emberfall.relic.RelicDefenceEvents.tryTotem(player)) {
                    return false;
                }
                endRunInsteadOfDying(player);
                return false;
            }
            return true;
        });

        ServerLivingEntityEvents.AFTER_DEATH.register((entity, damageSource) -> {
            if (entity instanceof ServerPlayer player) {
                handlePlayerLeftRun(player, ((ServerLevel) player.level()).getServer());
            }
        });

        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> {
            // DISCONNECT fires on the Netty IO thread (measured: thread 'Netty Epoll IO #1', isSameThread=false). Ending a run
            // discards entities and restores blocks, which must only happen on the server thread: doing it from Netty can
            // mutate the chunk map while the server thread iterates it (ChunkMap.tick NullPointerException, run-end crash).
            // execute() runs it at once when already on the server thread and queues it otherwise.
            ServerPlayer leaving = handler.getPlayer();
            com.solme.emberfall.bot.BotRoster.remove(leaving.getUUID()); // a bot that left is a plain name again
            com.solme.emberfall.bot.BotBrain.forget(leaving.getUUID());
            com.solme.emberfall.bot.BotPilot.forget(leaving.getUUID());
            com.solme.emberfall.rift.RiftGate.forget(leaving.getUUID());
            com.solme.emberfall.wave.SwarmPortal.forget(leaving.getUUID());
            com.solme.emberfall.network.SwarmHudSync.forget(leaving.getUUID());
            server.execute(() -> handlePlayerLeftRun(leaving, server));
        });

        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> {
            com.solme.emberfall.progression.UpgradeEffects.apply(handler.getPlayer());
            rescuePlayerIfOrphaned(handler.getPlayer(), server);
            if (RunManager.slotOf(handler.getPlayer()) == null) {
                // A run's permanent modifiers are saved with the player but their clean-up is in memory only: strip what a stopped server or a dropped connection left.
                int stale = com.solme.emberfall.tome.PlayerBuild.purgeStale(handler.getPlayer());
                if (stale > 0) {
                    EmberfallMod.LOGGER.info("Removed {} stale run modifier(s) from {} on join", stale, handler.getPlayer().getGameProfile().name());
                }
            }
            if (RunManager.slotOf(handler.getPlayer()) == null) {
                com.solme.emberfall.progression.DisplacedItems.restore(handler.getPlayer()); // a crash mid-run left an item stashed
            }
        });
    }

    private static void rescuePlayerIfOrphaned(ServerPlayer player, MinecraftServer server) {
        if (player == null || player.level().dimension() != com.solme.emberfall.world.Dimensions.EXPEDITION) {
            return;
        }
        if (RunManager.slotOf(player) != null) {
            return; // legitimately mid-run (e.g. a quick relog) - leave them where they are
        }
        ServerLevel overworld = server.getLevel(Level.OVERWORLD);
        if (overworld == null) {
            return;
        }
        BlockPos spawn = overworld.getLevelData().getRespawnData().pos();
        player.teleportTo(overworld, spawn.getX() + 0.5, spawn.getY(), spawn.getZ() + 0.5,
                java.util.Set.of(), player.getYRot(), player.getXRot(), true);
        EmberfallMod.LOGGER.info(
                "Rescued {} from an orphaned expedition-dimension login (no active run) - relocated to overworld spawn",
                player.getGameProfile().name());
    }

    /**
     * Cleanly exits a player from their run: awards the reward, unregisters
     * them, and tears the arena down if that was the last player. Public so
     * {@link com.solme.emberfall.command.RunCommand}'s voluntary
     * "/expedition leave" can reuse the exact same logic death/disconnect
     * already use, instead of a second, easy-to-drift-out-of-sync copy.
     */
    public static void handlePlayerLeftRun(ServerPlayer player, MinecraftServer server) {
        finishRun(player, server, null);
    }

    /** Leaving through the Final Swarm portal: ends the run and shows the run-end screen with cause "escaped". */
    public static void escapeRun(ServerPlayer player, MinecraftServer server) {
        finishRun(player, server, "escaped");
    }

    /** A lethal hit on a run player: heal, end the run, show the run-end screen. Never a real death. */
    private static void endRunInsteadOfDying(ServerPlayer player) {
        MinecraftServer server = ((ServerLevel) player.level()).getServer();
        player.setHealth(player.getMaxHealth());
        player.clearFire();
        player.removeAllEffects();
        player.fallDistance = 0;
        // A short grace so the mob that landed the killing blow cannot finish the player during the screen.
        player.addEffect(new net.minecraft.world.effect.MobEffectInstance(net.minecraft.world.effect.MobEffects.RESISTANCE, 100, 4));
        finishRun(player, server, "fallen");
    }

    /**
     * Ends the run. {@code cause} non-null also opens the run-end screen; null (disconnect, /expedition leave)
     * ends it silently. The screen numbers are read BEFORE leavePlayer/clear wipe them.
     */
    private static void finishRun(ServerPlayer player, MinecraftServer server, String cause) {
        if (player == null || server == null) {
            return;
        }
        Integer slot = RunManager.slotOf(player);
        if (slot == null) {
            return;
        }
        long seconds = RunTelemetry.elapsedSeconds(slot);
        int level = player.experienceLevel;
        int kills = RunStats.kills(player);
        int gold = com.solme.emberfall.pickup.PickupSystem.gold(player);
        boolean hydra = RunTelemetry.wasHydraDefeated(slot);
        boolean devourer = RunTelemetry.wasDevourerDefeated(slot);
        long earned = RunRewardCalculator.awardRunReward(server, player, slot);
        long total = com.solme.emberfall.progression.MetaProgressionData.get(server).getBalance(player.getUUID());
        com.solme.emberfall.pickup.PickupSystem.clear(player); // gold is per run and never carries over
        com.solme.emberfall.network.HudSync.hide(player); // the panel is a run-only display
        com.solme.emberfall.network.RunHudSync.hide(player);
        RunManager.leavePlayer(player);
        com.solme.emberfall.progression.DisplacedItems.restore(player); // weapons out, the player's own item back
        // A map run happens in the expedition dimension: move the player home BEFORE the arena is torn down under them.
        ReturnPoints.sendBack(player, server);
        com.solme.emberfall.rift.RiftGate.runEnded(player, server.getTickCount());   // the Rift refuses for a few seconds
        if (!RunManager.hasAnyPlayers(slot)) {
            ArenaInstance instance = RunManager.getActive(slot);
            WaveDirector.stop(slot);
            if (instance != null) {
                com.solme.emberfall.pickup.PickupSystem.clearLevel(instance.level());
                com.solme.emberfall.entity.AcidPools.clearLevel(instance.level()); // no puddle outlives its run
                com.solme.emberfall.combat.PyreLantern.clearAll(); // no lantern outlives its run
                com.solme.emberfall.entity.PinkPools.clearLevel(instance.level());
                RunManager.teardownArena(server, instance);
                EmberfallMod.LOGGER.info("Slot {} abandoned (last player left) - arena torn down", slot);
            }
        }
        if (cause != null) {
            net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking.send(player, new com.solme.emberfall.network.RunEndPayload(
                    cause, (int) Math.min(Integer.MAX_VALUE, seconds), level, kills, gold, hydra, devourer, earned, total));
        }
        if (TEST_MODE) {
            // server truth for the EmberTester run-end check: what the screen was told, and what was left of the run afterwards
            EmberfallMod.LOGGER.info("RUNEND_TEST player={} cause={} screen={} slotAfter={} goldAfter={} earned={} total={}",
                    player.getGameProfile().name(), cause, cause != null, RunManager.slotOf(player),
                    com.solme.emberfall.pickup.PickupSystem.gold(player), earned, total);
        }
    }
}
