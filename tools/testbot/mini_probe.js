const mineflayer = require('mineflayer');
const bot = mineflayer.createBot({ host: '127.0.0.1', port: 25565, username: 'EmberTester', version: '1.21.11', auth: 'offline' });
const sleep = ms => new Promise(r => setTimeout(r, ms));
const lines = []; bot.on('message', m => lines.push(m.toString()));
const ask = async (x, w = 700) => { const n = lines.length; bot.chat(x); await sleep(w); return lines.slice(n).join(' | '); };
bot.once('spawn', async () => {
  await sleep(6500);
  await ask('/gamemode creative'); await ask('/kill @e[type=!player]', 600); await sleep(2000);
  console.log('summon:', (await ask('/summon emberfall:devourer_spawn ~3 ~ ~', 900)).slice(0, 160));
  await sleep(1500);
  console.log('mini data:', (await ask('/data get entity @e[type=emberfall:devourer_spawn,limit=1] Health', 700)).slice(0, 160));
  console.log('displays near:', (await ask('/execute as @e[type=minecraft:item_display] run data get entity @s Passengers', 800)).slice(0, 200));
  console.log('riding:', (await ask('/execute if entity @e[type=minecraft:item_display,nbt={item:{id:"minecraft:player_head"}}]', 700)).slice(0, 120));
  await ask('/kill @e[type=emberfall:devourer_spawn]', 700); await sleep(3500);
  console.log('after kill, displays left:', (await ask('/execute if entity @e[type=minecraft:item_display]', 700)).slice(0, 120));
  bot.quit(); setTimeout(() => process.exit(0), 300);
});
bot.on('error', e => console.log('ERR', e.message));
