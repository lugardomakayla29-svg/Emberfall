const mineflayer = require('mineflayer');
const { Vec3 } = require('vec3');
const bot = mineflayer.createBot({ host: '127.0.0.1', port: 25565, username: 'ShrineChecker', version: '1.21.11', auth: 'offline' });
let start = Date.now();
const log = (m) => console.log(`[${((Date.now()-start)/1000).toFixed(1)}s] ${m}`);
bot.on('message', (m) => log('CHAT: ' + m.toString()));
bot.on('error', (e) => log('ERROR: ' + e));
bot.once('spawn', () => {
  setTimeout(() => {
    log('spawned overworld, teleporting via server admin next');
  }, 500);
});
process.on('SIGTERM', () => process.exit(0));
setInterval(() => {}, 1000);
