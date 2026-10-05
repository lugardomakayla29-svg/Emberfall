const mineflayer = require('mineflayer');
const bot = mineflayer.createBot({host:'127.0.0.1',port:25565,username:'EmberTester',version:'1.21.11',auth:'offline'});
bot.on('spawn', () => {
  setTimeout(() => bot.chat('/gamemode creative'), 300);
  setTimeout(() => bot.chat('/fill 200 100 200 220 100 220 minecraft:stone'), 700);
  setTimeout(() => bot.chat('/fill 200 101 200 220 106 220 minecraft:air'), 1300);
  setTimeout(() => bot.chat('/tp 210 101 210'), 1900);
});
bot.on('message', m => console.log('CHAT: ' + JSON.stringify(m.toString())));
setTimeout(() => { bot.quit(); process.exit(0); }, 3000);
