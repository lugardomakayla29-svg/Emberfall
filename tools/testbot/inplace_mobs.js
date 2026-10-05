const mineflayer = require('mineflayer');
const bot = mineflayer.createBot({ host: '127.0.0.1', port: 25565, username: 'EmberTester', version: '1.21.11', auth: 'offline' });
const t0 = Date.now(); const log = m => console.log(`[${((Date.now()-t0)/1000).toFixed(1)}s] ${m}`);
const sleep = ms => new Promise(r => setTimeout(r, ms));
bot.on('error', e => log('ERROR: ' + e));
const mobs = () => Object.values(bot.entities).filter(e => e !== bot.entity && e.type !== 'player' && e.position.distanceTo(bot.entity.position) < 60);
bot.once('spawn', async () => {
  await sleep(4000);
  bot.chat('/gamemode survival'); await sleep(400);
  bot.chat('/effect give @s minecraft:resistance 600 4 true'); await sleep(300);
  bot.chat('/effect give @s minecraft:regeneration 600 4 true'); await sleep(300);
  bot.chat('/expedition'); await sleep(1500);
  for (let i = 0; i < 6; i++) {
    await sleep(5000);
    const m = mobs(); const names = {};
    m.forEach(e => { const k = e.name || e.displayName || String(e.type); names[k] = (names[k] || 0) + 1; });
    const dists = m.filter(e => e.type === 'hostile' || e.type === 'mob').map(e => e.position.distanceTo(bot.entity.position));
    log(`t+${(i+1)*5}s entities within 60: ${m.length} ${JSON.stringify(names)} nearest=${dists.length ? Math.min(...dists).toFixed(1) : '-'} hp=${bot.health}`);
  }
  bot.chat('/expedition leave'); await sleep(3500);
  log(`after leave, entities within 60: ${mobs().length}`);
  bot.quit(); await sleep(500); process.exit(0);
});
