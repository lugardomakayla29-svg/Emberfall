const mineflayer = require('mineflayer');
const bot = mineflayer.createBot({ host: '127.0.0.1', port: 25565, username: 'EmberTester', version: '1.21.11', auth: 'offline' });
let t0 = Date.now();
const log = (m) => console.log(`[${((Date.now()-t0)/1000).toFixed(1)}s] ${m}`);
function cmd(c) { log('CMD: ' + c); bot.chat(c); }
bot.on('message', (msg) => log('CHAT: ' + msg.toString()));
bot.on('error', (e) => log('ERROR: ' + e));

bot.once('spawn', async () => {
  const sleep = (ms) => new Promise(r => setTimeout(r, ms));
  cmd('/gamemode creative'); await sleep(800);
  cmd('/expedition'); await sleep(2500);
  cmd('/setblock 10 64 10 emberfall:marker'); await sleep(500);
  cmd('/data merge block 10 64 10 {MarkerType:"boss_spawn"}'); await sleep(500);
  cmd('/emberfall capture starter_arena_v2 0 64 0 20 70 20'); await sleep(2500);
  cmd('/expedition leave'); await sleep(1500);
  bot.quit(); await sleep(1000); process.exit(0);
});
