const mineflayer = require('mineflayer');
const bot = mineflayer.createBot({host:'127.0.0.1',port:25565,username:'EmberTester',version:'1.21.11',auth:'offline'});
bot.on('spawn', () => {
  setTimeout(() => bot.chat('/gamemode creative'), 500);
  setTimeout(() => bot.chat('/kill @e[type=!minecraft:player]'), 1200);
});
bot.on('message', m => console.log('CHAT: ' + m.toString()));
setTimeout(() => { bot.quit(); process.exit(0); }, 3000);
