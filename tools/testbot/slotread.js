const mineflayer = require('mineflayer');
const bot = mineflayer.createBot({ host: '127.0.0.1', port: 25565, username: 'EmberTester', version: '1.21.11', auth: 'offline' });
const sleep = ms => new Promise(r => setTimeout(r, ms));
const lines = []; bot.on('message', m => lines.push(m.toString()));
bot.once('spawn', async () => {
  await sleep(6000);
  const n = lines.length; bot.chat('/emberfall debugloadout EmberTester'); await sleep(1200);
  console.log(lines.slice(n).filter(l => !/Teleported/.test(l)).join(' | ').slice(0, 200));
  bot.quit(); setTimeout(() => process.exit(0), 500);
});
