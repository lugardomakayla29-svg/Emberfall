const mineflayer = require('mineflayer');
const bot = mineflayer.createBot({host:'127.0.0.1',port:25565,username:'EmberTester',version:'1.21.11',auth:'offline'});
bot.on('spawn', () => {
  setTimeout(() => bot.chat('/gamemode creative'), 300);
  setTimeout(() => bot.chat('/effect give @s minecraft:resistance 60 5'), 600);
  setTimeout(() => bot.chat('/emberfall spawnelite blightfeather_marksman'), 1000);
});
bot.on('message', m => console.log('CHAT: ' + JSON.stringify(m.toString())));
bot.on('health', () => console.log('HEALTH: ' + bot.health));
setTimeout(() => process.exit(0), 40000);
