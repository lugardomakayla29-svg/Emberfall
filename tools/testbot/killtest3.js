const mineflayer = require('mineflayer');
const bot = mineflayer.createBot({host:'127.0.0.1',port:25565,username:'EmberKiller3',version:'1.21.11',auth:'offline'});
bot.on('spawn', () => {
  setTimeout(() => bot.chat('/tp @s ~ ~1 ~'), 500);
  setTimeout(() => bot.chat('/effect give @e minecraft:glowing 5'), 1200);
  setTimeout(() => { bot.quit(); process.exit(0); }, 2200);
});
bot.on('message', m => console.log('CHAT: ' + JSON.stringify(m.toString())));
