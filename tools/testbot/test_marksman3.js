const mineflayer = require('mineflayer');
const bot = mineflayer.createBot({host:'127.0.0.1',port:25565,username:'EmberTester',version:'1.21.11',auth:'offline'});
bot.on('spawn', () => {
  setTimeout(() => bot.chat('/gamemode creative'), 300);
  setTimeout(() => bot.chat('/effect give @s minecraft:resistance 90 5'), 700);
  setTimeout(() => bot.chat('/emberfall spawnelite blightfeather_marksman'), 1200);
});
bot.on('message', m => console.log('CHAT: ' + JSON.stringify(m.toString())));
setTimeout(() => process.exit(0), 55000);
