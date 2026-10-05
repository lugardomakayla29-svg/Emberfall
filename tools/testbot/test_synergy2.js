const mineflayer = require('mineflayer');

const bot = mineflayer.createBot({
  host: '127.0.0.1',
  port: 25565,
  username: 'EmberTester2',
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

  cmd('/emberfall paste wavetest2'); await wait(2000);
  cmd('/emberfall join 1 EmberTester2'); await wait(2000);
  log('position after join: ' + JSON.stringify(bot.entity.position));

  // Creative mode: player deals full outgoing damage (still triggers OnHitEffects) but
  // takes none, so the elite crowd can't kill the tester mid-verification.
  cmd('/gamemode creative EmberTester2'); await wait(500);

  await wait(500);
  log('position before granting: ' + JSON.stringify(bot.entity.position));

  // Max out every new active Tome's stacks so its on-hit chance is ~100%, making each
  // effect deterministic to observe instead of relying on luck within a short window.
  const maxedActives = ['frostbite_fang', 'venomous_fang', 'static_discharge', 'volatile_rounds', 'bloodletting', 'crimson_thirst'];
  for (const t of maxedActives) {
    for (let i = 0; i < 5; i++) {
      cmd(`/emberfall granttome EmberTester2 ${t}`);
      await wait(250);
    }
  }
  // One copy of every tag's carriers (+ the second Summon Tome) - enough for every tag's
  // 3-item synergy threshold, since tagCount only needs 3 distinct ids, not stack depth.
  const carriers = [
    'permafrost_shard', 'glacial_ward',
    'toxic_vial', 'serpents_mark',
    'charged_core', 'storm_sigil',
    'unstable_core', 'blast_sigil',
    'sanguine_locket',
    'loyal_hound', 'spectral_hound', 'reliquary_shard'
  ];
  for (const t of carriers) {
    cmd(`/emberfall granttome EmberTester2 ${t}`);
    await wait(250);
  }

  await wait(1000);
  log('position after granting (should be unchanged if stable ground): ' + JSON.stringify(bot.entity.position));

  log('Spawning ONE target dummy at a time near the tester...');
  for (let round = 0; round < 4; round++) {
    cmd('/emberfall spawnelite cinderbrand_reaver');
    await wait(8000);
    log('position at round ' + round + ': ' + JSON.stringify(bot.entity.position));
  }

  log('TEST COMPLETE');
  bot.quit();
  process.exit(0);
}

run().catch((e) => { log('FATAL: ' + e); process.exit(1); });
process.on('SIGTERM', () => { bot.quit(); process.exit(0); });
