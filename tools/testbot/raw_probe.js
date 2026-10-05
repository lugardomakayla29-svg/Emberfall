const mineflayer = require('mineflayer');
const bot = mineflayer.createBot({ host: '127.0.0.1', port: 25565, username: 'EmberTester', version: '1.21.11', auth: 'offline' });
const sleep = ms => new Promise(r => setTimeout(r, ms));
bot.on('message', m => console.log('MSG>', m.toString()));
bot.once('spawn', async () => {
  await sleep(4000);
  bot.chat('/gamemode creative'); await sleep(400);
  bot.chat('/kill @e[tag=vt]'); await sleep(300);
  bot.chat('/summon minecraft:villager ~ ~ ~3 {Tags:["vt","vil"],NoAI:1b}'); await sleep(500);
  bot.chat('/execute if entity @e[tag=vil,distance=..10]'); await sleep(500);
  bot.chat('/execute if entity @e[tag=vil,distance=..1]'); await sleep(500);
  bot.chat('/kill @e[tag=vt]'); await sleep(300);
  bot.quit(); process.exit(0);
});
