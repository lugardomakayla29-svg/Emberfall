const mineflayer = require('mineflayer');
const bot = mineflayer.createBot({ host: '127.0.0.1', port: 25565, username: 'EmberTester', version: '1.21.11', auth: 'offline' });
const sleep = ms => new Promise(r => setTimeout(r, ms));
bot.on('error', e => console.log('ERROR', e));
bot.on('message', m => { const t = m.toString(); if (/entity data/.test(t)) console.log('  ', t.replace(/.*entity data: /, '')); });
bot.once('spawn', async () => {
  await sleep(6000);
  const c = async (x, w = 800) => { bot.chat(x); await sleep(w); };
  await c('/gamemode survival', 600);
  await c('/character select battlemage', 600);
  await c('/expedition leave', 700);
  await c('/expedition', 4000);
  await c('/kill @e[type=!player,distance=..60]', 700);
  await c('/execute at @s run summon emberfall:horde_zombie ~5 ~ ~ {Tags:["d1"],Silent:1b,PersistenceRequired:1b}', 100);
  console.log('right after spawn (want x = player+5):');
  await c('/data get entity @s Pos', 300);
  await c('/data get entity @e[tag=d1,limit=1] Pos', 300);
  await c('/attribute @e[tag=d1,limit=1] minecraft:movement_speed base set 0', 300);
  for (let i = 1; i <= 4; i++) { await sleep(2000); console.log('after ' + (i * 2) + 's:'); await c('/data get entity @e[tag=d1,limit=1] Pos', 300); }
  await c('/kill @e[tag=d1]', 300); await c('/expedition leave', 700);
  bot.quit(); process.exit(0);
});
