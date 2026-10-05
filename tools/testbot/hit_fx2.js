const mineflayer = require('mineflayer');
const bot = mineflayer.createBot({ host: '127.0.0.1', port: 25565, username: 'EmberTester', version: '1.21.11', auth: 'offline' });
const sleep = ms => new Promise(r => setTimeout(r, ms));
bot.on('error', e => console.log('ERROR', e));
const parts = {};
bot._client.on('packet', (d, m) => { if (m.name === 'world_particles') { const k = JSON.stringify(d.particle?.type ?? d.particleId); parts[k] = (parts[k]||0)+1; } });
const c = async (x, w=600) => { bot.chat(x); await sleep(w); };
bot.once('spawn', async () => {
  await sleep(6000);
  await c('/expedition leave', 800);
  await c('/gamemode creative'); await c('/tp @s 38 120 -106', 1500); await c('/gamemode survival', 1500);
  await c('/effect give @s minecraft:resistance 900 4 true'); await c('/effect give @s minecraft:regeneration 900 4 true');
  await c('/expedition', 2500);
  for (const k of Object.keys(parts)) delete parts[k];
  await sleep(60000);      // let the real waves come to the player, no artificial placement
  console.log('PARTICLES over 60s, real waves:', JSON.stringify(parts));
  await c('/expedition leave', 1200); bot.quit(); process.exit(0);
});
