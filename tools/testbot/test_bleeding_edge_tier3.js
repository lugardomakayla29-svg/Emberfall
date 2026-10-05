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
  cmd(c); await sleep(150);
  const m = regex.exec(lastMsg || '');
  return m ? m[1] : null;
}
const readStreak = () => query('/emberfall debugstreak EmberTester', /streak: (\d+)/).then(Number);
const readHealth = (tag) => query(`/data get entity @e[tag=${tag},limit=1,sort=nearest] Health`, /: ([\d.]+)f/).then(Number);

bot.once('spawn', async () => {
  cmd('/gamemode creative'); await sleep(300);
  cmd('/execute in emberfall:expedition run tp EmberTester 0 100 0'); await sleep(400);
  cmd('/execute in emberfall:expedition run fill 3 99 3 -3 99 -3 minecraft:stone'); await sleep(400);
  cmd('/emberfall selectweapon EmberTester twin_daggers'); await sleep(300);
  cmd('/emberfall granttome EmberTester bleeding_edge'); await sleep(250);
  cmd('/emberfall granttome EmberTester bleeding_edge'); await sleep(250);
  cmd('/emberfall granttome EmberTester bleeding_edge'); await sleep(250);
  cmd('/kill @e[type=emberfall:horde_zombie]'); await sleep(300);

  log('=== Spawning dummyA (will be killed) and dummyB (2 blocks away, should catch the Rend burst) ===');
  cmd('/summon emberfall:horde_zombie 3 100 0.5 {Silent:1b,PersistenceRequired:1b,NoAI:1b,Tags:["dummyA"],Attributes:[{id:"minecraft:max_health",base:100.0}]}');
  await sleep(300);
  cmd('/data merge entity @e[tag=dummyA,limit=1,sort=nearest] {Health:100.0f}'); await sleep(300);
  cmd('/summon emberfall:horde_zombie 3 100 -2 {Silent:1b,PersistenceRequired:1b,NoAI:1b,Tags:["dummyB"],Attributes:[{id:"minecraft:max_health",base:100.0}]}');
  await sleep(300);
  cmd('/data merge entity @e[tag=dummyB,limit=1,sort=nearest] {Health:100.0f}'); await sleep(300);
  cmd('/tp @s 0.5 100 0.5 facing 3 100 0.5'); await sleep(300);

  log('=== Polling streak until ==2 (comboInterval=3 with tier>=2, so next hit is the 3rd/Empowered) ===');
  for (let i = 0; i < 60; i++) {
    const streak = await readStreak();
    if (streak === 2) { log(`streak hit 2 at poll ${i}`); break; }
    await sleep(70);
  }

  log('=== Forcing dummyA to 2.0 HP so the Empowered (3rd) hit kills it - it should already have Rend from hit 1-2 ===');
  cmd('/data merge entity @e[tag=dummyA,limit=1,sort=nearest] {Health:2.0f}'); await sleep(700);
  log(`dummyA gone (dead)? health query: ${await readHealth('dummyA')} (dead/gone = No entity was found)`);

  log('=== Directly checking dummyB\'s ActiveEffects NBT for a Wither entry (sidesteps HP-math noise entirely) ===');
  const effectsB = await query('/data get entity @e[tag=dummyB,limit=1,sort=nearest] ActiveEffects', /(.+)/);
  log(`>>> dummyB ActiveEffects: ${effectsB} <<<`);

  cmd('/kill @e[type=emberfall:horde_zombie]');
  await sleep(300);
  bot.quit(); await sleep(500); process.exit(0);
});
