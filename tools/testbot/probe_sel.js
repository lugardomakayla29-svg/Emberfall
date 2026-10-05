const mineflayer = require('mineflayer');
const bot = mineflayer.createBot({ host: '127.0.0.1', port: 25565, username: 'EmberTester', version: '1.21.11', auth: 'offline' });
const sleep = ms => new Promise(r => setTimeout(r, ms));
const lines = []; bot.on('message', m => lines.push(m.toString()));
const ask = async (x, w = 500) => { const n = lines.length; bot.chat(x); await sleep(w); return lines.slice(n).join(' | '); };
bot.once('spawn', async () => {
  await sleep(4000); await ask('/op EmberTester'); await ask('/gamemode survival');
  await ask('/character select ranger'); await ask('/expedition', 2500);
  for (let i = 0; i < 40; i++) { await sleep(2000); if (/expedition/.test(await ask('/data get entity @s Dimension', 400))) break; }
  await sleep(2000); await ask('/emberfall wavestop 0', 300);
  await ask('/execute at @s run summon minecraft:zombie ~4 ~ ~0 {Tags:["t","s0"],NoAI:1b,Silent:1b,PersistenceRequired:1b,CustomName:\'{"text":"Test Summon"}\'}', 400);
  await sleep(1500);
  console.log('A tag count     :', await ask('/execute if entity @e[tag=s0]', 500));
  console.log('B data Health   :', await ask('/data get entity @e[tag=s0,limit=1] Health', 500));
  console.log('C type count    :', await ask('/execute if entity @e[type=minecraft:zombie]', 500));
  console.log('D 5s later      :', await ask('/execute if entity @e[tag=s0]', 500));
  await sleep(5000);
  console.log('E after 5s      :', await ask('/execute if entity @e[tag=s0]', 500));
  await ask('/kill @e[tag=s0]', 300); await ask('/expedition leave', 800); bot.quit(); setTimeout(() => process.exit(0), 300);
});
