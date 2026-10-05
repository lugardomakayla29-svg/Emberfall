package com.solme.emberfall.network;

import com.solme.emberfall.item.WeaponChoiceManager;
import com.solme.emberfall.progression.ShopManager;
import com.solme.emberfall.tome.TomeChoiceManager;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;

/**
 * Registers the Tome Choice and Weapon Choice screens' custom payloads and
 * the server-side handlers for each player's pick. Payload type+codec
 * registration is common (both sides need to en/decode it); each S2C
 * payload's receiver lives client-side (EmberfallModClient).
 */
public final class EmberfallNetworking {
    private EmberfallNetworking() {}

    public static void registerCommon() {
        PayloadTypeRegistry.playS2C().register(OpenTomeChoicePayload.TYPE, OpenTomeChoicePayload.STREAM_CODEC);
        PayloadTypeRegistry.playC2S().register(ChooseTomePayload.TYPE, ChooseTomePayload.STREAM_CODEC);
        PayloadTypeRegistry.playC2S().register(RerollTomePayload.TYPE, RerollTomePayload.STREAM_CODEC);
        PayloadTypeRegistry.playC2S().register(BanishTomePayload.TYPE, BanishTomePayload.STREAM_CODEC);
        PayloadTypeRegistry.playS2C().register(OpenWeaponChoicePayload.TYPE, OpenWeaponChoicePayload.STREAM_CODEC);
        PayloadTypeRegistry.playC2S().register(ChooseWeaponPayload.TYPE, ChooseWeaponPayload.STREAM_CODEC);
        PayloadTypeRegistry.playS2C().register(OpenCharacterSelectPayload.TYPE, OpenCharacterSelectPayload.STREAM_CODEC);
        PayloadTypeRegistry.playC2S().register(ChooseCharacterPayload.TYPE, ChooseCharacterPayload.STREAM_CODEC);
        PayloadTypeRegistry.playS2C().register(OpenShopPayload.TYPE, OpenShopPayload.STREAM_CODEC);
        PayloadTypeRegistry.playS2C().register(HudStatePayload.TYPE, HudStatePayload.STREAM_CODEC);
        PayloadTypeRegistry.playS2C().register(RunHudPayload.TYPE, RunHudPayload.STREAM_CODEC);
        PayloadTypeRegistry.playS2C().register(RunEndPayload.TYPE, RunEndPayload.STREAM_CODEC);
        PayloadTypeRegistry.playC2S().register(BuyShopItemPayload.TYPE, BuyShopItemPayload.STREAM_CODEC);
        PayloadTypeRegistry.playS2C().register(OpenShrinePayload.TYPE, OpenShrinePayload.STREAM_CODEC);
        PayloadTypeRegistry.playC2S().register(ChooseShrinePayload.TYPE, ChooseShrinePayload.STREAM_CODEC);
        PayloadTypeRegistry.playS2C().register(OpenMerchantPayload.TYPE, OpenMerchantPayload.STREAM_CODEC);
        PayloadTypeRegistry.playS2C().register(SwarmHudPayload.TYPE, SwarmHudPayload.STREAM_CODEC);
        PayloadTypeRegistry.playC2S().register(BuyMerchantItemPayload.TYPE, BuyMerchantItemPayload.STREAM_CODEC);

        ServerPlayNetworking.registerGlobalReceiver(ChooseTomePayload.TYPE,
                (payload, context) -> TomeChoiceManager.onChoiceReceived(context.player(), payload));
        ServerPlayNetworking.registerGlobalReceiver(RerollTomePayload.TYPE,
                (payload, context) -> TomeChoiceManager.onRerollReceived(context.player(), payload));
        ServerPlayNetworking.registerGlobalReceiver(BanishTomePayload.TYPE,
                (payload, context) -> TomeChoiceManager.onBanishReceived(context.player(), payload));
        ServerPlayNetworking.registerGlobalReceiver(ChooseWeaponPayload.TYPE,
                (payload, context) -> WeaponChoiceManager.onChoiceReceived(context.player(), payload));
        ServerPlayNetworking.registerGlobalReceiver(BuyShopItemPayload.TYPE,
                (payload, context) -> ShopManager.onBuyReceived(context.player(), payload));
        ServerPlayNetworking.registerGlobalReceiver(BuyMerchantItemPayload.TYPE,
                (payload, context) -> com.solme.emberfall.relic.MerchantManager.onBuyReceived(context.player(), payload));
        ServerPlayNetworking.registerGlobalReceiver(ChooseCharacterPayload.TYPE,
                (payload, context) -> com.solme.emberfall.character.CharacterSelectManager.onChoiceReceived(context.player(), payload));
    }
}
