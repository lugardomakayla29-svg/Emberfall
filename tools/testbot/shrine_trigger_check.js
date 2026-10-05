const mineflayer = require('mineflayer');
const bot = mineflayer.createBot({ host: '127.0.0.1', port: 25565, username: 'EmberTester', version: '1.21.11', auth: 'offline' });
let start = Date.now();
const log = (m) => console.log(`[${((Date.now()-start)/1000).toFixed(1)}s] ${m}`);
bot.on('message', (m) => log('CHAT: ' + m.toString()));
bot.on('error', (e) => log('ERROR: ' + e));

bot.once('spawn', () => {
  log('spawned overworld, joining existing slot 0');
  setTimeout(() => bot.chat('/emberfall join 0 EmberTester'), 800);
  setTimeout(() => {
    log('teleporting to greed shrine at 15.5,65,15.5');
    bot.chat('/tp EmberTester 15.5 65 15.5');
  }, 3000);
  setTimeout(() => {
    log('checking health/attributes and inventory-free (should still have no GUI stuck)');
    bot.chat('/data get entity EmberTester Attributes');
  }, 5500);
  setTimeout(() => process.exit(0), 8000);
});
