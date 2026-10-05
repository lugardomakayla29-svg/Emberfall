const mineflayer = require('mineflayer');
const bot = mineflayer.createBot({ host: '127.0.0.1', port: 25565, username: 'EmberTester', version: '1.21.11', auth: 'offline' });
let start = Date.now();
const log = (m) => console.log(`[${((Date.now()-start)/1000).toFixed(1)}s] ${m}`);
bot.on('message', (m) => log('CHAT: ' + m.toString()));
bot.on('error', (e) => log('ERROR: ' + e));
let started = false;
bot.on('spawn', () => {
  if (started) return;
  started = true;
  log('spawn at ' + JSON.stringify(bot.entity.position));
  setTimeout(() => { log('still connected, pos=' + JSON.stringify(bot.entity.position)); }, 60000);
});
