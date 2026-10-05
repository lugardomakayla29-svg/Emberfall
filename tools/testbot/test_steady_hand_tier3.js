const mineflayer = require('mineflayer');
const bot = mineflayer.createBot({ host: '127.0.0.1', port: 25565, username: 'EmberTester', version: '1.21.11', auth: 'offline' });
let t0 = Date.now();
const log = (m) => console.log(`[${((Date.now()-t0)/1000).toFixed(1)}s] ${m}`);
function cmd(c) { log('CMD: ' + c); bot.chat(c); }
bot.on('message', (msg) => log('CHAT: ' + msg.toString()));
bot.on('error', (e) => log('ERROR: ' + e));

bot.once('spawn', async () => {
  const sleep = (ms) => new Promise(r => setTimeout(r, ms));
  cmd('/gamemode creative'); await sleep(400);
  cmd('/execute in emberfall:expedition run tp EmberTester 0 100 0'); await sleep(500);
  cmd('/execute in emberfall:expedition run fill 3 99 3 -3 99 -3 minecraft:stone'); await sleep(500);
  cmd('/emberfall selectweapon EmberTester broadsword'); await sleep(400);
  cmd('/emberfall granttome EmberTester steady_hand'); await sleep(300);
  cmd('/emberfall granttome EmberTester steady_hand'); await sleep(300);
  cmd('/emberfall granttome EmberTester steady_hand'); await sleep(300);
  cmd('/kill @e[type=emberfall:horde_zombie]'); await sleep(400);

  // Dummy A: held still, will take hits 1-3 then get set to low HP just before hit 4
  cmd('/summon emberfall:horde_zombie 3 100 0.5 {Silent:1b,PersistenceRequired:1b,NoAI:1b,Tags:["dummyA"]}');
  await sleep(500);
  cmd('/tp @s 0.5 100 0.5 facing 3 100 0.5'); await sleep(500);

  log('=== Landing hits 1-3 on dummyA (building streak, non-lethal) ===');
  await sleep(3000); // let a few normal auto-attacks land
  cmd('/data get entity @e[tag=dummyA,limit=1,sort=nearest] Health'); await sleep(500);

  log('=== Forcing dummyA to low HP so the next (4th, Empowered) hit kills it ===');
  cmd('/data merge entity @e[tag=dummyA,limit=1,sort=nearest] {Health:2.0f}'); await sleep(1500);
  cmd('/data get entity @e[tag=dummyA,limit=1,sort=nearest] Health'); await sleep(300);

  log('=== Spawning dummyB (full HP) right next to where dummyA died to check re-arm ===');
  cmd('/summon emberfall:horde_zombie 3 100 0.5 {Silent:1b,PersistenceRequired:1b,NoAI:1b,Tags:["dummyB"]}');
  await sleep(300);
  cmd('/tp @s 0.5 100 0.5 facing 3 100 0.5'); await sleep(300);
  cmd('/data get entity @e[tag=dummyB,limit=1,sort=nearest] Health'); await sleep(600);
  cmd('/data get entity @e[tag=dummyB,limit=1,sort=nearest] Health'); await sleep(600);

  cmd('/kill @e[type=emberfall:horde_zombie]');
  await sleep(500);
  bot.quit(); await sleep(1000); process.exit(0);
});
