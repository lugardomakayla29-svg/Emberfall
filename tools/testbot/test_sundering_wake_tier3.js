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
const readHealth = (tag) => query(`/data get entity @e[tag=${tag},limit=1,sort=nearest] Health`, /: ([\d.]+)f/).then(Number);

bot.once('spawn', async () => {
  cmd('/gamemode creative'); await sleep(300);
  cmd('/execute in emberfall:expedition run tp EmberTester 0 100 0'); await sleep(400);
  cmd('/execute in emberfall:expedition run fill 5 99 5 -5 99 -5 minecraft:stone'); await sleep(500);
  cmd('/emberfall selectweapon EmberTester war_halberd'); await sleep(300);
  cmd('/emberfall granttome EmberTester sundering_wake'); await sleep(250);
  cmd('/emberfall granttome EmberTester sundering_wake'); await sleep(250);
  cmd('/emberfall granttome EmberTester sundering_wake'); await sleep(250);
  cmd('/kill @e[type=emberfall:horde_zombie]'); await sleep(300);

  log('=== Spawning dummyA (will be killed at max Sunder) and dummyB (3.5 away, should catch the shatter) ===');
  cmd('/summon emberfall:horde_zombie 3 100 0.5 {Silent:1b,PersistenceRequired:1b,NoAI:1b,Tags:["dummyA"],Attributes:[{id:"minecraft:max_health",base:100.0}]}');
  await sleep(300);
  cmd('/data merge entity @e[tag=dummyA,limit=1,sort=nearest] {Health:100.0f}'); await sleep(300);
  cmd('/summon emberfall:horde_zombie 3 100 -3 {Silent:1b,PersistenceRequired:1b,NoAI:1b,Tags:["dummyB"],Attributes:[{id:"minecraft:max_health",base:100.0}]}');
  await sleep(300);
  cmd('/data merge entity @e[tag=dummyB,limit=1,sort=nearest] {Health:100.0f}'); await sleep(300);
  cmd('/tp @s 0.5 100 0.5 facing 3 100 0.5'); await sleep(300);

  log('=== Waiting ~2.8s for 2 slow halberd swings to land (Sunder should reach max=1 stack) ===');
  await sleep(2800);

  log('=== Forcing dummyA to 2.0 HP so the 3rd landed hit kills it while already at max Sunder ===');
  cmd('/data merge entity @e[tag=dummyA,limit=1,sort=nearest] {Health:2.0f}'); await sleep(1600);
  log(`dummyA gone (dead)? health query: ${await readHealth('dummyA')} (dead/gone = No entity was found)`);

  cmd('/kill @e[type=emberfall:horde_zombie]');
  await sleep(300);
  bot.quit(); await sleep(500); process.exit(0);
});
