package com.solme.emberfall.client.mixin;

import com.mojang.authlib.properties.Property;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.core.ClientAsset;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.PlayerModelType;
import net.minecraft.world.entity.player.PlayerSkin;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Gives an EmberTester one of the skins bundled in the mod. A vanilla client only fetches skins from Mojang's own domains, so a
 * hosted skin cannot work; the server instead tags the bot's profile with the skin NUMBER and this mixin turns it into a bundled
 * texture, built the same way vanilla's DefaultPlayerSkin builds Steve. A profile without the tag (every real player) is untouched.
 */
@Mixin(PlayerInfo.class)
public abstract class BotSkinMixin {
    private static final int SKIN_COUNT = 10; // keep in step with BotSkins.COUNT; BotSkinsCheck proves that many files ship
    private static final String SKIN_PROPERTY = "emberfall_bot_skin"; // keep in step with EmberBot.SKIN_PROPERTY (main source set)

    @Inject(method = "getSkin", at = @At("HEAD"), cancellable = true)
    private void emberfall$botSkin(CallbackInfoReturnable<PlayerSkin> cir) {
        var profile = ((PlayerInfo) (Object) this).getProfile();
        var tagged = profile.properties().get(SKIN_PROPERTY);
        if (tagged.isEmpty()) {
            return;
        }
        Property p = tagged.iterator().next();
        int n;
        try {
            n = Integer.parseInt(p.value());
        } catch (NumberFormatException e) {
            return; // a malformed tag falls back to the normal skin rather than crashing a render
        }
        if (n < 1 || n > SKIN_COUNT) {
            return;
        }
        ClientAsset.Texture body = new ClientAsset.ResourceTexture(Identifier.fromNamespaceAndPath("emberfall", "entity/bot/skin" + n));
        cir.setReturnValue(PlayerSkin.insecure(body, null, null, PlayerModelType.WIDE));
    }
}
