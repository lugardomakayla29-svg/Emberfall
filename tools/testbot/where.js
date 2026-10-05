const mineflayer = require('mineflayer');
const bot = mineflayer.createBot({ host: '127.0.0.1', port: 25565, username: 'EmberTester', version: '1.21.11', auth: 'offline' });
const sleep = ms => new Promise(r => setTimeout(r, ms));
bot.on('error', e => console.log('ERROR', e));
bot.on('message', m => { const s = m.toString(); if (/Pos|data|Health|test/i.test(s)) console.log('CHAT:', s.slice(0, 200)); });
bot.once('spawn', async () => {
  await sleep(5000);
  const c = async (x, w = 900) => { bot.chat(x); await sleep(w); };
  await c('/tp @s 40.5 72 40.5', 1500);
  await c('/data get entity @s Pos');
  await c('/summon emberfall:horde_zombie 42.5 72 40.5 {Tags:["t1"],Silent:1b,PersistenceRequired:1b}', 1500);
  await c('/data get entity @e[tag=t1,limit=1] Pos');
  await c('/data get entity @e[tag=t1,limit=1] Health');
  await c('/execute if block 40 71 40 minecraft:air');
  await c('/execute if block 40 64 40 minecraft:air');
  await c('/kill @e[tag=t1]', 400);
  bot.quit(); process.exit(0);
});
