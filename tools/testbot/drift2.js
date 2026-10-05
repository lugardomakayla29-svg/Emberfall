const mineflayer = require('mineflayer');
const bot = mineflayer.createBot({ host: '127.0.0.1', port: 25565, username: 'EmberTester', version: '1.21.11', auth: 'offline' });
const sleep = ms => new Promise(r => setTimeout(r, ms));
bot.on('error', e => console.log('ERROR', e));
bot.on('message', m => { const t = m.toString(); if (/entity data/.test(t)) console.log('  ', t.replace(/.*entity data: /, '').slice(0,90)); });
const RUN = process.argv[2] === 'run';
bot.once('spawn', async () => {
  await sleep(6000);
  const c = async (x, w = 700) => { bot.chat(x); await sleep(w); };
  await c('/gamemode survival', 600);
  await c('/tp @s 40.5 80 40.5', 1200);
  if (RUN) { await c('/character select battlemage', 600); await c('/expedition leave', 700); await c('/expedition', 3500); await c('/kill @e[type=!player,distance=..60]', 700); }
  console.log(RUN ? 'IN A RUN' : 'NO RUN');
  await c('/data get entity @s Pos', 300);
  await c('/execute at @s run summon emberfall:horde_zombie ~4.5 ~ ~ {Tags:["d1"],Silent:1b,PersistenceRequired:1b,Attributes:[{id:"minecraft:movement_speed",base:0.0}]}', 150);
  for (const w of [0, 400, 1000, 2000]) { await sleep(w ? 300 : 0); await c('/data get entity @e[tag=d1,limit=1] Pos', 250); }
  await c('/kill @e[tag=d1]', 300); if (RUN) await c('/expedition leave', 700);
  bot.quit(); process.exit(0);
});
