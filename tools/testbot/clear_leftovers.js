const mineflayer = require('mineflayer');
const bot = mineflayer.createBot({ host: '127.0.0.1', port: 25565, username: 'EmberTester', version: '1.21.11', auth: 'offline' });
bot.on('spawn', () => {
  setTimeout(() => bot.chat('/gamemode creative'), 500);
  setTimeout(() => bot.chat('/kill @e[type=emberfall:devourer_brain]'), 1000);
  setTimeout(() => bot.chat('/kill @e[type=emberfall:devourer_segment]'), 1500);
  setTimeout(() => bot.chat('/kill @e[type=emberfall:devourer_spawn]'), 2000);
  setTimeout(() => bot.chat('/kill @e[type=minecraft:item_display]'), 2500);
  setTimeout(() => { bot.quit(); process.exit(0); }, 4000);
});
bot.on('message', (m) => console.log('CHAT: ' + m.toString()));
