const mineflayer = require('mineflayer');
const { Vec3 } = require('vec3');
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
  cmd('/emberfall boss 0'); await sleep(2000);

  let d1 = bot.blockAt(new Vec3(11,64,11));
  let d2 = bot.blockAt(new Vec3(11,64,15));
  log(`BEFORE kill: dais(11,64,11)=${d1?d1.name:'?'}  moat(11,64,15)=${d2?d2.name:'?'}`);

  cmd('/kill @e[type=emberfall:hydra_brain]'); await sleep(2500); // let tickAll notice death and restore

  let d3 = bot.blockAt(new Vec3(11,64,11));
  let d4 = bot.blockAt(new Vec3(11,64,15));
  let d5 = bot.blockAt(new Vec3(11,65,11)); // was polished_blackstone, should be air now
  log(`AFTER kill: dais(11,64,11)=${d3?d3.name:'?'}  moat(11,64,15)=${d4?d4.name:'?'}  dais-top-air-check(11,65,11)=${d5?d5.name:'?'}`);

  await sleep(300);
  cmd('/expedition leave'); await sleep(1500);
  bot.quit(); await sleep(1000); process.exit(0);
});
