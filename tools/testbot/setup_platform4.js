const mineflayer = require('mineflayer');
const bot = mineflayer.createBot({host:'127.0.0.1',port:25565,username:'EmberTester',version:'1.21.11',auth:'offline'});
bot.on('spawn', () => {
  setTimeout(() => bot.chat('/gamemode creative'), 500);
  setTimeout(() => bot.chat('/tp 210 110 210'), 1500);
  setTimeout(() => bot.chat('/fill 195 100 195 225 100 225 minecraft:stone'), 4000);
  setTimeout(() => bot.chat('/fill 195 101 195 225 112 225 minecraft:air'), 8000);
});
bot.on('message', m => console.log('CHAT: ' + JSON.stringify(m.toString())));
setTimeout(() => { bot.quit(); process.exit(0); }, 13000);
