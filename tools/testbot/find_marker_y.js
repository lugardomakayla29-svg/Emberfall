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
  log(`my own position after spawn: ${JSON.stringify(bot.entity.position)}`);
  cmd('/gamemode spectator'); await sleep(800);
  // scan y=63..67 across the known interior for any emberfall:marker block (should still be there if unconsumed, or check for a shrine block if consumed)
  for (let y = 63; y <= 67; y++) {
    let hits = [];
    for (let x = 1; x <= 19; x += 1) {
      for (let z = 1; z <= 19; z += 1) {
        const b = bot.blockAt(new Vec3(x,y,z));
        if (b && b.name !== 'air' && b.name !== 'stone') hits.push(`(${x},${y},${z})=${b.name}`);
      }
    }
    if (hits.length) log(`y=${y}: ${hits.join(' ')}`);
  }
  await sleep(500);
  cmd('/expedition leave'); await sleep(1500);
  bot.quit(); await sleep(1000); process.exit(0);
});
