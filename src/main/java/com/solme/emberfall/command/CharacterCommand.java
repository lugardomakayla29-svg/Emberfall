package com.solme.emberfall.command;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.solme.emberfall.character.CharacterPool;
import com.solme.emberfall.character.CharacterType;
import com.solme.emberfall.character.PlayerCharacterSelection;
import com.solme.emberfall.world.RunManager;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

import java.util.List;

/**
 * Player-facing Character picker (design doc Section 5) - a separate
 * top-level command from {@code /emberfall}, same pattern as
 * {@link RunCommand}/{@link ShopCommand}. {@code /expedition} reads
 * whatever this command last selected (or {@link CharacterPool#fallback()}
 * if nothing was ever picked) at the moment a run starts - changing your
 * selection mid-run does nothing until your next expedition, matching how
 * a Character's weapon/stat spread is a "start of run" choice everywhere
 * else in the design doc.
 */
public final class CharacterCommand {
    private CharacterCommand() {}

    public static void register() {
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) ->
                dispatcher.register(Commands.literal("character")
                        .requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
                        .executes(CharacterCommand::list)
                        .then(Commands.literal("list").executes(CharacterCommand::list))
                        .then(Commands.literal("select")
                                .then(Commands.argument("id", StringArgumentType.word())
                                        .executes(CharacterCommand::select)))));
    }

    private static int list(CommandContext<CommandSourceStack> ctx) {
        CommandSourceStack source = ctx.getSource();
        ServerPlayer player = source.getPlayer();
        List<CharacterType> options = CharacterPool.defaultUnlocked();
        String currentId = null;
        if (player != null) {
            currentId = PlayerCharacterSelection.get(source.getServer()).selectedOrFallback(player.getUUID()).id();
        }
        // One sendSuccess call per line, not one big multi-line string -
        // verified live that a single long \n-joined component gets
        // silently clipped partway through on an actual client (the
        // console-only sender doesn't show it, only a real connected
        // player does), so every line needs its own message to guarantee
        // the full roster actually reaches the player.
        source.sendSuccess(() -> Component.literal("§6Characters:"), false);
        for (CharacterType c : options) {
            boolean isCurrent = c.id().equals(currentId);
            String line = (isCurrent ? "§a> " : "§7  ") + c.displayName() + " §7(" + c.id() + ") - "
                    + c.description() + " " + c.passiveDescription();
            source.sendSuccess(() -> Component.literal(line), false);
        }
        source.sendSuccess(() -> Component.literal("§7Use /character select <id> to choose."), false);
        return 1;
    }

    private static int select(CommandContext<CommandSourceStack> ctx) {
        CommandSourceStack source = ctx.getSource();
        ServerPlayer player = source.getPlayer();
        if (player == null) {
            source.sendFailure(Component.literal("Only a player can select a character."));
            return 0;
        }
        String error = trySelect(player, StringArgumentType.getString(ctx, "id"));
        if (error != null) {
            source.sendFailure(Component.literal(error));
            return 0;
        }
        return 1;
    }

    /**
     * Shared selection path for {@code /character select} and clicking a hub bust, so the rules
     * (unknown/locked id, no changing mid-expedition) live in exactly one place.
     * Returns null on success (and tells the player), or a plain-text reason on failure.
     */
    public static String trySelect(ServerPlayer player, String id) {
        CharacterType character = CharacterPool.byId(id);
        if (character == null || !character.unlockedByDefault()) {
            return "Unknown or locked character id '" + id + "'.";
        }
        if (RunManager.slotOf(player) != null) {
            return "Can't change character mid-expedition - it'll apply next time you start one.";
        }
        PlayerCharacterSelection.get(player.level().getServer()).select(player.getUUID(), character.id());
        player.sendSystemMessage(Component.literal("§aSelected " + character.displayName()
                + " §7- your next expedition starts with them."));
        return null;
    }
}
