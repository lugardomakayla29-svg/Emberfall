const mineflayer = require('mineflayer');
const bot = mineflayer.createBot({ host: '127.0.0.1', port: 25565, username: 'EmberTester', version: '1.21.11', auth: 'offline' });
let start = Date.now();
const log = (m) => console.log(`[${((Date.now()-start)/1000).toFixed(1)}s] ${m}`);
bot.on('message', (m) => log('CHAT: ' + m.toString()));
bot.on('error', (e) => log('ERROR: ' + e));
bot.on('kicked', (r) => log('KICKED: ' + JSON.stringify(r)));
let started = false;
bot.on('spawn', () => {
  if (started) { log('(ignoring duplicate spawn)'); return; }
  started = true;
  log('spawn at ' + JSON.stringify(bot.entity.position));
  const t = (ms, f) => setTimeout(f, ms);
  t(1000, () => { log('CMD: /expedition'); bot.chat('/expedition'); });
  t(4000, () => log('pos after start: ' + JSON.stringify(bot.entity.position)));
  t(6000, () => { log('CMD: /expedition leave'); bot.chat('/expedition leave'); });
  t(9000, () => log('pos after leave: ' + JSON.stringify(bot.entity.position)));
  t(10000, () => { log('DONE clean single-cycle test'); bot.quit(); process.exit(0); });
});
