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
  cmd('/gamemode spectator'); await sleep(800);
  cmd('/tp EmberTester 10.5 68 10.5'); await sleep(1000);

  let xline = [];
  for (let x = -2; x <= 22; x++) {
    const b = bot.blockAt(new Vec3(x,65,10));
    xline.push(`${x}:${b?b.name:'?'}`);
  }
  log('x-line @z=10,y=65: ' + xline.join(' '));
  let zline = [];
  for (let z = -2; z <= 22; z++) {
    const b = bot.blockAt(new Vec3(10,65,z));
    zline.push(`${z}:${b?b.name:'?'}`);
  }
  log('z-line @x=10,y=65: ' + zline.join(' '));
  // ceiling
  let yline = [];
  for (let y = 64; y <= 80; y++) {
    const b = bot.blockAt(new Vec3(10,y,10));
    yline.push(`${y}:${b?b.name:'?'}`);
  }
  log('y-line @x=10,z=10: ' + yline.join(' '));
  await sleep(500);
  cmd('/expedition leave'); await sleep(1500);
  bot.quit(); await sleep(1000); process.exit(0);
});
