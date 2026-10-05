const mineflayer = require('mineflayer');
const bot = mineflayer.createBot({ host: '127.0.0.1', port: 25565, username: 'EmberTester', version: '1.21.11', auth: 'offline' });
const sleep = ms => new Promise(r => setTimeout(r, ms));
bot.on('error', e => console.log('ERROR', e));
const parts = {}, snds = {}; let hits = 0, mobs = 0;
bot._client.on('packet', (d, m) => {
  if (m.name === 'world_particles') { const k = JSON.stringify(d.particle?.type ?? d.particleId ?? d.particle); parts[k] = (parts[k]||0) + 1; }
  if (m.name === 'sound_effect') { const k = d.sound?.soundId ?? d.soundId ?? JSON.stringify(d.sound); snds[k] = (snds[k]||0)+1; }
});
bot.on('entityHurt', e => { if (e !== bot.entity) hits++; });
bot.once('spawn', async () => {
  await sleep(6000);
  bot.chat('/gamemode survival'); await sleep(400);
  bot.chat('/effect give @s minecraft:resistance 900 4 true'); await sleep(300);
  bot.chat('/effect give @s minecraft:regeneration 900 4 true'); await sleep(300);
  bot.chat('/expedition'); await sleep(2500);
  await sleep(45000);
  mobs = Object.values(bot.entities).filter(e => e.type === 'hostile' || (e.name||'').includes('zombie') || (e.name||'').includes('skeleton') || (e.name||'').includes('spider')).length;
  console.log('PARTICLES by type:', JSON.stringify(parts));
  console.log('SOUNDS top:', JSON.stringify(Object.entries(snds).sort((a,b)=>b[1]-a[1]).slice(0,8)));
  console.log('mobs visible to client:', mobs, '| non-self hurt events:', hits);
  bot.chat('/expedition leave'); await sleep(1500); bot.quit(); process.exit(0);
});
