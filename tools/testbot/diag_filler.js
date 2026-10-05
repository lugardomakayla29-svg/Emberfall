const mineflayer = require('mineflayer');
const bot = mineflayer.createBot({ host: '127.0.0.1', port: 25565, username: 'EmberTester', version: '1.21.11', auth: 'offline' });
let t0 = Date.now();
const log = (m) => console.log(`[${((Date.now()-t0)/1000).toFixed(1)}s] ${m}`);
let lastMsg = null;
function cmd(c) { lastMsg = null; bot.chat(c); }
bot.on('message', (msg) => { lastMsg = msg.toString(); log('CHAT: ' + lastMsg); });
bot.on('error', (e) => log('ERROR: ' + e));
function sleep(ms) { return new Promise(r => setTimeout(r, ms)); }
async function query(c, regex) {
  cmd(c); await sleep(150);
  const m = regex.exec(lastMsg || '');
  return m ? m[1] : null;
}
const readHealth = (tag) => query(`/data get entity @e[tag=${tag},limit=1,sort=nearest] Health`, /: ([\d.]+)f/);

bot.once('spawn', async () => {
  cmd('/gamemode creative'); await sleep(300);
  cmd('/execute in emberfall:expedition run tp EmberTester 0 100 0'); await sleep(400);
  cmd('/emberfall selectweapon EmberTester spectral_sickles'); await sleep(300);
  cmd('/emberfall granttome EmberTester widening_gyre'); await sleep(250);
  cmd('/kill @e[type=emberfall:horde_zombie]'); await sleep(300);
  cmd('/tp @s 0.5 100 0.5'); await sleep(300);

  cmd('/summon emberfall:horde_zombie 2.7 100 0.5 {Silent:1b,PersistenceRequired:1b,NoAI:1b,Tags:["filler"],Attributes:[{id:"minecraft:max_health",base:1000.0}]}');
  await sleep(300);
  cmd('/data merge entity @e[tag=filler,limit=1,sort=nearest] {Health:1000.0f}'); await sleep(300);
  log(`initial health: ${await readHealth('filler')}`);

  for (let i = 0; i < 12; i++) {
    await sleep(800);
    const h = await readHealth('filler');
    log(`t+${((i+1)*0.8).toFixed(1)}s health: ${h}`);
    if (h === null) break;
  }

  cmd('/kill @e[type=emberfall:horde_zombie]');
  await sleep(300);
  bot.quit(); await sleep(500); process.exit(0);
});
