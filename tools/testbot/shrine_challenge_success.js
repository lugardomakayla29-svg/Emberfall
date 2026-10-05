const mineflayer = require('mineflayer');
const { Vec3 } = require('vec3');
const bot = mineflayer.createBot({ host: '127.0.0.1', port: 25565, username: 'EmberTester', version: '1.21.11', auth: 'offline' });
let start = Date.now();
const log = (m) => console.log(`[${((Date.now()-start)/1000).toFixed(1)}s] ${m}`);
bot.on('message', (m) => log('CHAT: ' + m.toString()));
bot.on('error', (e) => log('ERROR: ' + e));

const shrinePositions = [[15,15],[10,3],[10,17]];
function scanShrines() {
  const found = [];
  for (const [x,z] of shrinePositions) {
    const base = bot.blockAt(new Vec3(x,64,z));
    if (!base) continue;
    let type = null;
    if (base.name === 'gold_block') type = 'GREED';
    else if (base.name === 'soul_sand') type = 'CURSE';
    else if (base.name === 'nether_bricks') type = 'CHALLENGE';
    if (type) found.push({x, z, type});
  }
  return found;
}
async function sleep(ms) { return new Promise(r => setTimeout(r, ms)); }

bot.once('spawn', async () => {
  log('spawned overworld');
  let challenge = null;
  for (let attempt = 0; attempt < 6 && !challenge; attempt++) {
    if (attempt > 0) { bot.chat('/expedition leave'); await sleep(1500); }
    bot.chat('/expedition');
    await sleep(2500);
    const shrines = scanShrines();
    log(`attempt ${attempt}: shrines=` + JSON.stringify(shrines));
    challenge = shrines.find(s => s.type === 'CHALLENGE');
  }
  if (!challenge) { log('no challenge rolled'); process.exit(0); return; }

  // snapshot entity ids present BEFORE trigger, near the shrine, so we can diff after
  const beforeIds = new Set(Object.values(bot.entities)
    .filter(e => e.position && e.position.distanceTo(new Vec3(challenge.x+0.5,65,challenge.z+0.5)) < 6)
    .map(e => e.id));
  log(`--- triggering CHALLENGE shrine at (${challenge.x},${challenge.z}) ---`);
  bot.chat(`/tp EmberTester ${challenge.x + 0.5} 65 ${challenge.z + 0.5}`);
  await sleep(1500);

  const shrineCenter = new Vec3(challenge.x+0.5,65,challenge.z+0.5);
  const newMobs = () => Object.values(bot.entities)
    .filter(e => e.position && !beforeIds.has(e.id) && e.type !== 'player' && e.position.distanceTo(shrineCenter) < 8);
  log('new mobs spawned by challenge: ' + newMobs().length + ' ids=' + newMobs().map(m=>m.id).join(','));

  // use /kill targeting since these are OP-only test conditions and combat AI dodging makes
  // melee unreliable for a quick automated check - we just need to prove the SUCCESS branch fires
  for (const m of newMobs()) {
    bot.chat(`/kill @e[type=!minecraft:player,x=${m.position.x},y=${m.position.y},z=${m.position.z},distance=..1]`);
    await sleep(300);
  }
  await sleep(2000);
  log('mobs remaining: ' + newMobs().length);
  await sleep(3000);
  bot.chat('/expedition leave');
  await sleep(1500);
  bot.quit();
  await sleep(1500);
  process.exit(0);
});
