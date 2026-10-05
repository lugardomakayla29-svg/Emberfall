package com.solme.emberfall.command;

import com.solme.emberfall.progression.ShopManager;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.server.level.ServerPlayer;

/**
 * The real player-facing entry point into the currency shop - deliberately
 * a separate top-level command from {@code /emberfall} (whose whole tree
 * requires gamemaster permission for admin/debug testing - see
 * {@link EmberfallCommands}) since any player should be able to open their
 * own shop, any time, in the hub or mid-run (design decision from the
 * owner's clarification: the shop must be reachable mid-run too).
 */
public final class ShopCommand {
    private ShopCommand() {}

    public static void register() {
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> {
            dispatcher.register(Commands.literal("shop")
                        .requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
                    .executes(ShopCommand::openOwnShop));
        });
    }

    private static int openOwnShop(com.mojang.brigadier.context.CommandContext<CommandSourceStack> ctx) {
        CommandSourceStack source = ctx.getSource();
        ServerPlayer player = source.getPlayer();
        if (player == null) {
            source.sendFailure(net.minecraft.network.chat.Component.literal("Only a player can open the shop."));
            return 0;
        }
        ShopManager.open(player);
        return 1;
    }
}
