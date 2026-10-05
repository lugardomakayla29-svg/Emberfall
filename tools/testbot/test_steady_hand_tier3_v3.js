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
const readArmed = () => query('/emberfall debugarmed EmberTester', /steadyHandArmed: (\w+)/);
const readHealth = (tag) => query(`/data get entity @e[tag=${tag},limit=1,sort=nearest] Health`, /: ([\d.]+)f/).then(Number);

bot.once('spawn', async () => {
  cmd('/gamemode creative'); await sleep(300);
  cmd('/execute in emberfall:expedition run tp EmberTester 0 100 0'); await sleep(400);
  cmd('/execute in emberfall:expedition run fill 3 99 3 -3 99 -3 minecraft:stone'); await sleep(400);
  cmd('/emberfall selectweapon EmberTester broadsword'); await sleep(300);
  cmd('/emberfall granttome EmberTester steady_hand'); await sleep(250);
  cmd('/emberfall granttome EmberTester steady_hand'); await sleep(250);
  cmd('/emberfall granttome EmberTester steady_hand'); await sleep(250);
  cmd('/kill @e[type=emberfall:horde_zombie]'); await sleep(300);

  log(`armed flag before any combat (expect false): ${await readArmed()}`);

  cmd('/summon emberfall:horde_zombie 3 100 0.5 {Silent:1b,PersistenceRequired:1b,NoAI:1b,Tags:["dummyA"],Attributes:[{id:"minecraft:max_health",base:200.0}],Health:200.0f}');
  await sleep(300);
  cmd('/tp @s 0.5 100 0.5 facing 3 100 0.5'); await sleep(300);

  log('=== Polling streak until ==3 (next hit will be the Empowered 4th) ===');
  for (let i = 0; i < 60; i++) {
    const streak = await readStreak();
    if (streak === 3) { log(`streak hit 3 at poll ${i}`); break; }
    await sleep(70);
  }

  log('=== Forcing dummyA to 3.0 HP so its next (Empowered) hit kills it ===');
  cmd('/data merge entity @e[tag=dummyA,limit=1,sort=nearest] {Health:3.0f}'); await sleep(600);
  log(`dummyA gone (dead)? health query says: ${await readHealth('dummyA')} (NaN/-1 = dead/gone, good)`);
  log(`>>> armed flag right after the kill (this is the whole test): ${await readArmed()} <<<`);

  log('=== Spawning dummyB and letting exactly one hit land, then checking the flag is consumed ===');
  cmd('/summon emberfall:horde_zombie 3 100 0.5 {Silent:1b,PersistenceRequired:1b,NoAI:1b,Tags:["dummyB"],Attributes:[{id:"minecraft:max_health",base:200.0}],Health:200.0f}');
  await sleep(250);
  cmd('/tp @s 0.5 100 0.5 facing 3 100 0.5'); await sleep(900); // let >=1 hit land, well under a 2nd
  const hpB = await readHealth('dummyB');
  log(`dummyB HP after (should be ~194.1 if that one hit was Empowered ~5.9, ~196.1 if normal ~3.9): ${hpB}`);
  log(`>>> armed flag after dummyB's hit (expect false - consumed): ${await readArmed()} <<<`);

  cmd('/kill @e[type=emberfall:horde_zombie]');
  await sleep(300);
  bot.quit(); await sleep(500); process.exit(0);
});
