const mineflayer = require('mineflayer');
const bot = mineflayer.createBot({ host: '127.0.0.1', port: 25565, username: 'EmberTester', version: '1.21.11', auth: 'offline' });
const t0 = Date.now();
const log = m => console.log(`[${((Date.now()-t0)/1000).toFixed(1)}s] ${m}`);
const sleep = ms => new Promise(r => setTimeout(r, ms));
bot.on('message', m => { const s = m.toString(); if (s.trim()) log('CHAT: ' + s); });
bot.on('error', e => log('ERROR: ' + e));
bot.on('kicked', r => log('KICKED: ' + r));
function hashArea() {
  const p = bot.entity.position.floored(); let h = 0, n = 0;
  for (let dx = -30; dx <= 30; dx++) for (let dz = -30; dz <= 30; dz++) for (let dy = -10; dy <= 12; dy++) {
    const b = bot.blockAt(p.offset(dx, dy, dz)); if (!b) continue;
    h = (h * 31 + b.stateId + (dx * 7 + dy * 13 + dz * 17)) | 0; n++;
  }
  return `${h}/${n}`;
}
bot.once('spawn', async () => {
  await sleep(4000);
  const start = bot.entity.position.clone();
  log(`spawned in ${bot.game.dimension} at ${start}`);
  bot.chat('/gamemode survival'); await sleep(600);
  bot.chat('/effect give @s minecraft:resistance 600 4 true'); await sleep(400);
  const before = hashArea(); log('AREA HASH before: ' + before);
  bot.chat('/expedition'); await sleep(3000);
  const after = bot.entity.position;
  log(`dimension now: ${bot.game.dimension}; moved ${start.distanceTo(after).toFixed(2)} blocks`);
  await sleep(20000);
  bot.chat('/expedition leave'); await sleep(3500);
  const restored = hashArea(); log('AREA HASH after leave: ' + restored);
  log(before === restored ? 'RESULT: terrain IDENTICAL after run' : 'RESULT: TERRAIN DIFFERS');
  bot.quit(); await sleep(800); process.exit(0);
});
