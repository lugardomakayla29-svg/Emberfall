const mineflayer = require('mineflayer');
const bot = mineflayer.createBot({ host: '127.0.0.1', port: 25565, username: 'EmberTester', version: '1.21.11', auth: 'offline' });
const sleep = ms => new Promise(r => setTimeout(r, ms));
const lines = []; bot.on('message', m => lines.push(m.toString()));
bot.once('spawn', async () => {
  await sleep(6000);
  const c = async (x, w = 700) => { bot.chat(x); await sleep(w); };
  const ask = async (x, w = 700) => { const n = lines.length; bot.chat(x); await sleep(w); return lines.slice(n).join(' | '); };
  const AS = async () => (await ask('/attribute @s minecraft:attack_speed get', 600)).match(/value[^\d-]*(-?[\d.]+)/)?.[1];
  await c('/gamemode survival'); await c('/character select vanguard');
  await c('/expedition leave', 900); await c('/expedition', 4000); for (let i = 0; i < 30; i++) { await sleep(1500); if (/expedition/.test(await ask('/data get entity @s Dimension', 400))) break; } await sleep(1500);
  const purge = setInterval(() => bot.chat('/kill @e[type=!player,type=!item]'), 1500);
  console.log('no relic        : attack_speed', await AS());
  for (const n of [1, 3, 5]) { await c(`/emberfall relic give EmberTester wizards_cowl ${n === 1 ? 1 : 2}`, 500); console.log(`cowl stacks -> ${(await ask('/emberfall relic state EmberTester', 600)).match(/wizards_cowl=(\d+)/)?.[1]} : attack_speed`, await AS()); }
  console.log('state           :', (await ask('/emberfall relic state EmberTester', 700)).slice(0, 120));
  console.log('pool cap check  :', (await ask('/emberfall relic list', 900)).match(/wizards_cowl[^|]*/)?.[0]?.slice(0, 100));
  clearInterval(purge); bot.quit(); setTimeout(() => process.exit(0), 300);
});
