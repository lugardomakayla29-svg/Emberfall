// Pink Slime particles on the wire: every particle packet near a hopping Pink Slime, by particle type and (for item
// particles) the item carried. Green = minecraft:item_slime; pink = minecraft:item with emberfall:pink_slime_glob.
const mineflayer = require('mineflayer');
const bot = mineflayer.createBot({ host: '127.0.0.1', port: 25565, username: 'EmberTester', version: '1.21.11', auth: 'offline' });
const sleep = ms => new Promise(r => setTimeout(r, ms));
const lines = []; bot.on('message', m => lines.push(m.toString()));
const ask = async (x, w = 600) => { const n = lines.length; bot.chat(x); await sleep(w); return lines.slice(n).join(' | '); };
const seen = {};
let recording = false;
bot._client.on('packet', (data, meta) => {
  if (!recording || meta.name !== 'world_particles') return;
  const p = data.particle || {};
  if (p.type === 'item' && !global.dumped) { global.dumped = 1; console.log('ITEM_PACKET', JSON.stringify(p).slice(0, 300)); }
  let key = p.type !== undefined ? String(p.type) : JSON.stringify(Object.keys(data));
  if (p.data && p.data.item) key += ' item=' + (p.data.item.itemId !== undefined ? p.data.item.itemId : JSON.stringify(p.data.item).slice(0, 60));
  seen[key] = (seen[key] || 0) + (data.amount || 1);
});
bot.once('spawn', async () => {
  await sleep(6000);
  await ask('/gamemode survival'); await ask('/character select vanguard', 700); await ask('/expedition leave', 800); await ask('/expedition', 3500);
  await ask('/effect give @s minecraft:resistance 900 4 true', 300); await ask('/effect give @s minecraft:regeneration 900 4 true', 300);
  await ask('/emberfall wavestop 0', 500); await ask('/kill @e[type=!player]', 800);
  const pin = setInterval(() => bot.chat('/tp @s ' + bot.entity.position.x.toFixed(2) + ' ' + bot.entity.position.y.toFixed(2) + ' ' + bot.entity.position.z.toFixed(2)), 500);
  await ask('/execute at @s positioned ~9 ~ ~ run emberfall spawnelite pink_slime', 1500);
  recording = true;
  await sleep(20000);
  recording = false;
  console.log('PARTICLE_COUNTS', JSON.stringify(seen));
  clearInterval(pin); await ask('/expedition leave', 800);
  bot.quit(); setTimeout(() => process.exit(0), 300);
});
bot.on('error', e => console.log('ERR', e.message));
