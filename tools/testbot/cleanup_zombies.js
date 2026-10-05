const mineflayer = require('mineflayer');
const bot = mineflayer.createBot({ host: '127.0.0.1', port: 25565, username: 'EmberTester', version: '1.21.11', auth: 'offline' });
function sleep(ms) { return new Promise(r => setTimeout(r, ms)); }
bot.once('spawn', async () => {
  bot.chat('/kill @e[type=emberfall:horde_zombie]');
  await sleep(500);
  bot.quit(); await sleep(300); process.exit(0);
});
