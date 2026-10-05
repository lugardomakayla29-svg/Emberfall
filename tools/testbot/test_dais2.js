const mineflayer = require('mineflayer');
const { Vec3 } = require('vec3');
const bot = mineflayer.createBot({ host: '127.0.0.1', port: 25565, username: 'EmberTester', version: '1.21.11', auth: 'offline' });
let t0 = Date.now();
const log = (m) => console.log(`[${((Date.now()-t0)/1000).toFixed(1)}s] ${m}`);
function cmd(c) { log('CMD: ' + c); bot.chat(c); }
bot.on('message', (msg) => log('CHAT: ' + msg.toString()));
bot.on('error', (e) => log('ERROR: ' + e));

bot.once('spawn', async () => {
  const sleep = (ms) => new Promise(r => setTimeout(r, ms));
  cmd('/gamemode survival'); await sleep(500);
  cmd('/effect give @s minecraft:resistance 300 0'); await sleep(300); // small buffer so we don't die outright, but resistance 0 (level 1) still lets us see damage happen (20% reduction), fine for observing a drop
  cmd('/expedition'); await sleep(2500);
  cmd('/emberfall boss 0'); await sleep(2000);

  cmd('/data get entity @s Health'); await sleep(600);
  log('--- teleporting into moat ---');
  cmd('/tp EmberTester 11.5 65 15.5'); await sleep(500);
  await sleep(2500);
  cmd('/data get entity @s Health'); await sleep(600);
  await sleep(2500);
  cmd('/data get entity @s Health'); await sleep(600);

  log('--- back on dais (should stop taking damage) ---');
  cmd('/tp EmberTester 11.5 67 11.5'); await sleep(500);
  await sleep(2500);
  cmd('/data get entity @s Health'); await sleep(600);

  log('--- waiting near brain for slam telegraph (up to 17s) ---');
  const startPos = bot.entity.position.clone();
  for (let i = 0; i < 17; i++) {
    await sleep(1000);
    const p = bot.entity.position;
    const moved = p.distanceTo(startPos);
    if (moved > 0.6) { log(`tick ${i+1}s: KNOCKBACK DETECTED, moved ${moved.toFixed(2)} blocks, pos=${p}`); break; }
  }
  cmd('/data get entity @s Health'); await sleep(600);

  await sleep(500);
  cmd('/expedition leave'); await sleep(1500);
  bot.quit(); await sleep(1000); process.exit(0);
});
