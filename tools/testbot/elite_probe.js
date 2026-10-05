const mineflayer = require('mineflayer');
const bot = mineflayer.createBot({ host: '127.0.0.1', port: 25565, username: 'EmberTester', version: '1.21.11', auth: 'offline' });
const sleep = ms => new Promise(r => setTimeout(r, ms));
const lines = []; bot.on('message', m => lines.push(m.toString()));
bot.once('spawn', async () => {
  await sleep(6000);
  const c = async (x, w = 600) => { bot.chat(x); await sleep(w); };
  const ask = async (x, w = 800) => { const n = lines.length; bot.chat(x); await sleep(w); return lines.slice(n).join(' | ').slice(0, 260); };
  await c('/gamemode survival'); await c('/character select vanguard', 500);
  await c('/expedition leave', 900); await c('/expedition', 4000);
  for (let i = 0; i < 30; i++) { await sleep(1500); if (/expedition/.test(await ask('/data get entity @s Dimension', 400))) break; } await sleep(2500);
  console.log('ELITE (no wavestop):', await ask('/emberfall relic elite EmberTester', 1000));
  console.log('TAGGED NOW:', await ask('/execute as @e[tag=emberfall_elite] run say TAGGED', 700));
  await c('/emberfall wavestop 0', 500);
  console.log('ELITE (after wavestop):', await ask('/emberfall relic elite EmberTester', 1000));
  console.log('TAGGED NOW:', await ask('/execute as @e[tag=emberfall_elite] run say TAGGED', 700));
  await c('/expedition leave', 800); bot.quit(); setTimeout(() => process.exit(0), 400);
});
