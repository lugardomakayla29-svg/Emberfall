const mineflayer = require('mineflayer');
const sleep = ms => new Promise(r => setTimeout(r, ms));
const bot = mineflayer.createBot({ host: '127.0.0.1', port: 25565, username: 'EmberTester', version: '1.21.11', auth: 'offline' });
const lines = []; bot.on('message', m => lines.push(m.toString())); bot.on('error', e => console.log('ERR', e.message));
const ask = async (x, w = 500) => { const n = lines.length; bot.chat(x); await sleep(w); return lines.slice(n).join(' | '); };
bot.once('spawn', async () => {
  await sleep(5000);
  await ask('/gamemode survival'); await ask('/character select juggernaut'); await ask('/expedition', 2500);
  for (let i = 0; i < 40; i++) { await sleep(2000); if (/expedition/.test(await ask('/data get entity @s Dimension', 400))) break; }
  await sleep(2000);
  await ask('/emberfall wavestop 0', 300);
  await ask('/kill @e[type=!player,type=!minecraft:item_display,type=!minecraft:interaction]', 900);
  console.log('SUMMON:', (await ask('/execute at @s run summon minecraft:spider ~6 ~ ~ {Tags:["sp"],PersistenceRequired:1b,NoAI:1b}', 700)).slice(0, 160));
  console.log('COUNT :', (await ask('/execute if entity @e[tag=sp]', 300)).slice(0, 100));
  console.log('POS   :', (await ask('/data get entity @e[tag=sp,limit=1] Pos', 400)).slice(0, 200));
  await ask('/kill @e[tag=sp]', 300); await ask('/expedition leave', 800);
  bot.quit(); setTimeout(() => process.exit(0), 300);
});
