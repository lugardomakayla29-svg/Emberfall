const mineflayer = require('mineflayer');
const { Vec3 } = require('vec3');
const bot = mineflayer.createBot({ host: '127.0.0.1', port: 25565, username: 'EmberTester', version: '1.21.11', auth: 'offline' });
let start = Date.now();
const log = (m) => console.log(`[${((Date.now()-start)/1000).toFixed(1)}s] ${m}`);
bot.on('message', (m) => log('CHAT: ' + m.toString()));
bot.on('error', (e) => log('ERROR: ' + e));

const shrinePositions = [[15,15],[10,3],[10,17]]; // [x,z], y=64/65

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
  await sleep(800);
  bot.chat('/emberfall paste starter_arena');
  await sleep(2000);
  bot.chat('/emberfall join 0 EmberTester');
  await sleep(2500);
  const shrines = scanShrines();
  log('active shrines this roll: ' + JSON.stringify(shrines));

  for (const s of shrines) {
    log(`--- visiting ${s.type} shrine at (${s.x},${s.z}) ---`);
    const beforeHealth = bot.entity.attributes && bot.entity.attributes['minecraft:max_health'] ? bot.entity.attributes['minecraft:max_health'].value : bot.health;
    log('maxHealth before: ' + JSON.stringify(beforeHealth));
    bot.chat(`/tp EmberTester ${s.x + 0.5} 65 ${s.z + 0.5}`);
    await sleep(1500);
    const afterHealth = bot.entity.attributes && bot.entity.attributes['minecraft:max_health'] ? bot.entity.attributes['minecraft:max_health'].value : bot.health;
    log('maxHealth after: ' + JSON.stringify(afterHealth));

    if (s.type === 'CHALLENGE') {
      await sleep(1000);
      const mobs = Object.values(bot.entities).filter(e => e.type === 'mob' || (e.kind && e.kind.includes('Mob')));
      log('mobs nearby after challenge trigger: ' + mobs.length + ' names=' + mobs.map(m=>m.name||m.kind).join(','));
    }
    await sleep(500);
    // step away so we don't re-trigger something else immediately
    bot.chat('/tp EmberTester 10.5 65 10.5');
    await sleep(500);
  }

  log('done, exiting');
  process.exit(0);
});
