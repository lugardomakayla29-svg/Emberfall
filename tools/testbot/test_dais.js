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
  cmd('/gamemode creative'); await sleep(800);
  cmd('/expedition'); await sleep(2500);
  cmd('/emberfall boss 0'); await sleep(2000);

  // check dais/moat blocks
  const b1 = bot.blockAt(new Vec3(11,64,11)); // dais top should be raised base at y64 (blackstone)
  const b2 = bot.blockAt(new Vec3(11,65,11)); // dais walkable surface (polished blackstone)
  const b3 = bot.blockAt(new Vec3(11,64,15)); // moat ring (water) - chebyshev=4 from (11,11)
  const b4 = bot.blockAt(new Vec3(11,64,1)); // outside moat, normal floor untouched
  log(`dais base (11,64,11)=${b1?b1.name:'?'}  dais top (11,65,11)=${b2?b2.name:'?'}`);
  log(`moat ring (11,64,15)=${b3?b3.name:'?'}  untouched floor (11,64,1)=${b4?b4.name:'?'}`);

  cmd('/tp EmberTester 11.5 66 15.5'); await sleep(500); // stand in the moat area at dais height, will fall into water
  await sleep(3000); // let ambience/damage tick run
  log(`my health after standing near moat: checking via /data get entity`);
  cmd('/data get entity @s Health'); await sleep(700);

  await sleep(500);
  cmd('/expedition leave'); await sleep(1500);
  bot.quit(); await sleep(1000); process.exit(0);
});
