const mineflayer = require('mineflayer');
const bot = mineflayer.createBot({host:'127.0.0.1',port:25565,username:'EmberSetup',version:'1.21.11',auth:'offline'});
bot.on('spawn', () => {
  setTimeout(() => bot.chat('/gamemode creative'), 300);
  setTimeout(() => bot.chat('/fill 200 100 200 215 100 215 minecraft:stone'), 700);
  setTimeout(() => bot.chat('/fill 200 101 200 215 105 215 minecraft:air'), 1200);
});
bot.on('message', m => console.log('CHAT: ' + JSON.stringify(m.toString())));
setTimeout(() => { bot.quit(); process.exit(0); }, 3000);
