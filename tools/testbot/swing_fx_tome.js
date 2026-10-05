// Usage: node swing_fx_test.js <character> <seconds>   -> classifies world_particles packets while a held-still mob is being hit
const mineflayer = require('mineflayer');
const bot = mineflayer.createBot({ host: '127.0.0.1', port: 25565, username: 'EmberTester', version: '1.21.11', auth: 'offline' });
const sleep = ms => new Promise(r => setTimeout(r, ms));
bot.on('error', e => console.log('ERROR', e));
const CHAR = process.argv[2], SECS = parseInt(process.argv[3] || '12', 10), DIST = process.argv[4] || '1.8';
const pk = []; let on = false, hits = 0;
bot._client.on('packet', (d, m) => {
  if (!on) return;
  if (m.name === 'world_particles' && d.particle) pk.push({ t: Date.now(), x: d.x, y: d.y, z: d.z, type: d.particle.type, col: d.particle.data && d.particle.data.color, f: d.alwaysShow });
  if (m.name === 'damage_event') hits++;
});
bot.once('spawn', async () => {
  await sleep(6000);
  const c = async (x, w = 700) => { bot.chat(x); await sleep(w); };
  await c('/gamemode survival'); await c('/character select ' + CHAR); await c('/expedition leave', 800); await c('/expedition', 4000);
  await c('/effect give @s minecraft:resistance 900 4 true', 300); await c('/effect give @s minecraft:regeneration 900 4 true', 300);
  await c('/kill @e[type=!player,distance=..80]', 800); if (process.argv[5]) { for (const t of process.argv[5].split(',')) await c('/emberfall granttome EmberTester ' + t, 500); }
  await c(`/execute at @s run summon emberfall:horde_zombie ~${DIST} ~ ~ {Tags:["t1"],Silent:1b,PersistenceRequired:1b,Attributes:[{id:"minecraft:movement_speed",base:0.0}]}`, 700);
  await c('/attribute @e[tag=t1,limit=1] minecraft:max_health base set 2000', 400);
  await c('/effect give @e[tag=t1] minecraft:instant_health 1 10 true', 400);
  const pin = setInterval(() => bot.chat('/tp @s ' + bot.entity.position.x.toFixed(2) + ' ' + bot.entity.position.y.toFixed(2) + ' ' + bot.entity.position.z.toFixed(2)), 1500);
  const hold = setInterval(() => bot.chat(`/execute at @s run tp @e[tag=t1,limit=1] ~${DIST} ~ ~`), 500);
  on = true; await sleep(SECS * 1000); on = false; clearInterval(pin); clearInterval(hold);
  await c('/data get entity @e[tag=t1,limit=1] Health', 700);
  await c('/kill @e[tag=t1]', 300); await c('/expedition leave', 800);
  const by = {};
  for (const p of pk) { const k = p.type + (p.col !== undefined ? ':' + (p.col >>> 0).toString(16) : ''); by[k] = (by[k] || 0) + 1; }
  console.log(JSON.stringify({ char: CHAR, secs: SECS, packets: pk.length, perSec: +(pk.length / SECS).toFixed(1), byType: by }));
  require('fs').writeFileSync('/tmp/swing_' + CHAR + '.json', JSON.stringify(pk));
  bot.quit(); setTimeout(() => process.exit(0), 300);
});
bot.on('message', m => { const t = m.toString(); if (/Health|has the following/i.test(t)) console.log('CHAT:', t.slice(0, 150)); });
