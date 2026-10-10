package com.solme.emberfall.client;

import com.solme.emberfall.network.HudStatePayload;
import com.solme.emberfall.network.RunEndPayload;
import com.solme.emberfall.network.OpenShopPayload;
import com.solme.emberfall.network.OpenTomeChoicePayload;
import com.solme.emberfall.network.OpenWeaponChoicePayload;
import com.solme.emberfall.entity.ModEntities;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.rendering.v1.EntityRendererRegistry;
import net.minecraft.client.renderer.entity.*;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;

public class EmberfallModClient implements ClientModInitializer {
	/**
	 * Every custom entity extends a vanilla mob class, so each one reuses the matching VANILLA renderer
	 * (and therefore vanilla models and textures: no resource pack). The mod's real look comes from
	 * server-side head/equipment/item-display riders, which sync to any client automatically.
	 * Without a renderer an entity type has no model at all on the client, which is what made the
	 * mobs invisible or broken in the first playtest build.
	 */
	@SuppressWarnings({"unchecked", "rawtypes"})
	private static void registerRenderers() {
		// Zombie-based
		EntityRendererRegistry.register((net.minecraft.world.entity.EntityType) ModEntities.HORDE_ZOMBIE, HordeZombieRenderer::new);
		EntityRendererRegistry.register((net.minecraft.world.entity.EntityType) ModEntities.HORDE_CHARGER, ZombieRenderer::new);
		EntityRendererRegistry.register((net.minecraft.world.entity.EntityType) ModEntities.HORDE_SHIELDBEARER, ZombieRenderer::new);
		EntityRendererRegistry.register((net.minecraft.world.entity.EntityType) ModEntities.HORDE_SPITTER, ZombieRenderer::new);
		EntityRendererRegistry.register((net.minecraft.world.entity.EntityType) ModEntities.HORDE_IMP, HordeImpRenderer::new);
		EntityRendererRegistry.register((net.minecraft.world.entity.EntityType) ModEntities.DEVOURER_SPAWN, ZombieRenderer::new);
		EntityRendererRegistry.register((net.minecraft.world.entity.EntityType) ModEntities.CORRUPTED_SENTINEL, ZombieRenderer::new);
		EntityRendererRegistry.register((net.minecraft.world.entity.EntityType) ModEntities.PLAGUE_COLOSSUS, ZombieRenderer::new);
		EntityRendererRegistry.register((net.minecraft.world.entity.EntityType) ModEntities.BONECALLER_NECROMANCER, ZombieVillagerRenderer::new);
		// Villager-based: the Testificate merchant wears the vanilla Nitwit look
		EntityRendererRegistry.register((net.minecraft.world.entity.EntityType) ModEntities.TESTIFICATE, net.minecraft.client.renderer.entity.VillagerRenderer::new);
		// Creeper-based
		EntityRendererRegistry.register((net.minecraft.world.entity.EntityType) ModEntities.HORDE_BOMBER, net.minecraft.client.renderer.entity.CreeperRenderer::new);
		// Witch-based
		EntityRendererRegistry.register((net.minecraft.world.entity.EntityType) ModEntities.HORDE_WITCH, net.minecraft.client.renderer.entity.WitchRenderer::new);
		// Skeleton-based
		EntityRendererRegistry.register((net.minecraft.world.entity.EntityType) ModEntities.HORDE_SKELETON, SkeletonRenderer::new);
		EntityRendererRegistry.register((net.minecraft.world.entity.EntityType) ModEntities.BLIGHTFEATHER_MARKSMAN, SkeletonRenderer::new);
		EntityRendererRegistry.register((net.minecraft.world.entity.EntityType) ModEntities.BOIL_RIDDEN_MARKSMAN, SkeletonRenderer::new);
		// Spider-based
		EntityRendererRegistry.register((net.minecraft.world.entity.EntityType) ModEntities.HORDE_SPIDER, SpiderRenderer::new);
		EntityRendererRegistry.register((net.minecraft.world.entity.EntityType) ModEntities.BROODLING, SpiderRenderer::new);
		EntityRendererRegistry.register((net.minecraft.world.entity.EntityType) ModEntities.BROODMOTHER_STALKER, SpiderRenderer::new);
		// Others
		EntityRendererRegistry.register((net.minecraft.world.entity.EntityType) ModEntities.DEVOURER_BRAIN, SilverfishRenderer::new);
		EntityRendererRegistry.register((net.minecraft.world.entity.EntityType) ModEntities.CINDERBRAND_REAVER, VindicatorRenderer::new);
		EntityRendererRegistry.register((net.minecraft.world.entity.EntityType) ModEntities.UMBRAL_MAGUS, WitchRenderer::new);
		EntityRendererRegistry.register((net.minecraft.world.entity.EntityType) ModEntities.PINK_SLIME, PinkSlimeRenderer::new);
		EntityRendererRegistry.register((net.minecraft.world.entity.EntityType) ModEntities.BROODTIDE, BroodtideRenderer::new);
		EntityRendererRegistry.register((net.minecraft.world.entity.EntityType) ModEntities.TIKI_MAGMA, MagmaCubeRenderer::new);
		EntityRendererRegistry.register((net.minecraft.world.entity.EntityType) ModEntities.TIKI_CUBE, MagmaCubeRenderer::new);
		// Invisible rig parts (ArmorStand-based): the visible model is an item/block display rider, so
		// the stand itself must draw nothing. Reusing the armor stand renderer is fine because the
		// server keeps these entities invisible.
		EntityRendererRegistry.register((net.minecraft.world.entity.EntityType) ModEntities.TIKI_SEGMENT, ArmorStandRenderer::new);
	}

	@Override
	public void onInitializeClient() {
		registerRenderers();
		// Always-on weapon/tome panel, bottom left beside the hotbar. Attached after the health bar so it
		// draws with the other status elements; the server only sends state while the player is in a run.
		HudElementRegistry.attachElementAfter(VanillaHudElements.HEALTH_BAR,
				com.solme.emberfall.EmberfallMod.id("loadout_hud"), new LoadoutHud());
		HudElementRegistry.attachElementAfter(VanillaHudElements.HEALTH_BAR,
				com.solme.emberfall.EmberfallMod.id("run_hud"), new RunHud());
		ClientPlayNetworking.registerGlobalReceiver(com.solme.emberfall.network.RunHudPayload.TYPE,
				(payload, context) -> RunHud.set(payload));
		HudElementRegistry.attachElementAfter(VanillaHudElements.HEALTH_BAR,
				com.solme.emberfall.EmberfallMod.id("swarm_hud"), new SwarmHud());
		ClientPlayNetworking.registerGlobalReceiver(com.solme.emberfall.network.SwarmHudPayload.TYPE,
				(payload, context) -> SwarmHud.set(payload));
		ClientPlayNetworking.registerGlobalReceiver(HudStatePayload.TYPE,
				(payload, context) -> LoadoutHud.set(payload));
		// A static field outlives the connection, so drop the panel state on disconnect or a later server
		// would briefly show the previous run's loadout.
		ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> {
			LoadoutHud.set(HudStatePayload.HIDDEN);
			RunHud.set(com.solme.emberfall.network.RunHudPayload.HIDDEN);
			SwarmHud.set(com.solme.emberfall.network.SwarmHudPayload.HIDDEN);
		});
		// Design doc 7.4: server offers 3 Tome choices on level-up - open the pick screen.
		ClientPlayNetworking.registerGlobalReceiver(OpenTomeChoicePayload.TYPE,
				(payload, context) -> context.client().setScreen(
						new TomeChoiceScreen(payload.newLevel(), payload.offers(),
								payload.rerollsRemaining(), payload.banishesRemaining(),
								payload.goldPrice(), payload.gold())));
		// The run ended (the player never really dies): show what it earned.
		ClientPlayNetworking.registerGlobalReceiver(RunEndPayload.TYPE,
				(payload, context) -> context.client().setScreen(new RunEndScreen(payload)));
		// The Character Table: open the character-selection screen (lore on hover).
		ClientPlayNetworking.registerGlobalReceiver(com.solme.emberfall.network.OpenCharacterSelectPayload.TYPE,
				(payload, context) -> context.client().setScreen(
						new CharacterSelectScreen(payload.currentId(), payload.characters(), payload.selectId(), payload.secondsLeft())));
		// A shrine on the expedition map: left click opens its small window.
		ClientPlayNetworking.registerGlobalReceiver(com.solme.emberfall.network.OpenShrinePayload.TYPE,
				(payload, context) -> context.client().setScreen(new ShrineScreen(payload)));
		// A Testificate's stall. open=true opens it (or refreshes it in place); open=false closes it, but only if THAT is what is showing,
		// so a late packet can never close some other window the player has since opened.
		ClientPlayNetworking.registerGlobalReceiver(com.solme.emberfall.network.OpenMerchantPayload.TYPE,
				(payload, context) -> {
					var current = context.client().screen;
					if (!payload.open()) {
						if (current instanceof MerchantScreen ms) {
							ms.closeFromServer();
						}
					} else if (current instanceof MerchantScreen ms) {
						ms.refresh(payload);
					} else if (current == null) {
						context.client().setScreen(new MerchantScreen(payload));
					}
				});
		// The chest slot-machine. The relic is ALREADY granted when this arrives, so it only shows the answer. If the player is in another window
		// (a shop, a pick) it is not opened over it, and the close id is sent at once so the server's record of it does not linger until its sweep.
		ClientPlayNetworking.registerGlobalReceiver(com.solme.emberfall.network.OpenChestRevealPayload.TYPE,
				(payload, context) -> {
					if (context.client().screen == null) {
						context.client().setScreen(new ChestRevealScreen(payload));
					} else {
						ClientPlayNetworking.send(new com.solme.emberfall.network.CloseChestRevealPayload(payload.revealId()));
					}
				});
		// Weapon system: starting pick before a run, or a mid-run swap offer.
		ClientPlayNetworking.registerGlobalReceiver(OpenWeaponChoicePayload.TYPE,
				(payload, context) -> context.client().setScreen(
						new WeaponChoiceScreen(payload.isStartingPick(), payload.requestId(), payload.offers())));
		// Currency shop: opened via /shop, and re-sent after every buy to refresh the same screen in place.
		// Only replaces the screen if none is open or the shop is already open, so a stray late packet
		// (e.g. the player closed the shop right as a buy response arrived) can't yank open a new screen
		// over whatever they've since navigated to.
		ClientPlayNetworking.registerGlobalReceiver(OpenShopPayload.TYPE,
				(payload, context) -> {
					var current = context.client().screen;
					if (current == null || current instanceof ShopScreen) {
						context.client().setScreen(new ShopScreen(payload.balance(), payload.weapons(), payload.upgrades()));
					}
				});
	}
}
