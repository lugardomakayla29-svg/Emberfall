const mineflayer = require('mineflayer');
const bot = mineflayer.createBot({ host: '127.0.0.1', port: 25565, username: 'EmberTester', version: '1.21.11', auth: 'offline' });
const sleep = ms => new Promise(r => setTimeout(r, ms));
bot.on('error', e => console.log('ERROR', e));
bot.once('spawn', async () => {
  await sleep(4000);
  bot.chat('/gamemode survival'); await sleep(400);
  bot.chat('/effect give @s minecraft:resistance 300 4 true'); await sleep(300);
  bot.chat('/tp @s 200 119 215'); await sleep(2000);
  bot.chat('/kill @e[tag=vt]'); await sleep(300);
  bot.chat('/summon minecraft:villager 200 119 192 {Tags:["vt"],Invulnerable:1b,NoAI:1b}'); await sleep(400);
  bot.chat('/summon emberfall:plague_colossus 200 119 204 {Tags:["vt"],Invulnerable:1b}'); await sleep(400);
  for (let i = 0; i < 4; i++) { await sleep(1500); bot.chat('/data get entity @e[type=emberfall:plague_colossus,limit=1] Brain'); bot.chat('/data get entity @e[type=emberfall:plague_colossus,limit=1] AngryAt'); }
  bot.chat('/kill @e[tag=vt]'); await sleep(300); bot.quit(); process.exit(0);
});
