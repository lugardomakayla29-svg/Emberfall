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
function allNearby(radius) {
  return Object.values(bot.entities).filter(e => e.position && bot.entity.position.distanceTo(e.position) < radius);
}
async function sleep(ms) { return new Promise(r => setTimeout(r, ms)); }

bot.once('spawn', async () => {
  log('spawned overworld');
  let challenge = null;
  for (let attempt = 0; attempt < 6 && !challenge; attempt++) {
    if (attempt > 0) {
      bot.chat('/expedition leave');
      await sleep(1500);
    }
    bot.chat('/expedition');
    await sleep(2500);
    const shrines = scanShrines();
    log(`attempt ${attempt}: shrines=` + JSON.stringify(shrines));
    challenge = shrines.find(s => s.type === 'CHALLENGE');
  }
  if (!challenge) { log('no challenge rolled'); process.exit(0); return; }
  log(`--- triggering CHALLENGE shrine at (${challenge.x},${challenge.z}) ---`);
  bot.chat(`/tp EmberTester ${challenge.x + 0.5} 65 ${challenge.z + 0.5}`);
  await sleep(1500);
  log('ALL entities within 20 blocks right after trigger:');
  for (const e of allNearby(20)) {
    log(`  id=${e.id} type=${e.type} name=${e.name} displayName=${e.displayName} kind=${e.kind} pos=${JSON.stringify(e.position)}`);
  }
  await sleep(20000); // wait almost full 25s duration to see if a timeout/reward message appears
  log('ALL entities within 20 blocks after wait:');
  for (const e of allNearby(20)) {
    log(`  id=${e.id} type=${e.type} name=${e.name} pos=${JSON.stringify(e.position)}`);
  }
  await sleep(6000);
  bot.chat('/expedition leave');
  await sleep(1500);
  bot.quit();
  await sleep(1500);
  process.exit(0);
});
