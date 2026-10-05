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

async function spawnDummy(tag, x, z, hp) {
  cmd(`/summon emberfall:horde_zombie ${x} 100 ${z} {Silent:1b,PersistenceRequired:1b,NoAI:1b,Attributes:[{id:"minecraft:max_health",base:100.0}],Tags:["${tag}"]}`);
  await sleep(250);
  cmd(`/data merge entity @e[tag=${tag},limit=1,sort=nearest] {Health:${hp.toFixed(1)}f}`);
  await sleep(250);
}

bot.once('spawn', async () => {
  cmd('/gamemode creative'); await sleep(300);
  cmd('/execute in emberfall:expedition run tp EmberTester 0 100 0'); await sleep(400);
  cmd('/execute in emberfall:expedition run fill 45 99 5 -5 99 -5 minecraft:stone'); await sleep(700);
  cmd('/emberfall selectweapon EmberTester arcane_staff'); await sleep(300);
  cmd('/emberfall granttome EmberTester arcane_convergence'); await sleep(250);
  cmd('/emberfall granttome EmberTester arcane_convergence'); await sleep(250);
  cmd('/emberfall granttome EmberTester arcane_convergence'); await sleep(250);
  cmd('/kill @e[type=emberfall:horde_zombie]'); await sleep(300);

  log('=== mob1 at 10 HP survives shots 1-3 (7.5 dmg) then dies on shot 4 (Nova, +3.75). mob2-6 at 2.0 HP (out of weapon range, only reachable via sparks; any Nova/spark hit is lethal), 7 blocks apart along the beam ===');
  await spawnDummy('mob1', 4, 0, 10.0);
  await spawnDummy('mob2', 10, 0, 2.0);
  await spawnDummy('mob3', 17, 0, 2.0);
  await spawnDummy('mob4', 24, 0, 2.0);
  await spawnDummy('mob5', 31, 0, 2.0);
  await spawnDummy('mob6', 38, 0, 2.0);
  cmd('/tp @s 0.5 100 0.5 facing 4 100 0'); await sleep(300);

  log('=== Waiting ~10s for shots 1-3 (normal) + shot 4 (primary Nova) then the full spark chain to propagate ===');
  await sleep(10000);

  for (const tag of ['mob1','mob2','mob3','mob4','mob5','mob6']) {
    cmd(`/data get entity @e[tag=${tag},limit=1,sort=nearest] Health`);
    await sleep(200);
    log(`${tag}: ${lastMsg}`);
  }

  cmd('/kill @e[type=emberfall:horde_zombie]');
  await sleep(300);
  bot.quit(); await sleep(500); process.exit(0);
});
