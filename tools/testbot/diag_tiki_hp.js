const mineflayer = require('mineflayer');
const bot = mineflayer.createBot({ host: '127.0.0.1', port: 25565, username: 'EmberTester', version: '1.21.11', auth: 'offline' });
let t0 = Date.now();
const log = (m) => console.log(`[${((Date.now()-t0)/1000).toFixed(1)}s] ${m}`);
function cmd(c) { bot.chat(c); }
bot.on('message', (msg) => { log('CHAT: ' + msg.toString()); });
bot.on('error', (e) => log('ERROR: ' + e));
bot.on('end', (r) => log('DISCONNECTED: ' + JSON.stringify(r)));
function sleep(ms) { return new Promise(r => setTimeout(r, ms)); }

bot.once('spawn', async () => {
  cmd('/gamemode creative'); await sleep(300);
  cmd('/execute in emberfall:expedition run tp EmberTester 0 100 0'); await sleep(400);
  cmd('/kill @e[type=emberfall:tiki_magma]'); await sleep(300);
  cmd('/kill @e[type=emberfall:tiki_segment]'); await sleep(300);
  cmd('/tp @s 0.5 100 0.5'); await sleep(300);
  cmd('/emberfall spawnveteran tiki_magma'); await sleep(80);
  bot.chat('/data get entity @e[type=emberfall:tiki_magma,limit=1] Health');
  await sleep(200);
  bot.chat('/attribute @e[type=emberfall:tiki_magma,limit=1] minecraft:max_health base get');
  await sleep(300);
  bot.chat('/attribute @e[type=emberfall:tiki_magma,limit=1] minecraft:max_health get');
  await sleep(300);
  bot.chat('/data get entity @e[type=emberfall:tiki_magma,limit=1] Size');
  await sleep(300);
  // poll health over next 2s
  for (let i=0;i<8;i++){
    bot.chat('/data get entity @e[type=emberfall:tiki_magma,limit=1] Health');
    await sleep(250);
  }
});

setTimeout(async () => {
  bot.quit();
  await sleep(300);
  process.exit(0);
}, 5000);
