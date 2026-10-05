const mineflayer = require('mineflayer');
const bot = mineflayer.createBot({ host: '127.0.0.1', port: 25565, username: 'EmberTester', version: '1.21.11', auth: 'offline' });
let start = Date.now();
const log = (m) => console.log(`[${((Date.now()-start)/1000).toFixed(1)}s] ${m}`);
bot.on('message', (m) => log('CHAT: ' + m.toString()));
let started = false;
bot.on('spawn', () => {
  if (started) return;
  started = true;
  log('spawn');
  const t = (ms, f) => setTimeout(f, ms);
  t(1000, () => { bot.chat('/character select juggernaut'); });
  t(2500, () => { bot.chat('/expedition'); });
  t(30000, () => { bot.chat('/expedition leave'); bot.quit(); process.exit(0); });
});
