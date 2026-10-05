const mineflayer = require('mineflayer');
const bot = mineflayer.createBot({host:'127.0.0.1',port:25565,username:'EmberKiller',version:'1.21.11',auth:'offline'});
bot.on('spawn', () => {
  setTimeout(() => bot.chat('/kill @e[type=item]'), 500);
  setTimeout(() => { bot.quit(); process.exit(0); }, 2000);
});
bot.on('message', m => console.log('CHAT: ' + JSON.stringify(m.toString())));
