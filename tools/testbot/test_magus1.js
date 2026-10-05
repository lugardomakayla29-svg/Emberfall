const mineflayer = require('mineflayer');
const bot = mineflayer.createBot({host:'127.0.0.1',port:25565,username:'EmberTester',version:'1.21.11',auth:'offline'});
bot.on('spawn', () => {
  setTimeout(() => bot.chat('/tp 300 101 300'), 300);
  setTimeout(() => bot.chat('/gamemode survival'), 900);
  setTimeout(() => bot.chat('/effect give @s minecraft:resistance 300 5'), 1400);
  setTimeout(() => bot.chat('/effect give @s minecraft:fire_resistance 300'), 1900);
  setTimeout(() => bot.chat('/emberfall spawnelite umbral_magus'), 2500);
});
bot.on('message', () => {});
setTimeout(() => { bot.quit(); process.exit(0); }, 100000);
