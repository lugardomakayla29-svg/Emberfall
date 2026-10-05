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
  if (!challenge) {
    log('Never rolled a CHALLENGE shrine after 6 attempts, giving up on this check');
    process.exit(0);
    return;
  }
  log(`--- triggering CHALLENGE shrine at (${challenge.x},${challenge.z}) ---`);
  bot.chat(`/tp EmberTester ${challenge.x + 0.5} 65 ${challenge.z + 0.5}`);
  await sleep(2000);
  const mobs = () => Object.values(bot.entities).filter(e => e.position && bot.entity.position.distanceTo(e.position) < 10 && e.type === 'mob');
  log('mobs right after trigger: ' + mobs().length + ' -> ' + mobs().map(m => m.name).join(','));
  await sleep(3000);
  log('mobs after 3s: ' + mobs().length);
  log('killing mobs by attacking...');
  for (let i = 0; i < 30 && mobs().length > 0; i++) {
    const target = mobs()[0];
    bot.lookAt(target.position.offset(0, 1, 0));
    bot.attack(target);
    await sleep(500);
  }
  await sleep(1500);
  log('mobs remaining after kill attempts: ' + mobs().length);
  log('final health: ' + bot.health);
  await sleep(2000);
  bot.chat('/expedition leave');
  await sleep(2000);
  bot.quit();
  await sleep(2000);
  process.exit(0);
});
