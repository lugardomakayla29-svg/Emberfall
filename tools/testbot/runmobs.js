const mineflayer = require('mineflayer');
const bot = mineflayer.createBot({ host: '127.0.0.1', port: 25565, username: 'EmberTester', version: '1.21.11', auth: 'offline' });
const sleep = ms => new Promise(r => setTimeout(r, ms));
bot.on('error', e => console.log('ERROR', e));
bot.on('message', m => { const t = m.toString(); if (/entity data|Count|passed|failed/.test(t)) console.log('  ', t.replace(/.*entity data: /, '').slice(0,120)); });
bot.once('spawn', async () => {
  await sleep(6000);
  const c = async (x, w = 800) => { bot.chat(x); await sleep(w); };
  await c('/gamemode survival', 600);
  await c('/character select battlemage', 600);
  await c('/expedition leave', 700);
  await c('/expedition', 3000);
  await c('/effect give @s minecraft:resistance 900 4 true', 300);
  await c('/effect give @s minecraft:regeneration 900 4 true', 300);
  for (let i = 0; i < 4; i++) {
    await sleep(3000);
    console.log('t+' + (i*3+3) + 's mobs within 12 / 30 / 60 blocks:');
    for (const r of [12, 30, 60]) await c(`/execute if entity @e[type=#minecraft:undead,distance=..${r}] `, 250);
    await c('/execute as @e[type=!player,limit=1,sort=nearest,distance=..60] run data get entity @s Pos', 400);
  }
  await c('/expedition leave', 700);
  bot.quit(); process.exit(0);
});
