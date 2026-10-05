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

bot.once('spawn', async () => {
  cmd('/gamemode creative'); await sleep(300);
  cmd('/execute in emberfall:expedition run tp EmberTester 0 100 0'); await sleep(400);
  cmd('/kill @e[type=emberfall:horde_zombie]'); await sleep(300);

  log('=== Test A: inline Attributes NBT at summon (what my tests have been using) ===');
  cmd('/summon emberfall:horde_zombie 10 100 0 {Silent:1b,PersistenceRequired:1b,NoAI:1b,Tags:["a"],Attributes:[{id:"minecraft:max_health",base:1000.0}]}');
  await sleep(400);
  log(`a max_health via /attribute query: ${await query('/attribute @e[tag=a,limit=1] minecraft:max_health base get', /is ([\d.]+)/)}`);
  log(`a Health NBT: ${await query('/data get entity @e[tag=a,limit=1] Health', /: ([\d.]+)f/)}`);

  log('=== Test B: /attribute command to set base after summon ===');
  cmd('/summon emberfall:horde_zombie 12 100 0 {Silent:1b,PersistenceRequired:1b,NoAI:1b,Tags:["b"]}');
  await sleep(400);
  cmd('/attribute @e[tag=b,limit=1] minecraft:max_health base set 1000');
  await sleep(300);
  log(`b max_health via /attribute query: ${await query('/attribute @e[tag=b,limit=1] minecraft:max_health base get', /is ([\d.]+)/)}`);
  cmd('/data merge entity @e[tag=b,limit=1] {Health:1000.0f}'); await sleep(300);
  log(`b Health NBT after merge: ${await query('/data get entity @e[tag=b,limit=1] Health', /: ([\d.]+)f/)}`);

  cmd('/kill @e[type=emberfall:horde_zombie]');
  await sleep(300);
  bot.quit(); await sleep(500); process.exit(0);
});
