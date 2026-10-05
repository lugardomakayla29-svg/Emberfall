const mineflayer = require('mineflayer');
const bot = mineflayer.createBot({ host: '127.0.0.1', port: 25565, username: 'EmberTester', version: '1.21.11', auth: 'offline' });
let t0 = Date.now();
const log = (m) => console.log(`[${((Date.now()-t0)/1000).toFixed(1)}s] ${m}`);
function cmd(c) { log('CMD: ' + c); bot.chat(c); }
bot.on('message', (msg) => log('CHAT: ' + msg.toString()));
bot.on('error', (e) => log('ERROR: ' + e));

bot.once('spawn', async () => {
  const sleep = (ms) => new Promise(r => setTimeout(r, ms));
  cmd('/gamemode survival'); await sleep(400);
  cmd('/effect give @s minecraft:resistance 600 4'); await sleep(300);
  cmd('/expedition'); await sleep(2500);
  cmd('/emberfall boss 0'); await sleep(1500);
  cmd('/tp EmberTester 13.5 66 11.5'); await sleep(500); // stand ~2 blocks from brain (spawned near 11.5,?,11.5), within SLAM_RADIUS+2

  log('sampling position every 1s for 20s, watching for a sudden ~1.6-2 block hop (slam), ignoring gradual pathing drift');
  let lastPos = bot.entity.position.clone();
  for (let i = 0; i < 20; i++) {
    await sleep(1000);
    const p = bot.entity.position;
    const delta = p.distanceTo(lastPos);
    log(`t+${i+1}s pos=(${p.x.toFixed(2)},${p.y.toFixed(2)},${p.z.toFixed(2)}) delta=${delta.toFixed(2)} onGround=${bot.entity.onGround}`);
    lastPos = p.clone();
  }
  cmd('/data get entity @s Health'); await sleep(600);

  await sleep(300);
  cmd('/expedition leave'); await sleep(1500);
  bot.quit(); await sleep(1000); process.exit(0);
});
