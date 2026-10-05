const mineflayer = require('mineflayer');
const bot = mineflayer.createBot({ host: '127.0.0.1', port: 25565, username: 'EmberTester', version: '1.21.11', auth: 'offline' });
const sleep = ms => new Promise(r => setTimeout(r, ms));
bot.on('error', e => console.log('ERROR', e));
bot.on('message', m => { const t=m.toString(); if (t.length<170 && !/attribute|Applied effect|Killed|Summoned|game mode|Teleported|No entity/.test(t)) console.log('MSG:', t); });
const parts = {}; const snds = {};
bot._client.on('packet', (d, m) => {
  if (m.name === 'world_particles') { const k = JSON.stringify(d.particle?.type ?? d.particleId); parts[k] = (parts[k]||0)+1; }
});
const c = async (x, w=700) => { bot.chat(x); await sleep(w); };
bot.once('spawn', async () => {
  await sleep(6000);
  await c('/expedition leave', 800);                       // clear any leftover run
  await c('/gamemode creative'); await c('/tp @s 38 120 -106', 1500); await c('/gamemode survival', 1500); await c('/effect give @s minecraft:resistance 900 4 true');
  await c('/effect give @s minecraft:regeneration 900 4 true');
  await c('/expedition', 3000);                            // start on real ground where we stand
  await c('/kill @e[type=emberfall:horde_zombie]', 500);   // clear wave mobs so the only target is ours
  await c('/kill @e[type=emberfall:horde_skeleton]', 300); await c('/kill @e[type=emberfall:horde_spider]', 300);
  await c('/execute at @s run summon emberfall:horde_zombie ~1.5 ~ ~', 600);
  await c('/execute as @s at @s if entity @e[type=emberfall:horde_zombie,distance=..2.5]', 500);
  await c('/attribute @e[type=emberfall:horde_zombie,limit=1,sort=nearest] minecraft:movement_speed base set 0', 300);
  await c('/attribute @e[type=emberfall:horde_zombie,limit=1,sort=nearest] minecraft:max_health base set 600', 300);
  await c('/effect give @e[type=emberfall:horde_zombie,limit=1,sort=nearest] minecraft:instant_health 1 30 true', 300);
  for (const k of Object.keys(parts)) delete parts[k];
  await c('/data get entity @s SelectedItemSlot', 500); await c('/data get entity @s Inventory[{Slot:0b}].id', 500);
  await c('/data get entity @e[type=emberfall:horde_zombie,limit=1,sort=nearest] Health', 600);
  await c('/execute if entity @e[type=emberfall:horde_zombie,distance=..4]', 600);
  await sleep(12000);
  await c('/data get entity @e[type=emberfall:horde_zombie,limit=1,sort=nearest] Health', 600);
  console.log('PARTICLES over 12s of auto-attack on a held target:', JSON.stringify(parts));
  await c('/expedition leave', 1200); bot.quit(); process.exit(0);
});
