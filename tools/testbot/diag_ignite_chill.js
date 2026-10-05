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
async function query(c, regex) { cmd(c); await sleep(120); const m = regex.exec(lastMsg || ''); return m ? m[1] : null; }
async function spawnDummy(tag, x, y, z, hp) {
  cmd(`/summon emberfall:horde_zombie ${x} ${y} ${z} {Silent:1b,PersistenceRequired:1b,NoAI:1b,Tags:["${tag}"]}`);
  await sleep(250);
  cmd(`/attribute @e[tag=${tag},limit=1,sort=nearest] minecraft:max_health base set ${hp.toFixed(1)}`); await sleep(200);
  cmd(`/data merge entity @e[tag=${tag},limit=1,sort=nearest] {Health:${hp.toFixed(1)}f}`); await sleep(200);
}

bot.once('spawn', async () => {
  cmd('/gamemode creative'); await sleep(300);
  cmd('/execute in emberfall:expedition run tp EmberTester 0 100 0'); await sleep(400);
  cmd('/emberfall selectweapon EmberTester broadsword'); await sleep(300);
  cmd('/kill @e[type=emberfall:horde_zombie]'); await sleep(300);
  cmd('/tp @s 0.5 100 0.5'); await sleep(300);

  log('=== ISOLATED: Cinder Wisp - polling every 200ms for 5s, tracking primary alive-state + witness Fire ticks each poll ===');
  cmd('/emberfall granttome EmberTester cinder_wisp'); await sleep(300);
  await spawnDummy('p1', 2.5, 100, 0.5, 1.0);
  await spawnDummy('w1', 0.9, 100, 2.5, 20.0);
  cmd('/tp @s 0.5 100 0.5 facing 2.5 100 0.5'); await sleep(200);
  let primaryDeadAt = null;
  for (let i = 0; i < 25; i++) {
    const alive = await query('/data get entity @e[tag=p1,limit=1,sort=nearest] Health', /: ([\d.]+)f/);
    const fire = await query('/data get entity @e[tag=w1,limit=1,sort=nearest] Fire', /: (-?\d+)/);
    log(`poll ${i}: p1Health=${alive} w1Fire=${fire}`);
    if (alive === null && primaryDeadAt === null) primaryDeadAt = i;
    await sleep(200);
  }
  log(`primary died at poll #${primaryDeadAt}`);
  cmd('/kill @e[tag=p1]'); await sleep(150);
  cmd('/kill @e[tag=w1]'); await sleep(300);

  log('=== ISOLATED: Permafrost Shard - same polling, tracking witness active_effects each poll ===');
  cmd('/emberfall granttome EmberTester permafrost_shard'); await sleep(300);
  await spawnDummy('p2', 2.5, 100, 0.5, 1.0);
  await spawnDummy('w2', 0.9, 100, 2.5, 20.0);
  cmd('/tp @s 0.5 100 0.5 facing 2.5 100 0.5'); await sleep(200);
  let primary2DeadAt = null;
  for (let i = 0; i < 25; i++) {
    const alive = await query('/data get entity @e[tag=p2,limit=1,sort=nearest] Health', /: ([\d.]+)f/);
    const fx = await query('/data get entity @e[tag=w2,limit=1,sort=nearest] active_effects', /"([\w:]+)"/);
    log(`poll ${i}: p2Health=${alive} w2Effects=${fx}`);
    if (alive === null && primary2DeadAt === null) primary2DeadAt = i;
    await sleep(200);
  }
  log(`primary2 died at poll #${primary2DeadAt}`);
  cmd('/kill @e[tag=p2]'); await sleep(150);
  cmd('/kill @e[tag=w2]'); await sleep(300);

  cmd('/kill @e[type=emberfall:horde_zombie]');
  await sleep(300);
  cmd('/expedition leave'); await sleep(1000);
  bot.quit(); await sleep(500); process.exit(0);
});
