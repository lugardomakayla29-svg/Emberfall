const mineflayer = require('mineflayer');
const bot = mineflayer.createBot({ host: '127.0.0.1', port: 25565, username: 'EmberTester', version: '1.21.11', auth: 'offline' });
const sleep = ms => new Promise(r => setTimeout(r, ms));
const lines = []; bot.on('message', m => lines.push(m.toString()));
bot.once('spawn', async () => {
  await sleep(6000);
  const c = async (x, w = 700) => { bot.chat(x); await sleep(w); };
  const ask = async (x, w = 700) => { const n = lines.length; bot.chat(x); await sleep(w); return lines.slice(n).join(' | '); };
  await c('/gamemode survival'); await c('/character select juggernaut');
  await c('/expedition leave', 900); await c('/expedition', 4000); for (let i = 0; i < 30; i++) { await sleep(1500); if (/expedition/.test(await ask('/data get entity @s Dimension', 400))) break; } await sleep(1500);
  await c('/execute at @s run summon emberfall:horde_zombie ~1 ~ ~0 {Tags:["kt"],PersistenceRequired:1b}', 800);
  console.log('COUNT  :', await ask('/execute if entity @e[tag=kt]', 600));
  console.log('HEALTH :', await ask('/data get entity @e[tag=kt,limit=1] Health', 700));
  await c('/attribute @e[tag=kt,limit=1] minecraft:max_health base set 400', 300);
  console.log('SETHP  :', await ask('/data merge entity @e[tag=kt,limit=1] {Health:400f}', 500));
  console.log('HEALTH2:', await ask('/data get entity @e[tag=kt,limit=1] Health', 700));
  console.log('ATTR   :', await ask('/attribute @e[tag=kt,limit=1] minecraft:max_health get', 600));
  bot.quit(); setTimeout(() => process.exit(0), 300);
});
