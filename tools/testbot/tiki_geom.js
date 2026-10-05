const mineflayer = require('mineflayer');
const bot = mineflayer.createBot({ host: '127.0.0.1', port: 25565, username: 'EmberTester', version: '1.21.11', auth: 'offline' });
const sleep = ms => new Promise(r => setTimeout(r, ms));
bot.on('error', e => console.log('ERROR', e));
bot.on('message', m => { const t=m.toString(); if (/Test (passed|failed)/.test(t)) console.log('   ->', t); });
const c = async (x, w=500) => { bot.chat(x); await sleep(w); };
bot.once('spawn', async () => {
  await sleep(6000);
  await c('/gamemode creative'); await c('/tp @s 0 200 8', 1500);
  await c('/kill @e[type=!player,distance=..40]', 600);
  await c('/emberfall spawnelite tiki_magma', 1500);
  await c('/execute at @s run tp @e[type=emberfall:tiki_magma,limit=1] 0 200 0', 500);
  await c('/attribute @e[type=emberfall:tiki_magma,limit=1] minecraft:movement_speed base set 0', 1500);
  // Vertical layout: for each 0.5 block band above the floor (y=200), is a tiki_segment present within 0.6 of the pole axis?
  for (let h = 0; h <= 3.5; h += 0.5) {
    const y = 200 + h;
    bot.chat(`/execute if entity @e[type=emberfall:tiki_segment,x=0,y=${y},z=0,dx=0.9,dy=0.4,dz=0.9]`);
    console.log(`band y=${h.toFixed(1)}..${(h+0.4).toFixed(1)}`); await sleep(450);
  }
  bot.quit(); process.exit(0);
});
