const mineflayer = require('mineflayer');
const bot = mineflayer.createBot({ host: '127.0.0.1', port: 25565, username: 'EmberTester', version: '1.21.11', auth: 'offline' });
const sleep = ms => new Promise(r => setTimeout(r, ms));
const lines = []; bot.on('message', m => lines.push(m.toString()));
bot.once('spawn', async () => {
  await sleep(6000);
  const c = async (x, w = 700) => { bot.chat(x); await sleep(w); };
  const ask = async (x, w = 700) => { const n = lines.length; bot.chat(x); await sleep(w); return lines.slice(n).join(' | '); };
  const H = async () => (await ask('/data get entity @s Health', 600)).match(/entity data: (-?[\d.]+)f/)?.[1];
  await c('/gamemode survival'); await c('/character select vanguard');
  await c('/expedition leave', 900); await c('/expedition', 4000); for (let i = 0; i < 30; i++) { await sleep(1500); if (/expedition/.test(await ask('/data get entity @s Dimension', 400))) break; } await sleep(1500);
  await c('/effect clear @s', 300);
  console.log('start      :', await H());
  console.log('damage     :', await ask('/damage @s 10 minecraft:generic', 700));
  console.log('after dmg  :', await H());
  await sleep(3000);
  console.log('3 s later  :', await H(), '(natural recovery)');
  bot.quit(); setTimeout(() => process.exit(0), 300);
});
