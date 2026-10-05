const mineflayer = require('mineflayer');
const bot = mineflayer.createBot({ host: '127.0.0.1', port: 25565, username: 'EmberTester', version: '1.21.11', auth: 'offline' });
const sleep = ms => new Promise(r => setTimeout(r, ms));
const lines = []; bot.on('message', m => lines.push(m.toString()));
bot.once('spawn', async () => {
  await sleep(5000);
  console.log('spawned at', bot.entity.position.toString());
  const n = lines.length; bot.chat('/character select juggernaut'); await sleep(500); bot.chat('/expedition'); await sleep(3000);
  console.log('replies:', JSON.stringify(lines.slice(n)));
  bot.chat('/expedition leave'); await sleep(800);
  bot.quit(); setTimeout(() => process.exit(0), 300);
});
bot.on('error', e => console.log('ERR', e.message));
