const mineflayer = require('mineflayer');
const bot = mineflayer.createBot({ host: '127.0.0.1', port: 25565, username: 'EmberTester', version: '1.21.11', auth: 'offline' });
let t0 = Date.now();
const log = (m) => console.log(`[${((Date.now()-t0)/1000).toFixed(1)}s] ${m}`);
function cmd(c) { log('CMD: ' + c); bot.chat(c); }
bot.on('message', (msg) => log('CHAT: ' + msg.toString()));
bot.on('error', (e) => log('ERROR: ' + e));

bot.once('spawn', async () => {
  const sleep = (ms) => new Promise(r => setTimeout(r, ms));
  cmd('/gamemode creative'); await sleep(500);
  cmd('/expedition'); await sleep(2500);
  await sleep(45000); // let WaveDirector naturally roll zombie/skeleton/spider fillers
  cmd('/data get entity @e[type=emberfall:horde_skeleton,limit=1] Health'); await sleep(300);
  cmd('/data get entity @e[type=emberfall:horde_spider,limit=1] Health'); await sleep(300);
  cmd('/kill @e[type=emberfall:horde_zombie]');
  cmd('/kill @e[type=emberfall:horde_skeleton]');
  cmd('/kill @e[type=emberfall:horde_spider]');
  await sleep(500);
  cmd('/expedition leave'); await sleep(1500);
  bot.quit(); await sleep(1000); process.exit(0);
});
