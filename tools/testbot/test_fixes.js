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

  // ===== Widening Gyre tier1: confirm cap is now +32% (4 stacks) not +40% =====
  cmd('/emberfall buyweapon EmberTester spectral_sickles'); await sleep(400);
  cmd('/emberfall selectweapon EmberTester spectral_sickles'); await sleep(400);
  cmd('/emberfall granttome EmberTester widening_gyre'); await sleep(400);
  cmd('/emberfall granttome EmberTester widening_gyre'); await sleep(400); // tier2 too, for radius check
  let p = bot.entity.position;
  cmd(`/summon emberfall:horde_zombie ${(p.x+1.8).toFixed(1)} ${p.y} ${p.z.toFixed(1)} {Silent:1b,PersistenceRequired:1b,Tags:["g"],Attributes:[{id:"minecraft:movement_speed",base:0.0}]}`);
  await sleep(14000);
  cmd('/kill @e[tag=g]'); await sleep(300);

  // ===== Undying Embers: confirm NO more spam-redeploy while a beacon is alive =====
  cmd('/emberfall buyweapon EmberTester ashen_beacon'); await sleep(400);
  cmd('/emberfall selectweapon EmberTester ashen_beacon'); await sleep(400);
  cmd('/emberfall granttome EmberTester undying_embers'); await sleep(400);
  cmd('/emberfall granttome EmberTester undying_embers'); await sleep(400); // tier2 too
  p = bot.entity.position;
  cmd(`/summon emberfall:horde_zombie ${(p.x+3).toFixed(1)} ${p.y} ${p.z.toFixed(1)} {Silent:1b,PersistenceRequired:1b,Tags:["t"],Attributes:[{id:"minecraft:movement_speed",base:0.0},{id:"minecraft:max_health",base:200.0}]}`);
  log('--- watching totem lifecycle: should be exactly ONE "spawning Ember" around 10s (natural expiry), not a burst ---');
  await sleep(16000); // 10s main beacon + 4s ember + margin, to see the tier2 final burst too
  cmd('/kill @e[tag=t]'); await sleep(300);

  // ===== Grave Anchor: root effect log =====
  cmd('/emberfall buyweapon EmberTester gravechain'); await sleep(400);
  cmd('/emberfall selectweapon EmberTester gravechain'); await sleep(400);
  cmd('/emberfall granttome EmberTester grave_anchor'); await sleep(400);
  p = bot.entity.position;
  cmd(`/summon emberfall:horde_zombie ${(p.x+2).toFixed(1)} ${p.y} ${p.z.toFixed(1)} {Silent:1b,PersistenceRequired:1b,Tags:["hm"],Attributes:[{id:"minecraft:movement_speed",base:0.0},{id:"minecraft:max_health",base:400.0}]}`);
  cmd(`/summon emberfall:horde_zombie ${(p.x+4).toFixed(1)} ${p.y} ${p.z.toFixed(1)} {Silent:1b,PersistenceRequired:1b,Tags:["hs"],Attributes:[{id:"minecraft:movement_speed",base:0.0},{id:"minecraft:max_health",base:400.0}]}`);
  await sleep(500);
  log('--- attacking hm, watching for root debug log at 4th hit ---');
  await sleep(6000);
  cmd('/kill @e[tag=hm]'); await sleep(200);
  cmd('/kill @e[tag=hs]'); await sleep(300);

  await sleep(300);
  cmd('/expedition leave'); await sleep(1500);
  bot.quit(); await sleep(1000); process.exit(0);
});
