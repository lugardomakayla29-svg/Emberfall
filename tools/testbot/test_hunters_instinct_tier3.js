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
  cmd('/execute in emberfall:expedition run fill 10 99 10 -10 99 -10 minecraft:stone'); await sleep(600);
  cmd('/emberfall selectweapon EmberTester hunting_bow'); await sleep(300);
  cmd('/emberfall granttome EmberTester hunters_instinct'); await sleep(250);
  cmd('/emberfall granttome EmberTester hunters_instinct'); await sleep(250);
  cmd('/emberfall granttome EmberTester hunters_instinct'); await sleep(250);
  cmd('/kill @e[type=emberfall:horde_zombie]'); await sleep(300);

  log('=== dummyA starts at 14.0 HP (rangedDamage=4.0/shot: survives shots 1-3, dies to shot 4/the pierce) - deterministic, no race. dummyC (NoAI, 3,3) for the chain to find ===');
  cmd('/summon emberfall:horde_zombie 5 100 0.5 {Silent:1b,PersistenceRequired:1b,Attributes:[{id:"minecraft:max_health",base:100.0},{id:"minecraft:movement_speed",base:0.0}],Tags:["dummyA"]}');
  await sleep(300);
  cmd('/data merge entity @e[tag=dummyA,limit=1,sort=nearest] {Health:14.0f}'); await sleep(300);
  cmd('/summon emberfall:horde_zombie 3 100 3 {Silent:1b,PersistenceRequired:1b,NoAI:1b,Attributes:[{id:"minecraft:max_health",base:100.0}],Tags:["dummyC"]}');
  await sleep(300);
  cmd('/data merge entity @e[tag=dummyC,limit=1,sort=nearest] {Health:100.0f}'); await sleep(300);
  cmd('/tp @s 0.5 100 0.5 facing 5 100 0.5'); await sleep(300);

  log('=== Waiting 3s for 4 shots to land (2/sec cadence) ===');
  await sleep(3000);

  log(`dummyA gone? ${await readHealth('dummyA')} (0/no-entity = confirmed dead from the pierce)`);
  log(`dummyC HP (should be reduced if the chained re-volley landed on it): ${await readHealth('dummyC')}`);

  cmd('/kill @e[type=emberfall:horde_zombie]');
  await sleep(300);
  bot.quit(); await sleep(500); process.exit(0);
});
