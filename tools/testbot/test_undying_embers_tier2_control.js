const mineflayer = require('mineflayer');
const bot = mineflayer.createBot({ host: '127.0.0.1', port: 25565, username: 'EmberTester', version: '1.21.11', auth: 'offline' });
let t0 = Date.now();
const log = (m) => console.log(`[${((Date.now()-t0)/1000).toFixed(1)}s] ${m}`);
let lastMsg = null;
function cmd(c) { lastMsg = null; bot.chat(c); }
bot.on('message', (msg) => { lastMsg = msg.toString(); log('CHAT: ' + msg.toString()); });
bot.on('error', (e) => log('ERROR: ' + e));
bot.on('end', (r) => log('DISCONNECTED: ' + JSON.stringify(r)));

function sleep(ms) { return new Promise(r => setTimeout(r, ms)); }
async function query(c, regex) {
  cmd(c); await sleep(100);
  const m = regex.exec(lastMsg || '');
  return m ? m[1] : null;
}
const readHealth = (tag) => query(`/data get entity @e[tag=${tag},limit=1,sort=nearest] Health`, /: ([\d.]+)f/);

bot.once('spawn', async () => {
  cmd('/gamemode creative'); await sleep(300);
  cmd('/execute in emberfall:expedition run tp EmberTester 0 100 0'); await sleep(400);
  cmd('/emberfall selectweapon EmberTester ashen_beacon'); await sleep(300);
  cmd('/emberfall granttome EmberTester undying_embers'); await sleep(250);
  cmd('/emberfall granttome EmberTester undying_embers'); await sleep(250);
  cmd('/kill @e[type=emberfall:horde_zombie]'); await sleep(300);
  cmd('/tp @s 0.5 100 0.5'); await sleep(300);

  log('=== Ashen Beacon: main pulseDamage=rangedDamage=3.0, PULSE_RADIUS=3.0, pulses every 1s, first pulse immediate on plant. Undying Embers tier 3 ("Feeding Embers"): a pulse that lands a kill plants a fresh half-strength (1.5) kill-ember AT the kill site, itself pulsing immediately then every 1s for 4s. Since a beacon is a pure in-memory record (no entity, unqueryable directly), verifying indirectly: trigger (HP 1.0) dies on the beacon\'s first pulse (planted right at trigger\'s position); witness (HP 6.0), spawned immediately after at the same spot, should show damage from ONLY the main beacon\'s regular +1.0s/+2.0s pulses if tier 3 did nothing, or ALSO an extra ~1.5 dmg kill-ember pulse layered in if it fired ===');

  cmd('/summon emberfall:horde_zombie 3.5 100 0.5 {Silent:1b,PersistenceRequired:1b,NoAI:1b,Tags:["trigger"]}');
  await sleep(300);
  cmd('/data merge entity @e[tag=trigger,limit=1,sort=nearest] {Health:1.0f}'); await sleep(300);

  log('=== Polling trigger for its death (confirms the beacon planted + first-pulse-killed it) ===');
  let triggerDeathTime = null;
  const deadline1 = Date.now() + 4000;
  while (Date.now() < deadline1) {
    const h = await readHealth('trigger');
    if (h === null) { triggerDeathTime = Date.now(); break; }
    await sleep(100);
  }
  if (triggerDeathTime === null) {
    log('trigger never died within 4s - beacon deploy or first-pulse-kill did not happen as expected, aborting');
    cmd('/kill @e[type=emberfall:horde_zombie]'); await sleep(300);
    bot.quit(); await sleep(500); process.exit(0);
  }
  log(`trigger died at t+${((triggerDeathTime - t0)/1000).toFixed(2)}s - spawning witness immediately at the same spot`);

  cmd('/summon emberfall:horde_zombie 3.5 100 0.5 {Silent:1b,PersistenceRequired:1b,NoAI:1b,Tags:["witness"]}');
  await sleep(200);
  cmd('/data merge entity @e[tag=witness,limit=1,sort=nearest] {Health:6.0f}'); await sleep(200);
  const witnessSpawnedAt = Date.now();
  log(`witness spawned+merged at t+${((witnessSpawnedAt - triggerDeathTime)/1000).toFixed(2)}s after trigger's death`);

  log('=== Polling witness health every ~150ms for 2.2s to see the actual pulse timeline (expect a drop near +1.0s from a main-beacon pulse always; tier 3 would show an EXTRA distinct drop around trigger-death+0.05s or +1.05s from the kill-ember) ===');
  const pollUntil = Date.now() + 2200;
  let lastSeen = 6.0;
  while (Date.now() < pollUntil) {
    const h = await readHealth('witness');
    const val = h === null ? 'DEAD' : Number(h);
    if (val === 'DEAD' || Math.abs(val - lastSeen) > 0.001) {
      log(`witness health change at t+${((Date.now() - triggerDeathTime)/1000).toFixed(2)}s since trigger death: ${val}`);
      if (val === 'DEAD') break;
      lastSeen = val;
    }
    await sleep(100);
  }

  cmd('/kill @e[type=emberfall:horde_zombie]');
  await sleep(300);
  bot.quit(); await sleep(500); process.exit(0);
});
