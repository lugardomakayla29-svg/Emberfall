// Halberd swing cadence: hp of one foe sampled fast, unfrozen; prints the ms between drops. juggernaut, level 1, no grants.
const mineflayer = require('mineflayer');
const bot = mineflayer.createBot({ host: '127.0.0.1', port: 25565, username: 'EmberTester', version: '1.21.11', auth: 'offline' });
const sleep = ms => new Promise(r => setTimeout(r, ms));
const lines = []; bot.on('message', m => lines.push(m.toString())); bot.on('error', e => console.log('ERR', e.message));
const ask = async (x, w = 500) => { const n = lines.length; bot.chat(x); await sleep(w); return lines.slice(n).join(' | '); };
const hp = async () => { const r = await ask('/data get entity @e[tag=a,limit=1] Health', 300); const m = /entity data: (-?[\d.]+)f/.exec(r); return m ? +m[1] : null; };
bot.once('spawn', async () => {
  await sleep(5000);
  await ask('/gamemode survival'); await ask('/character select juggernaut'); await ask('/expedition', 2500);
  for (let i = 0; i < 40; i++) { await sleep(2000); if (/expedition/.test(await ask('/data get entity @s Dimension', 400))) break; }
  await sleep(2000);
  await ask('/effect give @s minecraft:resistance 999 4 true', 200); await ask('/emberfall wavestop 0', 300);
  await ask('/kill @e[type=!player,type=!minecraft:item_display,type=!minecraft:interaction]', 900);
  const bx = bot.entity.position.x, by = bot.entity.position.y, bz = bot.entity.position.z;
  const pin = setInterval(() => bot.chat(`/tp @s ${bx.toFixed(2)} ${by.toFixed(2)} ${bz.toFixed(2)} 0 0`), 800);
  await ask('/execute at @s run summon emberfall:horde_zombie ~ ~ ~1.5 {Tags:["a","keep"],NoAI:1b,Silent:1b,PersistenceRequired:1b}', 400);
  await ask('/attribute @e[tag=a,limit=1] minecraft:max_health base set 1024', 150);
  await ask('/data modify entity @e[tag=a,limit=1] Health set value 1024.0f', 150);
  let last = await hp(), t0 = Date.now(), drops = [];
  for (let i = 0; i < 90; i++) { const v = await hp(); if (v !== null && last !== null && v < last - 0.01) { drops.push(Date.now() - t0); t0 = Date.now(); } if (v !== null) last = v; }
  console.log('ms between hits:', drops.join(' '));
  clearInterval(pin); await ask('/kill @e[tag=keep]', 300); await ask('/expedition leave', 800);
  bot.quit(); setTimeout(() => process.exit(0), 400);
});
