const mineflayer = require('mineflayer');

const bot = mineflayer.createBot({
  host: '127.0.0.1',
  port: 25565,
  username: 'EmberTester',
  version: '1.21.11',
  auth: 'offline'
});

let startTime = Date.now();
function log(msg) {
  console.log(`[${((Date.now() - startTime) / 1000).toFixed(1)}s] ${msg}`);
}
function cmd(c) {
  log('CMD: ' + c);
  bot.chat(c);
}
bot.on('message', (msg) => log('CHAT: ' + msg.toString()));
bot.on('error', (err) => log('ERROR: ' + (err.message || err)));
bot.on('kicked', (reason) => log('KICKED: ' + JSON.stringify(reason)));
bot.on('end', (reason) => log('disconnected: ' + JSON.stringify(reason)));

function wait(ms) { return new Promise((resolve) => setTimeout(resolve, ms)); }

async function run() {
  await new Promise((resolve) => bot.once('spawn', resolve));
  log('spawned at ' + JSON.stringify(bot.entity.position));

  cmd('/gamemode survival'); await wait(500);
  cmd('/effect give EmberTester minecraft:resistance 999999 4 true'); await wait(500);
  cmd('/effect give EmberTester minecraft:saturation 999999 0 true'); await wait(500);

  cmd('/emberfall paste wavetest'); await wait(2000);
  cmd('/emberfall join 0 EmberTester'); await wait(2000);

  // Grant every new/changed synergy tag's full 3-set to the test player, deterministically,
  // bypassing the random level-up choice screen (design doc test hook, see grantTome).
  const tomes = [
    'frostbite_fang', 'permafrost_shard', 'glacial_ward',
    'venomous_fang', 'toxic_vial', 'serpents_mark',
    'static_discharge', 'charged_core', 'storm_sigil',
    'volatile_rounds', 'unstable_core', 'blast_sigil',
    'bloodletting', 'crimson_thirst', 'sanguine_locket',
    'loyal_hound', 'spectral_hound', 'reliquary_shard'
  ];
  for (const t of tomes) {
    cmd(`/emberfall granttome EmberTester ${t}`);
    await wait(400);
  }

  await wait(1000);
  log('All tomes granted. Spawning target dummies and letting auto-attack run...');

  // Spawn several elite dummies near the player - AutoAttackSystem auto-targets any
  // emberfall-namespaced Mob in range, so these get hit automatically without manual clicking.
  for (let i = 0; i < 6; i++) {
    cmd('/emberfall spawnelite cinderbrand_reaver');
    await wait(600);
  }

  for (let i = 0; i < 6; i++) {
    await wait(15000);
    log('CHECK ' + ((i + 1) * 15) + 's - respawning dummies if thinned out');
    cmd('/emberfall spawnelite cinderbrand_reaver');
  }

  log('TEST COMPLETE');
  bot.quit();
  process.exit(0);
}

run().catch((e) => { log('FATAL: ' + e); process.exit(1); });
process.on('SIGTERM', () => { bot.quit(); process.exit(0); });
