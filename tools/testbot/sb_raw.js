const mineflayer = require('${process.env.EMBERFALL_HOME}/bot/node_modules/mineflayer');
const sleep = ms => new Promise(r => setTimeout(r, ms));
const bot = mineflayer.createBot({ host: '127.0.0.1', port: 25565, username: 'EmberTester', version: '1.21.11', auth: 'offline' });
const lines = []; bot.on('message', m => lines.push(m.toString()));
const ask = async (x, w = 500) => { const n = lines.length; bot.chat(x); await sleep(w); return lines.slice(n).join(' | '); };
bot.once('spawn', async () => {
  await sleep(5000); await ask('/gamemode survival'); await ask('/character select juggernaut'); await ask('/expedition', 2500);
  for (let i = 0; i < 40; i++) { await sleep(2000); if (/expedition/.test(await ask('/data get entity @s Dimension', 400))) break; }
  await sleep(2000); await ask('/emberfall wavestop 0', 300);
  await ask('/kill @e[type=!player,type=!minecraft:item_display,type=!minecraft:interaction]', 900);
  await ask('/execute at @s positioned ~8 ~ ~ run emberfall spawnveteran horde_shieldbearer', 500);
  await ask('/tag @e[type=emberfall:horde_shieldbearer,limit=1] add sb', 200);
  console.log('EQUIP:', (await ask('/data get entity @e[tag=sb,limit=1] equipment', 400)).slice(0, 300));
  console.log('NAME :', (await ask('/data get entity @e[tag=sb,limit=1] CustomName', 400)).slice(0, 200));
  console.log('HP   :', (await ask('/data get entity @e[tag=sb,limit=1] Health', 300)).slice(0, 100));
  await ask('/kill @e[tag=sb]', 300); await ask('/expedition leave', 800);
  bot.quit(); setTimeout(() => process.exit(0), 300);
});
