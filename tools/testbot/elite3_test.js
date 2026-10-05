const mineflayer = require('mineflayer');
const bot = mineflayer.createBot({ host: '127.0.0.1', port: 25565, username: 'EmberTester', version: '1.21.11', auth: 'offline' });
const t0 = Date.now();
const log = (m) => console.log(`[${((Date.now()-t0)/1000).toFixed(1)}s] ${m}`);
const sleep = (ms) => new Promise(r => setTimeout(r, ms));
bot.on('message', (m) => log('CHAT: ' + m.toString()));
bot.on('error', (e) => log('ERROR: ' + e));
bot.on('end', (r) => log('DISCONNECTED: ' + JSON.stringify(r)));
const which = process.argv[2];

async function c(x, w = 350) { bot.chat(x); await sleep(w); }

bot.once('spawn', async () => {
  await sleep(1500);
  await c('/gamemode survival');
  await c('/execute in emberfall:expedition run tp EmberTester 0 100 0', 600);
  // Build a 41x41 stone arena floor at y=99 (walls of barrier so nothing wanders off) and stand on it,
  // so the test never depends on the expedition terrain height.
  await c('/execute in emberfall:expedition run fill -20 99 -20 20 99 20 minecraft:stone', 700);
  await c('/execute in emberfall:expedition run fill -20 100 -20 20 104 20 minecraft:air', 700);
  await c('/execute in emberfall:expedition run tp EmberTester 0.5 100 0.5', 500);
  await c('/kill @e[type=!player,distance=..60]', 400);
  await c('/effect give @s minecraft:resistance 999 4 true');   // survive so effects can be read, still receives effects
  await c('/effect give @s minecraft:regeneration 999 4 true');

  if (which === 'colossus') {
    await c('/execute in emberfall:expedition positioned 0 100 8 run emberfall spawnelite plague_colossus', 500);
    await c('/data get entity @e[type=emberfall:plague_colossus,limit=1] Health');
    await c('/attribute @e[type=emberfall:plague_colossus,limit=1] minecraft:scale get');
    await c('/data get entity @e[type=emberfall:plague_colossus,limit=1] equipment.head.id');
    // let it grow + fight
    await sleep(9000);
    await c('/attribute @e[type=emberfall:plague_colossus,limit=1] minecraft:scale get');
    await c('/execute as @s at @s if entity @e[type=emberfall:horde_zombie]');
    await c('/execute as @s at @s if entity @e[type=emberfall:horde_zombie,distance=..80]', 200);
    await sleep(8000);
    await c('/execute as @s at @s if entity @e[type=emberfall:horde_zombie,distance=..80]', 200);
    await c('/effect clear @s minecraft:nausea');
    await c('/effect clear @s minecraft:poison');
    await c('/kill @e[type=emberfall:plague_colossus]', 800);
    await c('/execute as @s at @s if entity @e[type=emberfall:plague_colossus]');
  }
  if (which === 'marksman') {
    await c('/execute in emberfall:expedition positioned 0 100 10 run emberfall spawnelite boil_ridden_marksman', 500);
    await c('/data get entity @e[type=emberfall:boil_ridden_marksman,limit=1] Health');
    await c('/data get entity @e[type=emberfall:boil_ridden_marksman,limit=1] equipment.head.id');
    await c('/execute as @s at @s if entity @e[type=minecraft:item_display]');
    await sleep(16000);
    await c('/execute as @s at @s if entity @e[type=minecraft:area_effect_cloud]');
    await c('/effect clear @s minecraft:poison');
    await sleep(6000);
    await c('/execute as @s at @s if entity @e[type=minecraft:area_effect_cloud]');
    await c('/kill @e[type=emberfall:boil_ridden_marksman]', 700);
    await c('/execute as @s at @s if entity @e[type=minecraft:item_display]');
  }
  if (which === 'brood') {
    await c('/execute in emberfall:expedition positioned 0 100 8 run emberfall spawnelite broodmother_stalker', 500);
    await c('/data get entity @e[type=emberfall:broodmother_stalker,limit=1] Health');
    await c('/execute as @s at @s if entity @e[type=minecraft:item_display]');
    await sleep(6000);
    await c('/effect clear @s minecraft:slowness');
    await c('/effect clear @s minecraft:mining_fatigue');
    await c('/execute as @s at @s if entity @e[type=minecraft:cave_spider]');
    await sleep(20000);
    await c('/execute as @s at @s if entity @e[type=minecraft:cave_spider]');
    await c('/kill @e[type=emberfall:broodmother_stalker]', 700);
    await c('/execute as @s at @s if entity @e[type=minecraft:item_display]');
    await c('/kill @e[type=minecraft:cave_spider]', 300);
  }
  await c('/kill @e[type=!player,distance=..80]', 400);
  bot.quit(); await sleep(300); process.exit(0);
});
setTimeout(() => { log('HARD TIMEOUT'); process.exit(1); }, 70000);
