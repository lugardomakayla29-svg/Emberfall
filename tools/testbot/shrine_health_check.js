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
  await sleep(800);
  bot.chat('/attribute EmberTester minecraft:max_health get');
  await sleep(1000);
  bot.chat('/expedition');
  await sleep(2500);
  const shrines = scanShrines();
  const curse = shrines.find(s => s.type === 'CURSE');
  log('shrines: ' + JSON.stringify(shrines) + ' curse=' + JSON.stringify(curse));
  if (curse) {
    bot.chat(`/tp EmberTester ${curse.x + 0.5} 65 ${curse.z + 0.5}`);
    await sleep(1500);
    log('--- max_health mid-run, after curse trigger: ---');
    bot.chat('/attribute EmberTester minecraft:max_health get');
    await sleep(1000);
    bot.chat('/attribute EmberTester minecraft:max_health base get');
    await sleep(1000);
  } else {
    log('no CURSE shrine this roll, skipping health assertion');
  }
  bot.chat('/expedition leave');
  await sleep(2000);
  log('--- max_health after leaving run (should be restored to 20): ---');
  bot.chat('/attribute EmberTester minecraft:max_health get');
  await sleep(1500);
  log('done, staying connected 3s then quitting gracefully');
  await sleep(3000);
  bot.quit();
  await sleep(2000);
  process.exit(0);
});
