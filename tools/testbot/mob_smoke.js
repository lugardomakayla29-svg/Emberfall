const mineflayer = require('mineflayer');
const bot = mineflayer.createBot({ host: '127.0.0.1', port: 25565, username: 'EmberTester', version: '1.21.11', auth: 'offline' });
const sleep = ms => new Promise(r => setTimeout(r, ms));
let particles = 0; bot._client.on('packet', (d, m) => { if (m.name === 'world_particles') particles++; });
bot.on('error', e => console.log('ERROR', e));
const mobs = ['cinderbrand_reaver', 'bonecaller_necromancer', 'hydra_brain', 'plague_colossus', 'umbral_magus', 'blightfeather_marksman'];
bot.once('spawn', async () => {
  await sleep(6000);
  const c = async (x, w = 700) => { bot.chat(x); await sleep(w); };
  await c('/gamemode survival'); await c('/effect give @s minecraft:resistance 900 4 true', 400); await c('/effect give @s minecraft:regeneration 900 4 true', 400);
  await c('/tp @s 43 72 40.5', 2500);
  for (const m of mobs) {
    await c('/kill @e[type=!player,distance=..60]', 700);
    const before = particles;
    await c(`/summon emberfall:${m} 40.5 72 40.5`, 400);
    await sleep(12000);
    console.log(m.padEnd(24), 'particles in 12s:', particles - before);
  }
  await c('/kill @e[type=!player,distance=..60]', 500);
  bot.quit(); process.exit(0);
});
