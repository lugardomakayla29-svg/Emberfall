const mineflayer = require('mineflayer');
const bot = mineflayer.createBot({ host: '127.0.0.1', port: 25565, username: 'EmberTester', version: '1.21.11', auth: 'offline' });
let t0 = Date.now();
const log = (m) => console.log(`[${((Date.now()-t0)/1000).toFixed(1)}s] ${m}`);
bot.on('message', (msg) => log('CHAT: ' + msg.toString()));
bot._client.on('packet', (data, meta) => {
  if (meta.name === 'system_chat' || meta.name === 'chat') {
    log('PACKET(' + meta.name + '): ' + JSON.stringify(data).slice(0, 500));
  }
});
bot.on('error', (e) => log('ERROR: ' + e));
bot.on('kicked', (r) => log('KICKED: ' + JSON.stringify(r)));
let started = false;
bot.on('spawn', () => {
  if (started) return;
  started = true;
  setTimeout(() => { log('CMD: /data get entity @s'); bot.chat('/data get entity @s'); }, 1000);
  setTimeout(() => { log('CMD: /data get entity @p'); bot.chat('/data get entity @p'); }, 3000);
  setTimeout(() => { bot.quit(); process.exit(0); }, 6000);
});
