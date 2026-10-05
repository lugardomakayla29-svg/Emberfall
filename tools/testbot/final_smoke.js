const mineflayer = require('mineflayer');
const bot = mineflayer.createBot({ host: '127.0.0.1', port: 25565, username: 'EmberTester', version: '1.21.11', auth: 'offline' });
let t0 = Date.now();
const log = (m) => console.log(`[${((Date.now()-t0)/1000).toFixed(1)}s] ${m}`);
function cmd(c) { log('CMD: ' + c); bot.chat(c); }
bot.on('message', (msg) => log('CHAT: ' + msg.toString()));
bot.on('error', (e) => log('ERROR: ' + e));

bot.once('spawn', async () => {
  const sleep = (ms) => new Promise(r => setTimeout(r, ms));
  cmd('/gamemode creative'); await sleep(500);
  cmd('/expedition'); await sleep(2500);
  cmd('/emberfall wavestop 0'); await sleep(400);
  cmd('/kill @e[type=emberfall:horde_zombie]'); await sleep(400);
  cmd('/emberfall givecurrency EmberTester 2000'); await sleep(400);
  for (const w of ['spectral_sickles','ashen_beacon','gravechain']) {
    cmd(`/emberfall buyweapon EmberTester ${w}`); await sleep(400);
    cmd(`/emberfall selectweapon EmberTester ${w}`); await sleep(400);
  }
  for (const t of ['widening_gyre','undying_embers','grave_anchor']) {
    cmd(`/emberfall granttome EmberTester ${t}`); await sleep(400);
  }
  let p = bot.entity.position;
  cmd(`/summon emberfall:horde_zombie ${(p.x+2).toFixed(1)} ${p.y} ${p.z.toFixed(1)} {Silent:1b,PersistenceRequired:1b,Tags:["smoke"],Attributes:[{id:"minecraft:movement_speed",base:0.0},{id:"minecraft:max_health",base:400.0}]}`);
  await sleep(4000);
  cmd('/data get entity @e[tag=smoke,limit=1] Health'); await sleep(400);
  cmd('/kill @e[tag=smoke]'); await sleep(300);
  cmd('/expedition leave'); await sleep(1500);
  bot.quit(); await sleep(1000); process.exit(0);
});
