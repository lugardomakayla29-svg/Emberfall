const mineflayer = require('mineflayer');
const bot = mineflayer.createBot({host:'127.0.0.1',port:25565,username:'EmberTester',version:'1.21.11',auth:'offline'});
bot.on('spawn', () => {
  setTimeout(() => bot.chat('/tp 210 101 210'), 500);
  setTimeout(() => bot.chat('/gamemode survival'), 1500);
  setTimeout(() => bot.chat('/effect give @s minecraft:resistance 300 5'), 2200);
  setTimeout(() => bot.chat('/effect give @s minecraft:fire_resistance 300'), 2900);
  setTimeout(() => bot.chat('/emberfall spawnelite umbral_magus'), 3600);
});
bot.on('message', m => { const s = m.toString(); if (!/^$/.test(s)) console.log('CHAT: ' + s); });
setTimeout(() => { bot.quit(); process.exit(0); }, 120000);
