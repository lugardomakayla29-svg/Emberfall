const mineflayer = require('mineflayer');
const bot = mineflayer.createBot({ host: '127.0.0.1', port: 25565, username: 'EmberTester', version: '1.21.11', auth: 'offline' });
const sleep = ms => new Promise(r => setTimeout(r, ms));
bot.on('error', e => console.log('ERROR', e));
const counts = {};
bot._client.on('packet', (data, meta) => { if (['world_particles','sound_effect','entity_sound_effect','named_sound_effect','spawn_entity'].includes(meta.name)) counts[meta.name] = (counts[meta.name]||0)+1; });
function snapshot(){ const c={...counts}; return c; }
function delta(a,b){ const o={}; for (const k of new Set([...Object.keys(a),...Object.keys(b)])) o[k]=(b[k]||0)-(a[k]||0); return o; }
bot.once('spawn', async () => {
  await sleep(6000);
  bot.chat('/gamemode survival'); await sleep(400);
  bot.chat('/effect give @s minecraft:resistance 900 4 true'); await sleep(300);
  bot.chat('/effect give @s minecraft:regeneration 900 4 true'); await sleep(300);
  const idle0 = snapshot(); await sleep(10000); const idle1 = snapshot();
  console.log('IDLE 10s (no run):', JSON.stringify(delta(idle0, idle1)));
  bot.chat('/expedition'); await sleep(2500);
  const r0 = snapshot(); await sleep(30000); const r1 = snapshot();
  console.log('RUN 30s (waves + auto-attack):', JSON.stringify(delta(r0, r1)));
  bot.chat('/expedition leave'); await sleep(1500);
  bot.quit(); process.exit(0);
});
