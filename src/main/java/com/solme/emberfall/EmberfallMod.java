package com.solme.emberfall;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;

import net.minecraft.resources.Identifier;
import net.minecraft.core.registries.BuiltInRegistries;
import net.fabricmc.fabric.api.event.registry.RegistryAttribute;
import net.fabricmc.fabric.api.event.registry.RegistryAttributeHolder;

import com.solme.emberfall.block.ModBlockEntities;
import com.solme.emberfall.block.ModBlocks;
import com.solme.emberfall.boss.DevourerBossFight;
import com.solme.emberfall.boss.GuardianBossFight;
import com.solme.emberfall.combat.CompositeParts;
import com.solme.emberfall.command.EmberfallCommands;
import com.solme.emberfall.command.CharacterCommand;
import com.solme.emberfall.command.RunCommand;
import com.solme.emberfall.command.ShopCommand;
import com.solme.emberfall.entity.ModEntities;
import com.solme.emberfall.entity.ScorchedGround;
import com.solme.emberfall.item.ModItems;
import com.solme.emberfall.item.WeaponChoiceManager;
import com.solme.emberfall.wave.WaveDirector;
import com.solme.emberfall.combat.AutoAttackSystem;
import com.solme.emberfall.combat.ModEffects;
import com.solme.emberfall.leveling.LevelingHandler;
import com.solme.emberfall.network.EmberfallNetworking;
import com.solme.emberfall.tome.TomeChoiceManager;
import com.solme.emberfall.shrine.ShrineManager;
import com.solme.emberfall.world.RunEndHandler;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class EmberfallMod implements ModInitializer {
	public static final String MOD_ID = "emberfall";

	// This logger is used to write text to the console and the log file.
	// It is considered best practice to use your mod id as the logger's name.
	// That way, it's clear which mod wrote info, warnings, and errors.
	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

	@Override
	public void onInitialize() {
		// This code runs as soon as Minecraft is in a mod-load-ready state.
		// However, some things (like resources) may still be uninitialized.
		// Proceed with mild caution.

		ModBlocks.init();
		com.solme.emberfall.music.ModSounds.init();
		ModBlockEntities.init();
		ModEntities.init();
		ModEffects.init();
		ModItems.init();
		com.solme.emberfall.item.ModCreativeTab.init();
		EmberfallCommands.register();
		com.solme.emberfall.command.RelicCommands.register();
		com.solme.emberfall.relic.RelicDefenceEvents.register();
		com.solme.emberfall.relic.RelicHitEvents.register();
		ShopCommand.register();
		RunCommand.register();
		CharacterCommand.register();
		EmberfallNetworking.registerCommon();
		RunEndHandler.register();
		com.solme.emberfall.hub.HubSiteSearch.register();
		com.solme.emberfall.hub.HubInteractions.register();
		com.solme.emberfall.world.RunManager.registerRunEntityTagger();
		com.solme.emberfall.combat.MobPresentation.register();
		com.solme.emberfall.pickup.KillRewards.register();
		com.solme.emberfall.world.RunManager.registerLifecycleSafety();
		CompositeParts.register();
		enableHeadlessTestModeIfRequested();
		ServerTickEvents.END_SERVER_TICK.register(WaveDirector::tickAll);
		ServerTickEvents.END_SERVER_TICK.register(com.solme.emberfall.bot.BotBrain::tickAll);
		ServerTickEvents.END_SERVER_TICK.register(com.solme.emberfall.bot.BotPilot::tickAll);
		ServerTickEvents.END_SERVER_TICK.register(com.solme.emberfall.music.RunMusic::tick);
		ServerTickEvents.END_SERVER_TICK.register(GuardianBossFight::tickAll);
		ServerTickEvents.END_SERVER_TICK.register(com.solme.emberfall.rift.RiftStage::tickAll);
		ServerTickEvents.END_SERVER_TICK.register(com.solme.emberfall.rift.RiftManager::tickAll);
		ServerTickEvents.END_SERVER_TICK.register(com.solme.emberfall.relic.ChestManager::tickReveals);
		ServerTickEvents.END_SERVER_TICK.register(DevourerBossFight::tickAll);
		ServerTickEvents.END_SERVER_TICK.register(AutoAttackSystem::tickAll);
		ServerTickEvents.END_SERVER_TICK.register(LevelingHandler::tickAll);
		ServerTickEvents.END_SERVER_TICK.register(com.solme.emberfall.network.HudSync::tickAll);
		ServerTickEvents.END_SERVER_TICK.register(com.solme.emberfall.network.RunHudSync::tickAll);
		ServerTickEvents.END_SERVER_TICK.register(com.solme.emberfall.world.ArenaBoundary::tick);
		ServerTickEvents.END_SERVER_TICK.register(com.solme.emberfall.world.CircleBoundary::tick);
		com.solme.emberfall.world.RunMobPurge.init();
		com.solme.emberfall.world.DelayedTasks.register();
		com.solme.emberfall.shrine.MapShrines.init(); // before MapProtection: a click on a shrine opens its window first
		com.solme.emberfall.world.MapProtection.init();
		com.solme.emberfall.world.MapManager.init();
		ServerTickEvents.END_SERVER_TICK.register(com.solme.emberfall.world.RunMobPurge::tick);
		ServerTickEvents.END_SERVER_TICK.register(com.solme.emberfall.world.RunMobAggro::tick);
		com.solme.emberfall.world.RunMobTeam.init();
		ServerTickEvents.END_SERVER_TICK.register(com.solme.emberfall.world.RunMobTeam::tick);
		ServerTickEvents.END_SERVER_TICK.register(TomeChoiceManager::tickAll);
		ServerTickEvents.END_SERVER_TICK.register(com.solme.emberfall.relic.RelicRegenSystem::tick);
		ServerTickEvents.END_SERVER_TICK.register(com.solme.emberfall.relic.MerchantManager::tickAll);
		ServerTickEvents.END_SERVER_TICK.register(com.solme.emberfall.wave.SwarmPortal::tickAll);
		ServerTickEvents.END_SERVER_TICK.register(com.solme.emberfall.hub.GateManager::tickAll);
		ServerTickEvents.END_SERVER_TICK.register(com.solme.emberfall.network.SwarmHudSync::tickAll);
		ServerTickEvents.END_SERVER_TICK.register(ShrineManager::tickAll);
		ServerTickEvents.END_SERVER_TICK.register(WeaponChoiceManager::tickAll);
		ServerTickEvents.END_SERVER_TICK.register(ScorchedGround::tickAll);
		ServerTickEvents.END_SERVER_TICK.register(com.solme.emberfall.entity.AcidPools::tickAll);
		ServerTickEvents.END_SERVER_TICK.register(com.solme.emberfall.entity.PinkPools::tickAll);
		ServerTickEvents.END_SERVER_TICK.register(com.solme.emberfall.entity.TrackedProjectiles::tickAll);
		ServerTickEvents.END_SERVER_TICK.register(com.solme.emberfall.entity.SpinBarrageRunner::tickAll);
		ServerTickEvents.END_SERVER_TICK.register(com.solme.emberfall.entity.UmbralMinionRunner::tickAll);
		ServerTickEvents.END_SERVER_TICK.register(com.solme.emberfall.entity.StarBitLob::tickAll);
		ServerTickEvents.END_SERVER_TICK.register(com.solme.emberfall.entity.BonecallerMinionRunner::tickAll);
		ServerTickEvents.END_SERVER_TICK.register(com.solme.emberfall.entity.SmokeCloud::tickAll);
		ServerTickEvents.END_SERVER_TICK.register(com.solme.emberfall.combat.OrbitWeaponSystem::tickAll);
		ServerTickEvents.END_SERVER_TICK.register(com.solme.emberfall.combat.TotemWeaponSystem::tickAll);
		ServerTickEvents.END_SERVER_TICK.register(com.solme.emberfall.combat.PhantomBladeSystem::tickAll);
		ServerTickEvents.END_SERVER_TICK.register(com.solme.emberfall.combat.ReapersRiteSystem::tickAll);
		ServerTickEvents.END_SERVER_TICK.register(com.solme.emberfall.combat.TimedAbilitySystem::tickAll);

		LOGGER.info("Hello Fabric world!");
	}

	/**
	 * ONLY for the sandbox test server (started with -Demberfall.testMode=true),
	 * NEVER for a real deployment: marks our registries OPTIONAL in Fabric's
	 * registry-sync handshake so a plain vanilla/headless client (no Fabric
	 * Loader, no mod) isn't kicked at login. This does NOT make a vanilla
	 * client understand our custom content - it still can't render our
	 * blocks/entities correctly - it only lets a non-rendering test client
	 * (e.g. a Mineflayer bot driving server-side verification via chat/health/
	 * position, which is all vanilla protocol) stay connected long enough to
	 * exercise real gameplay code paths.
	 */
	private static void enableHeadlessTestModeIfRequested() {
		if (!Boolean.getBoolean("emberfall.testMode")) {
			return;
		}
		RegistryAttributeHolder.get(BuiltInRegistries.BLOCK).addAttribute(RegistryAttribute.OPTIONAL);
		RegistryAttributeHolder.get(BuiltInRegistries.ITEM).addAttribute(RegistryAttribute.OPTIONAL);
		RegistryAttributeHolder.get(BuiltInRegistries.ENTITY_TYPE).addAttribute(RegistryAttribute.OPTIONAL);
		RegistryAttributeHolder.get(BuiltInRegistries.BLOCK_ENTITY_TYPE).addAttribute(RegistryAttribute.OPTIONAL);
		RegistryAttributeHolder.get(BuiltInRegistries.MOB_EFFECT).addAttribute(RegistryAttribute.OPTIONAL);
		RegistryAttributeHolder.get(BuiltInRegistries.SOUND_EVENT).addAttribute(RegistryAttribute.OPTIONAL);
		LOGGER.warn("emberfall.testMode=true: registries marked OPTIONAL, unmodded/headless clients CAN connect. "
				+ "This must never be set on a real server - real players still need the actual client mod to see or "
				+ "interact with any of this content correctly.");
	}

	public static Identifier id(String path) {
		return Identifier.fromNamespaceAndPath(MOD_ID, path);
	}
}
