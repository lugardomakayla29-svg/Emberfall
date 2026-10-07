package com.solme.emberfall.mixin;

import java.util.List;
import net.minecraft.network.protocol.game.ClientboundPlayerInfoUpdatePacket;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.gen.Accessor;

/** Lets {@link EmberfallTabListMixin} write the (private, final) entry list of a player-info packet it is about to send to a human. */
@Mixin(ClientboundPlayerInfoUpdatePacket.class)
public interface InfoUpdateEntriesAccessor {
    @Mutable
    @Accessor("entries")
    void emberfall$setEntries(List<ClientboundPlayerInfoUpdatePacket.Entry> entries);
}
