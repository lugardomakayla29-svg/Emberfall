// Counts packets per second from ONE Bonecaller and ONE Reaver, so the cost of the new telegraphs is a number, not a guess.
const mineflayer = require('mineflayer');
const bot = mineflayer.createBot({ host: '127.0.0.1', port: 25565, username: 'EmberTester', version: '1.21.11', auth: 'offline' });
const sleep = ms => new Promise(r => setTimeout(r, ms));
const buckets = {}; let tag = null;
bot._client.on('packet', (d, m) => { if (tag && m.name === 'world_particles') { const t = d.particle && d.particle.type; buckets[tag] = buckets[tag] || {}; buckets[tag][t] = (buckets[tag][t] || 0) + 1; } });
bot.once('spawn', async () => {
  await sleep(6000);
  const c = async (x, w = 700) => { bot.chat(x); await sleep(w); };
  await c('/gamemode survival'); await c('/effect give @s minecraft:resistance 900 4 true', 400); await c('/effect give @s minecraft:regeneration 900 4 true', 400);
  await c('/tp @s 43 72 40.5', 2500);
  for (const m of ['bonecaller_necromancer', 'cinderbrand_reaver']) {
    await c('/kill @e[type=!player,distance=..60]', 700);
    await c(`/summon emberfall:${m} 40.5 72 40.5`, 800);
    tag = m; await sleep(30000); tag = null;
    const b = buckets[m]; const total = Object.values(b).reduce((a, v) => a + v, 0);
    console.log(`${m}: ${total} particle packets in 30s = ${(total / 30).toFixed(1)}/s`);
    console.log('  dust (the new telegraph type):', b.dust || 0, ' -> ' + ((b.dust || 0) / 30).toFixed(1) + '/s');
  }
  await c('/kill @e[type=!player,distance=..60]', 500);
  bot.quit(); process.exit(0);
});
