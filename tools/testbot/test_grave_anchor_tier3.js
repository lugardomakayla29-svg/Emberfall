const mineflayer = require('mineflayer');
const bot = mineflayer.createBot({ host: '127.0.0.1', port: 25565, username: 'EmberTester', version: '1.21.11', auth: 'offline' });
let t0 = Date.now();
const log = (m) => console.log(`[${((Date.now()-t0)/1000).toFixed(1)}s] ${m}`);
let lastMsg = null;
function cmd(c) { lastMsg = null; bot.chat(c); }
bot.on('message', (msg) => { lastMsg = msg.toString(); log('CHAT: ' + lastMsg); });
bot.on('error', (e) => log('ERROR: ' + e));
bot.on('end', (r) => log('DISCONNECTED: ' + JSON.stringify(r)));

function sleep(ms) { return new Promise(r => setTimeout(r, ms)); }
async function query(c, regex) {
  cmd(c); await sleep(120);
  const m = regex.exec(lastMsg || '');
  return m ? m[1] : null;
}
const readSlowness = (tag) => query(`/data get entity @e[tag=${tag},limit=1,sort=nearest] active_effects`, /duration: (\d+)/);
const readHealth = (tag) => query(`/data get entity @e[tag=${tag},limit=1,sort=nearest] Health`, /: ([\d.]+)f/);

bot.once('spawn', async () => {
  cmd('/gamemode creative'); await sleep(300);
  cmd('/execute in emberfall:expedition run tp EmberTester 0 100 0'); await sleep(400);
  cmd('/execute in emberfall:expedition run fill 8 99 8 -8 99 -8 minecraft:stone'); await sleep(600);
  cmd('/emberfall selectweapon EmberTester gravechain'); await sleep(300);
  cmd('/emberfall granttome EmberTester grave_anchor'); await sleep(250);
  cmd('/emberfall granttome EmberTester grave_anchor'); await sleep(250);
  cmd('/emberfall granttome EmberTester grave_anchor'); await sleep(250);
  cmd('/kill @e[type=emberfall:horde_zombie]'); await sleep(300);

  log('=== Measured live: Gravechain lands ~2.94 dmg/hit at a real cadence of ~0.3s/hit (faster than assumed). Gather (tier2+, interval 3) fires on hit 3. anchor HP=13.0 survives hits 1-4 (11.76 dealt) then dies on hit 5 (+2.94=14.70) - NOT a multiple of 3, so no natural 2nd Gather coincides with the kill; pile1/pile2 (HP 100, durable) sit 3 blocks out, within HOOK_GATHER_RADIUS=6 ===');
  cmd('/summon emberfall:horde_zombie 1 100 0.5 {Silent:1b,PersistenceRequired:1b,NoAI:1b,Tags:["anchor"],Attributes:[{id:"minecraft:max_health",base:20.0}]}');
  await sleep(300);
  cmd('/data merge entity @e[tag=anchor,limit=1,sort=nearest] {Health:13.0f}'); await sleep(300);
  cmd('/summon emberfall:horde_zombie 4 100 0.5 {Silent:1b,PersistenceRequired:1b,NoAI:1b,Tags:["pile1"],Attributes:[{id:"minecraft:max_health",base:100.0}]}');
  await sleep(300);
  cmd('/data merge entity @e[tag=pile1,limit=1,sort=nearest] {Health:100.0f}'); await sleep(300);
  cmd('/summon emberfall:horde_zombie 1 100 3.5 {Silent:1b,PersistenceRequired:1b,NoAI:1b,Tags:["pile2"],Attributes:[{id:"minecraft:max_health",base:100.0}]}');
  await sleep(300);
  cmd('/data merge entity @e[tag=pile2,limit=1,sort=nearest] {Health:100.0f}'); await sleep(300);
  cmd('/tp @s 0.5 100 0.5 facing 1 100 0.5'); await sleep(300);

  log('=== Waiting 1.8s for hits 1-5 to land (anchor dies on hit 5, ~1.2-1.5s in) ===');
  await sleep(1800);
  log(`anchor health query (null/"No entity was found" = dead = hit 5 landed the kill): ${await readHealth('anchor')}`);

  log('=== Tier 3 check A (immediately after the kill): if Anchor Snap refreshed the root, both should read close to 30 again; if it did nothing, plain decay since hit 3\'s Gather (~2 hits/~0.6s=12 ticks earlier) would read close to ~18 ===');
  const pile1A = await readSlowness('pile1');
  const pile2A = await readSlowness('pile2');
  log(`pile1 Slowness: ${pile1A}`);
  log(`pile2 Slowness: ${pile2A}`);

  log('=== Backing off out of weapon range immediately so no further hits land on pile1/pile2 - isolates check B to pure decay of whatever check A captured ===');
  cmd('/tp @s 40 100 40'); await sleep(1000);

  log('=== Tier 3 check B (~1s of pure decay after check A, no attacks in between): should read almost exactly 20 ticks (1s) less than check A ===');
  log(`pile1 Slowness: ${await readSlowness('pile1')} (expect ~${pile1A ? Number(pile1A) - 20 : '?'})`);
  log(`pile2 Slowness: ${await readSlowness('pile2')} (expect ~${pile2A ? Number(pile2A) - 20 : '?'})`);

  cmd('/kill @e[type=emberfall:horde_zombie]');
  await sleep(300);
  bot.quit(); await sleep(500); process.exit(0);
});
