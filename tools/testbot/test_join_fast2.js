const mineflayer = require('mineflayer');
const bot = mineflayer.createBot({ host: '127.0.0.1', port: 25565, username: 'EmberTester', version: '1.21.11', auth: 'offline' });
let t0 = Date.now();
function log(m) { console.log(`[${((Date.now()-t0)/1000).toFixed(1)}s] ${m}`); }
function cmd(c) { log('CMD: ' + c); bot.chat(c); }
function wait(ms) { return new Promise(r => setTimeout(r, ms)); }
bot.on('message', (m) => log('CHAT: ' + m.toString()));
bot.on('error', (e) => log('ERROR: ' + e));

async function run() {
  await new Promise((resolve) => bot.once('spawn', resolve));
  log('spawned dim=' + bot.game.dimension);
  cmd('/emberfall join 1 EmberTester'); await wait(2000);
  log('position: ' + JSON.stringify(bot.entity.position) + ' dim=' + bot.game.dimension);
  bot.quit(); process.exit(0);
}
run().catch(e => { log('FATAL: ' + e); process.exit(1); });
