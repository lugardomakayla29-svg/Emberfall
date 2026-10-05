const mineflayer = require('mineflayer');
const bot = mineflayer.createBot({ host: '127.0.0.1', port: 25565, username: 'EmberTester', version: '1.21.11', auth: 'offline' });
const sleep = ms => new Promise(r => setTimeout(r, ms));
bot.on('error', e => console.log('ERROR', e));
bot.on('message', m => { const s = m.toString(); if (/entity data|Equipped|Unknown|holding|Item/i.test(s)) console.log('CHAT:', s.slice(0, 220)); });
bot.once('spawn', async () => {
  await sleep(5000);
  const c = async (x, w = 900) => { bot.chat(x); await sleep(w); };
  await c('/gamemode survival', 700);
  await c('/tp @s 40.5 80 40.5', 1500);
  await c('/emberfall selectweapon EmberTester war_halberd', 700);
  await c('/data get entity @s SelectedItem');
  await c('/summon emberfall:horde_zombie 42.5 80 40.5 {Tags:["t1"],Silent:1b,PersistenceRequired:1b}', 800);
  await c('/attribute @e[tag=t1,limit=1] minecraft:movement_speed base set 0', 400);
  await c('/attribute @e[tag=t1,limit=1] minecraft:max_health base set 1000', 400);
  await c('/effect give @e[tag=t1] minecraft:instant_health 1 10 true', 500);
  await c('/data get entity @e[tag=t1,limit=1] Health');
  for (let i = 0; i < 4; i++) { await sleep(2500); await c('/data get entity @e[tag=t1,limit=1] Health', 300); }
  await c('/data get entity @s Pos');
  await c('/data get entity @e[tag=t1,limit=1] Pos');
  await c('/kill @e[tag=t1]', 400);
  bot.quit(); process.exit(0);
});
