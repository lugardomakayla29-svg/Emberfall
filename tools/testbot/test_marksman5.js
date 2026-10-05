const mineflayer = require('mineflayer');
const bot = mineflayer.createBot({host:'127.0.0.1',port:25565,username:'EmberTester',version:'1.21.11',auth:'offline'});
bot.on('spawn', () => {
  setTimeout(() => bot.chat('/gamemode survival'), 300);
  setTimeout(() => bot.chat('/effect give @s minecraft:resistance 90 5'), 700);
  setTimeout(() => bot.chat('/effect give @s minecraft:fire_resistance 90'), 900);
  setTimeout(() => bot.chat('/emberfall spawnelite blightfeather_marksman'), 1300);
  setTimeout(() => bot.chat('/tp -110 64 -30'), 1800);
});
bot.on('message', m => console.log('CHAT: ' + JSON.stringify(m.toString())));
bot.on('health', () => console.log('HEALTH: ' + bot.health));
setTimeout(() => process.exit(0), 65000);
