const mineflayer = require('mineflayer');
const bot = mineflayer.createBot({ host: '127.0.0.1', port: 25565, username: 'EmberTester', version: '1.21.11', auth: 'offline' });
const sleep = ms => new Promise(r => setTimeout(r, ms));
const chat = []; bot.on('message', m => chat.push(m.toString()));
bot.once('spawn', async () => {
  await sleep(5000);
  bot.chat('/gamemode survival'); await sleep(600);
  bot.chat('/kill @e[type=!player,distance=..60]'); await sleep(500);
  bot.chat('/summon emberfall:corrupted_sentinel 40.5 72 40.5 {Tags:["yp"]}'); await sleep(2500);
  chat.length = 0; bot.chat('/data get entity @e[tag=yp,limit=1] Pos'); await sleep(900);
  console.log('Sentinel Pos after settling:', chat.join(' | ').slice(0, 160));
  bot.chat('/kill @e[tag=yp]'); await sleep(400);
  bot.quit(); process.exit(0);
});
