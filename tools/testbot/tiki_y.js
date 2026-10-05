const mineflayer = require('mineflayer');
const bot = mineflayer.createBot({ host: '127.0.0.1', port: 25565, username: 'EmberTester', version: '1.21.11', auth: 'offline' });
const sleep = ms => new Promise(r => setTimeout(r, ms));
bot.on('error', e => console.log('ERROR', e));
bot.on('message', m => { const t=m.toString(); if (/following entity data|scale/.test(t)) console.log(t.replace(/^.*data: /,'').slice(0,70)); });
const c = async (x, w=500) => { bot.chat(x); await sleep(w); };
const which = process.argv[2] || 'tiki_magma';
bot.once('spawn', async () => {
  await sleep(6000);
  await c('/gamemode creative'); await c('/tp @s 0 200 8', 1500);
  await c('/kill @e[type=!player,distance=..40]', 600);
  await c(`/emberfall spawnelite ${which}`, 1500);
  await c('/execute at @s run tp @e[type=emberfall:tiki_magma,limit=1] 0 200 0', 500);
  await c('/attribute @e[type=emberfall:tiki_magma,limit=1] minecraft:movement_speed base set 0', 2000);
  console.log('--- mob'); await c('/data get entity @e[type=emberfall:tiki_magma,limit=1] Pos[1]', 500);
  console.log('--- scale'); await c('/attribute @e[type=emberfall:tiki_magma,limit=1] minecraft:scale get', 500);
  // Read segments one at a time in ascending height: exclude ones already read by tagging them.
  for (let i = 0; i < 5; i++) {
    bot.chat('/data get entity @e[type=emberfall:tiki_segment,tag=!read,limit=1,sort=nearest,x=0,y=200,z=0] Pos[1]'); await sleep(450);
    bot.chat('/tag @e[type=emberfall:tiki_segment,tag=!read,limit=1,sort=nearest,x=0,y=200,z=0] add read'); await sleep(350);
  }
  bot.quit(); process.exit(0);
});
