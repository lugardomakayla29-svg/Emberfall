package com.solme.emberfall.mixin;

import com.solme.emberfall.bot.BotRoster;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientboundPlayerInfoUpdatePacket;
import net.minecraft.server.network.ServerCommonPacketListenerImpl;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.List;

/**
 * Keeps EmberTester bots off every tab list. A bot is a real ServerPlayer, so vanilla announces it to everyone through
 * six different paths (join, leave, latency ticks, game-mode change, client information, chat session). All of them end
 * in {@code ServerCommonPacketListenerImpl.send(Packet)}, so this one hook covers them all:
 * <ul>
 *   <li>an info UPDATE whose every entry is a bot is dropped;</li>
 *   <li>a mixed UPDATE is rebuilt from the human players only;</li>
 *   <li>an info REMOVE loses the bot UUIDs (and is dropped when nothing is left).</li>
 * </ul>
 * The bot's own listener receives nothing useful anyway (its connection is inert), so it is left alone.
 */
@Mixin(ServerCommonPacketListenerImpl.class)
public abstract class EmberfallTabListMixin {
    @Inject(method = "send(Lnet/minecraft/network/protocol/Packet;)V", at = @At("HEAD"), cancellable = true)
    private void emberfall$hideBots(Packet<?> packet, CallbackInfo ci) {
        if (BotRoster.count() == 0) {
            return; // the common case costs one volatile read
        }
        if ((Object) this instanceof ServerGamePacketListenerImpl own && BotRoster.isBot(own.player.getUUID())) {
            com.solme.emberfall.bot.BotBrain.hear(own.player, packet); // a bot's screens arrive here, see BotBrain
            return;
        }
        if (packet instanceof ClientboundPlayerInfoUpdatePacket update) {
            // A client builds a remote player ONLY if it already holds a PlayerInfo for that id (it otherwise logs "Server attempted to add
            // player prior to sending player info" and creates nothing). So a bot's entry must REACH humans; it is sent unlisted so the tab
            // list (getListedOnlinePlayers) never shows it. The packet object is shared between recipients, so a rewritten COPY is sent.
            var entries = update.entries();
            var rewritten = com.solme.emberfall.bot.BotTabEntries.rewrite(entries,
                    e -> e.listed() && BotRoster.isBot(e.profileId()), // already-unlisted entries are done: this also stops the send() below re-entering forever
                    e -> new ClientboundPlayerInfoUpdatePacket.Entry(e.profileId(), e.profile(), false, e.latency(), e.gameMode(),
                            e.displayName(), e.showHat(), e.listOrder(), e.chatSession()));
            if (rewritten != entries) {
                ci.cancel();
                ClientboundPlayerInfoUpdatePacket copy = new ClientboundPlayerInfoUpdatePacket(update.actions(), java.util.List.of());
                ((InfoUpdateEntriesAccessor) (Object) copy).emberfall$setEntries(rewritten);
                ((ServerCommonPacketListenerImpl) (Object) this).send(copy);
            }
        }
    }
}
