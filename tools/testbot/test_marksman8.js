const mineflayer = require('mineflayer');
const bot = mineflayer.createBot({host:'127.0.0.1',port:25565,username:'EmberTester',version:'1.21.11',auth:'offline'});
bot.on('spawn', () => {
  setTimeout(() => bot.chat('/tp 210 101 210'), 300);
  setTimeout(() => bot.chat('/gamemode survival'), 900);
  setTimeout(() => bot.chat('/effect give @s minecraft:resistance 150 5'), 1400);
  setTimeout(() => bot.chat('/effect give @s minecraft:fire_resistance 150'), 1900);
  setTimeout(() => bot.chat('/emberfall spawnelite blightfeather_marksman'), 2500);
});
bot.on('message', () => {});
setTimeout(() => { bot.quit(); process.exit(0); }, 40000);
