const mineflayer = require('mineflayer');
const bot = mineflayer.createBot({ host: '127.0.0.1', port: 25565, username: 'EmberTester', version: '1.21.11', auth: 'offline' });
let t0 = Date.now();
const log = (m) => console.log(`[${((Date.now()-t0)/1000).toFixed(1)}s] ${m}`);
let lastMsg = null;
function cmd(c) { lastMsg = null; bot.chat(c); }
bot.on('message', (msg) => { lastMsg = msg.toString(); log('CHAT: ' + lastMsg); });
bot.on('error', (e) => log('ERROR: ' + e));
bot.on('end', (r) => log('DISCONNECTED: ' + JSON.stringify(r)));
bot.on('kicked', (r) => log('KICKED: ' + JSON.stringify(r)));

function sleep(ms) { return new Promise(r => setTimeout(r, ms)); }

async function readStreak() {
  cmd('/emberfall debugstreak EmberTester');
  await sleep(150);
  const m = /streak: (\d+)/.exec(lastMsg || '');
  return m ? parseInt(m[1]) : -1;
}
async function readHealth(tag) {
  cmd(`/data get entity @e[tag=${tag},limit=1,sort=nearest] Health`);
  await sleep(150);
  const m = /: ([\d.]+)f/.exec(lastMsg || '');
  return m ? parseFloat(m[1]) : -1;
}

bot.once('spawn', async () => {
  cmd('/gamemode creative'); await sleep(400);
  cmd('/execute in emberfall:expedition run tp EmberTester 0 100 0'); await sleep(500);
  cmd('/execute in emberfall:expedition run fill 3 99 3 -3 99 -3 minecraft:stone'); await sleep(500);
  cmd('/emberfall selectweapon EmberTester broadsword'); await sleep(400);
  cmd('/emberfall granttome EmberTester steady_hand'); await sleep(300);
  cmd('/emberfall granttome EmberTester steady_hand'); await sleep(300);
  cmd('/emberfall granttome EmberTester steady_hand'); await sleep(300);
  cmd('/kill @e[type=emberfall:horde_zombie]'); await sleep(400);

  log('=== Spawning dummyA with high HP so it survives many cycles while we watch the streak ===');
  cmd('/summon emberfall:horde_zombie 3 100 0.5 {Silent:1b,PersistenceRequired:1b,NoAI:1b,Tags:["dummyA"],Attributes:[{id:"minecraft:max_health",base:200.0}],Health:200.0f}');
  await sleep(400);
  cmd('/tp @s 0.5 100 0.5 facing 3 100 0.5'); await sleep(400);

  log('=== Polling streak+health every 150ms until streak==3 (next hit will be Empowered #4) ===');
  let sawStreak3 = false;
  for (let i = 0; i < 60; i++) {
    const streak = await readStreak();
    const hp = await readHealth('dummyA');
    log(`poll ${i}: streak=${streak} hp=${hp}`);
    if (streak === 3) { sawStreak3 = true; break; }
    await sleep(80);
  }

  if (!sawStreak3) {
    log('FAILED to catch streak==3 in time - aborting');
  } else {
    log('=== streak==3 caught. Forcing dummyA HP to 3.0 (survives a normal ~3.9dmg hit, dies to an Empowered ~5.9dmg hit) ===');
    cmd('/data merge entity @e[tag=dummyA,limit=1,sort=nearest] {Health:3.0f}'); await sleep(700);
    const afterHp = await readHealth('dummyA');
    log(`dummyA HP after the streak==3->4 hit resolved: ${afterHp} (expect: dead if killed by Empowered hit, as it should be)`);

    log('=== Spawning dummyB immediately to check if the kill re-armed the streak (next hit should be Empowered) ===');
    cmd('/summon emberfall:horde_zombie 3 100 0.5 {Silent:1b,PersistenceRequired:1b,NoAI:1b,Tags:["dummyB"],Attributes:[{id:"minecraft:max_health",base:200.0}],Health:200.0f}');
    await sleep(200);
    cmd('/tp @s 0.5 100 0.5 facing 3 100 0.5'); await sleep(250);
    const hpB1 = await readHealth('dummyB');
    log(`dummyB HP after its first hit: ${hpB1} (200 - hpB1 = damage of that first hit; ~5.9 if Empowered, ~3.9 if normal)`);
  }

  cmd('/kill @e[type=emberfall:horde_zombie]');
  await sleep(500);
  bot.quit(); await sleep(1000); process.exit(0);
});
