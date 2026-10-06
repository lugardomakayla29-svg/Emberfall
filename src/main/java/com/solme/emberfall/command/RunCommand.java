package com.solme.emberfall.command;

import com.mojang.brigadier.context.CommandContext;
import com.solme.emberfall.EmberfallMod;
import com.solme.emberfall.wave.WaveDirector;
import com.solme.emberfall.world.ArenaInstance;
import com.solme.emberfall.world.Dimensions;
import com.solme.emberfall.world.MapManager;
import com.solme.emberfall.world.ReturnPoints;
import com.solme.emberfall.world.RunEndHandler;
import com.solme.emberfall.world.RunManager;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;

import java.util.UUID;

/**
 * The real player-facing entry point into an expedition - deliberately a
 * separate top-level command from {@code /emberfall} (whose whole tree is
 * gamemaster-only admin/debug tooling, see {@link EmberfallCommands}), the
 * same pattern {@link ShopCommand} already established for the currency
 * shop. Before this command existed, the only way for a player to get from
 * "logged into the server" to "standing in a live run" was an operator
 * manually running paste/wavestart/join by hand - fine for this session's
 * debugging, not something a real player could ever do themselves.
 *
 * v1 always pastes the single shipped {@code starter_arena} template - a
 * deliberately reused, already-proven-stable structure (it's the same
 * "rendtest" room this session's entire weapon/boss regression suite ran
 * against, just shipped as real mod content instead of a throwaway dev
 * capture) rather than a newly hand-authored space, on purpose: the goal
 * here is proving the *command/run-lifecycle* wiring end-to-end without
 * also introducing fresh map-authoring risk in the same change. Design doc
 * 3.2's full multi-theme, multi-tier arena roster is separate, later work.
 */
public final class RunCommand {
    private static final Identifier STARTER_ARENA = EmberfallMod.id("starter_arena");
    /** Players whose map is being built, so a second /expedition or plate step cannot start a second run. */
    private static final java.util.Set<UUID> BUILDING = new java.util.HashSet<>();

    private RunCommand() {}

    public static void register() {
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) ->
                dispatcher.register(Commands.literal("expedition")
                        // The root stays open to everyone because a child's requirement is AND-ed with its
                        // parent's: gating the root would make "leave" unreachable and trap players in a
                        // run (there is no physical way out). Starting a run is done at the hub's departure
                        // plate now, so the bare typed start checks for operator permission itself.
                        .executes(RunCommand::start)
                        .then(Commands.literal("leave").executes(RunCommand::leave))));
    }

    private static int start(CommandContext<CommandSourceStack> ctx) {
        CommandSourceStack source = ctx.getSource();
        ServerPlayer player = source.getPlayer();
        if (player == null) {
            source.sendFailure(Component.literal("Only a player can start an expedition."));
            return 0;
        }
        if (!Commands.LEVEL_GAMEMASTERS.check(source.permissions())) {
            source.sendFailure(Component.literal("Light an Ember Hearth and step on its departure plate to begin an expedition."));
            return 0;
        }
        String problem = tryStart(player);
        if (problem != null) {
            source.sendFailure(Component.literal(problem));
            return 0;
        }
        source.sendSuccess(() -> Component.literal("Expedition started - good luck."), true);
        return 1;
    }

    /**
     * Starts an in-place expedition for {@code player} where they stand. Shared by {@code /expedition}
     * and the hub's departure ring so both go through exactly the same checks. Returns null on
     * success, otherwise a player-facing sentence explaining why it could not start.
     */
    public static String tryStart(ServerPlayer player) {
        return tryStartFrom(player, Double.NaN, Double.NaN, Double.NaN);
    }

    /**
     * {@link #tryStart} with an explicit return spot (the Expedition Gate's spot beside the gate). NaN means
     * "where the player stands when the map is ready", the behaviour of a typed /expedition.
     */
    public static String tryStartFrom(ServerPlayer player, double backX, double backY, double backZ) {
        if (RunManager.slotOf(player) != null) {
            return "You're already on an expedition. Use /expedition leave first.";
        }
        if (!(player.level() instanceof ServerLevel from)) {
            return "You can't start an expedition from here.";
        }
        if (from.dimension() == Dimensions.EXPEDITION) {
            return "Expeditions start in a normal world. Use /expedition leave first.";
        }
        if (player.isSpectator() || player.isPassenger() || player.isInWater() || player.isInLava()) {
            return "Stand on dry ground (not riding, swimming or spectating) to start an expedition.";
        }
        if (BUILDING.contains(player.getUUID())) {
            return "Your expedition map is still being prepared. One moment.";
        }
        MinecraftServer server = from.getServer();
        ServerLevel expedition = server.getLevel(Dimensions.EXPEDITION);
        if (expedition == null) {
            return "The expedition world is not available.";
        }
        int slot = RunManager.reserveMapSlot();
        boolean needsBuild = !MapManager.isBuilt(slot);
        if (needsBuild) {
            player.sendSystemMessage(Component.literal("\u00A76Preparing the expedition grounds... this takes about half a minute the first time."));
        }
        UUID id = player.getUUID();
        BUILDING.add(id);
        MapManager.ensureBuilt(expedition, slot, RunManager.originForSlot(slot), built -> {
            BUILDING.remove(id);
            ServerPlayer p = server.getPlayerList().getPlayer(id);
            // The player may have quit, died elsewhere or already joined a run while the map was building.
            if (p == null || RunManager.slotOf(p) != null) {
                RunManager.releaseMapSlot(slot);
                return;
            }
            if (Double.isNaN(backX)) {
                ReturnPoints.remember(p);
            } else {
                ReturnPoints.rememberAt(p, backX, backY, backZ);
            }
            ArenaInstance instance = RunManager.startOnMap(expedition, slot);
            RunManager.joinPlayer(expedition, instance, p);
            WaveDirector.start(instance);
        });
        return null;
    }

    /** One member of a gate party and the spot they return to (where they stood when they joined the countdown). */
    public record PartyMember(ServerPlayer player, double backX, double backY, double backZ) {}

    /**
     * Starts ONE expedition for a whole gate party: the map slot is reserved and built once, then every member that is still
     * eligible joins the same run, so {@code RunManager.partySize} is the number of people who actually departed. Each member
     * keeps their own return spot. Members who stopped being eligible while the map built (quit, already in a run, died) are
     * dropped without failing the others. Returns null on success, else a sentence for the first (leader) member.
     */
    public static String tryStartParty(java.util.List<PartyMember> members) {
        if (members.isEmpty()) {
            return "Nobody is at the gate.";
        }
        if (members.size() == 1) {
            PartyMember m = members.get(0);
            return tryStartFrom(m.player(), m.backX(), m.backY(), m.backZ());
        }
        java.util.List<PartyMember> ok = new java.util.ArrayList<>();
        String firstProblem = null;
        for (PartyMember m : members) {
            String why = eligibility(m.player());
            if (why == null) {
                ok.add(m);
            } else if (firstProblem == null) {
                firstProblem = why;
            }
        }
        if (ok.isEmpty()) {
            return firstProblem;
        }
        ServerPlayer leader = ok.get(0).player();
        MinecraftServer server = ((ServerLevel) leader.level()).getServer();
        ServerLevel expedition = server.getLevel(Dimensions.EXPEDITION);
        if (expedition == null) {
            return "The expedition world is not available.";
        }
        int slot = RunManager.reserveMapSlot();
        if (!MapManager.isBuilt(slot)) {
            for (PartyMember m : ok) {
                m.player().sendSystemMessage(Component.literal("\u00A76Preparing the expedition grounds... this takes about half a minute the first time."));
            }
        }
        java.util.List<UUID> ids = new java.util.ArrayList<>();
        for (PartyMember m : ok) {
            ids.add(m.player().getUUID());
            BUILDING.add(m.player().getUUID());
        }
        java.util.Map<UUID, PartyMember> byId = new java.util.HashMap<>();
        for (PartyMember m : ok) {
            byId.put(m.player().getUUID(), m);
        }
        MapManager.ensureBuilt(expedition, slot, RunManager.originForSlot(slot), built -> {
            ids.forEach(BUILDING::remove);
            java.util.List<ServerPlayer> joining = new java.util.ArrayList<>();
            for (UUID id : ids) {
                ServerPlayer p = server.getPlayerList().getPlayer(id);
                if (p != null && p.isAlive() && RunManager.slotOf(p) == null) {
                    joining.add(p);
                }
            }
            if (joining.isEmpty()) {
                RunManager.releaseMapSlot(slot);
                return;
            }
            ArenaInstance instance = RunManager.startOnMap(expedition, slot);
            for (ServerPlayer p : joining) {
                PartyMember m = byId.get(p.getUUID());
                ReturnPoints.rememberAt(p, m.backX(), m.backY(), m.backZ());
                RunManager.joinPlayer(expedition, instance, p);
            }
            WaveDirector.start(instance);
        });
        return null;
    }

    /** The reasons a player cannot depart, shared by the single and party paths. Null = eligible. */
    private static String eligibility(ServerPlayer player) {
        if (RunManager.slotOf(player) != null) {
            return "You're already on an expedition. Use /expedition leave first.";
        }
        if (!(player.level() instanceof ServerLevel from)) {
            return "You can't start an expedition from here.";
        }
        if (from.dimension() == Dimensions.EXPEDITION) {
            return "Expeditions start in a normal world. Use /expedition leave first.";
        }
        if (player.isSpectator() || player.isPassenger() || player.isInWater() || player.isInLava()) {
            return "Stand on dry ground (not riding, swimming or spectating) to start an expedition.";
        }
        if (BUILDING.contains(player.getUUID())) {
            return "Your expedition map is still being prepared. One moment.";
        }
        return null;
    }

    private static int leave(CommandContext<CommandSourceStack> ctx) {
        CommandSourceStack source = ctx.getSource();
        ServerPlayer player = source.getPlayer();
        if (player == null) {
            source.sendFailure(Component.literal("Only a player can leave an expedition."));
            return 0;
        }
        if (RunManager.slotOf(player) == null) {
            source.sendFailure(Component.literal("You're not on an expedition."));
            return 0;
        }

        // RunEndHandler moves a map-run player home (ReturnPoints) before the arena is torn down under them.
        RunEndHandler.handlePlayerLeftRun(player, source.getServer());

        source.sendSuccess(() -> Component.literal("You left the expedition."), true);
        return 1;
    }
}
