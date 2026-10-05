const mineflayer = require('mineflayer');
const bot = mineflayer.createBot({ host: '127.0.0.1', port: 25565, username: 'EmberTester', version: '1.21.11', auth: 'offline' });
let t0 = Date.now();
const log = (m) => console.log(`[${((Date.now()-t0)/1000).toFixed(1)}s] ${m}`);
function cmd(c) { log('CMD: ' + c); bot.chat(c); }
bot.on('message', (msg) => log('CHAT: ' + msg.toString()));
bot.on('error', (e) => log('ERROR: ' + e));

bot.once('spawn', async () => {
  const sleep = (ms) => new Promise(r => setTimeout(r, ms));
  cmd('/gamemode creative'); await sleep(500);
  cmd('/expedition'); await sleep(2500);
  cmd('/emberfall wavestop 0'); await sleep(400);
  cmd('/kill @e[type=emberfall:horde_zombie]'); await sleep(400);
  cmd('/emberfall givecurrency EmberTester 2000'); await sleep(400);

  // ===== Widening Gyre tier1 =====
  cmd('/emberfall buyweapon EmberTester spectral_sickles'); await sleep(400);
  cmd('/emberfall selectweapon EmberTester spectral_sickles'); await sleep(400);
  cmd('/emberfall granttome EmberTester widening_gyre'); await sleep(400);
  let p = bot.entity.position;
  cmd(`/summon emberfall:horde_zombie ${(p.x+1.8).toFixed(1)} ${p.y} ${p.z.toFixed(1)} {Silent:1b,PersistenceRequired:1b,Tags:["gyre1"],Attributes:[{id:"minecraft:movement_speed",base:0.0}]}`);
  await sleep(8000);
  cmd('/kill @e[tag=gyre1]'); await sleep(300);

  // ===== Widening Gyre tier2 =====
  cmd('/emberfall granttome EmberTester widening_gyre'); await sleep(400);
  p = bot.entity.position;
  cmd(`/summon emberfall:horde_zombie ${(p.x+1.8).toFixed(1)} ${p.y} ${p.z.toFixed(1)} {Silent:1b,PersistenceRequired:1b,Tags:["near"],Attributes:[{id:"minecraft:movement_speed",base:0.0},{id:"minecraft:max_health",base:400.0}]}`);
  await sleep(16000);
  cmd('/kill @e[tag=near]'); await sleep(300);

  // ===== Undying Embers tier1: replace-triggered ember =====
  cmd('/emberfall buyweapon EmberTester ashen_beacon'); await sleep(400);
  cmd('/emberfall selectweapon EmberTester ashen_beacon'); await sleep(400);
  cmd('/emberfall granttome EmberTester undying_embers'); await sleep(400);
  p = bot.entity.position;
  cmd(`/summon emberfall:horde_zombie ${(p.x+3).toFixed(1)} ${p.y} ${p.z.toFixed(1)} {Silent:1b,PersistenceRequired:1b,Tags:["beaconA"],Attributes:[{id:"minecraft:movement_speed",base:0.0}]}`);
  await sleep(600);
  log('--- waiting for a shot to land on beaconA (plants beacon there) ---');
  await sleep(2500);
  cmd(`/summon emberfall:horde_zombie ${(p.x-3).toFixed(1)} ${p.y} ${p.z.toFixed(1)} {Silent:1b,PersistenceRequired:1b,Tags:["beaconB"],Attributes:[{id:"minecraft:movement_speed",base:0.0}]}`);
  log('--- waiting for a shot to land on beaconB (replaces beaconA -> should spawn Ember at old spot) ---');
  await sleep(3000);
  cmd('/kill @e[tag=beaconA]'); await sleep(200);
  cmd('/kill @e[tag=beaconB]'); await sleep(300);

  // ===== Grave Anchor tier1+2: gather threshold + root =====
  cmd('/emberfall buyweapon EmberTester gravechain'); await sleep(400);
  cmd('/emberfall selectweapon EmberTester gravechain'); await sleep(400);
  cmd('/emberfall granttome EmberTester grave_anchor'); await sleep(400); // tier1
  cmd('/emberfall granttome EmberTester grave_anchor'); await sleep(400); // tier2
  p = bot.entity.position;
  cmd(`/summon emberfall:horde_zombie ${(p.x+2).toFixed(1)} ${p.y} ${p.z.toFixed(1)} {Silent:1b,PersistenceRequired:1b,Tags:["hookMain"],Attributes:[{id:"minecraft:movement_speed",base:0.0},{id:"minecraft:max_health",base:400.0}]}`);
  cmd(`/summon emberfall:horde_zombie ${(p.x+4).toFixed(1)} ${p.y} ${p.z.toFixed(1)} {Silent:1b,PersistenceRequired:1b,Tags:["hookSide"],Attributes:[{id:"minecraft:movement_speed",base:0.0}]}`);
  await sleep(500);
  log('--- attacking hookMain, watching for gather-threshold logs ---');
  await sleep(9000);
  cmd('/data get entity @e[tag=hookSide,limit=1] active_effects'); await sleep(400);
  cmd('/data get entity @e[tag=hookMain,limit=1] active_effects'); await sleep(400);
  cmd('/kill @e[tag=hookMain]'); await sleep(200);
  cmd('/kill @e[tag=hookSide]'); await sleep(300);

  await sleep(300);
  cmd('/expedition leave'); await sleep(1500);
  bot.quit(); await sleep(1000); process.exit(0);
});
