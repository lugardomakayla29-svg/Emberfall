const mineflayer = require('mineflayer');
const bot = mineflayer.createBot({ host: '127.0.0.1', port: 25565, username: 'EmberTester', version: '1.21.11', auth: 'offline' });
let t0 = Date.now();
const log = (m) => console.log(`[${((Date.now()-t0)/1000).toFixed(1)}s] ${m}`);
let lastMsg = null;
function cmd(c) { lastMsg = null; bot.chat(c); }
bot.on('message', (msg) => { lastMsg = msg.toString(); log('CHAT: ' + lastMsg); });
bot.on('error', (e) => log('ERROR: ' + e));
bot.on('end', (r) => log('DISCONNECTED: ' + JSON.stringify(r)));

function sleep(ms) { return new Promise(r => setTimeout(r, ms)); }
async function query(c, regex) {
  cmd(c); await sleep(120);
  const m = regex.exec(lastMsg || '');
  return m ? m[1] : null;
}
const readHealth = (tag) => query(`/data get entity @e[tag=${tag},limit=1,sort=nearest] Health`, /: ([\d.]+)f/);

bot.once('spawn', async () => {
  cmd('/gamemode creative'); await sleep(300);
  cmd('/execute in emberfall:expedition run tp EmberTester 0 100 0'); await sleep(400);
  cmd('/execute in emberfall:expedition run fill 8 99 8 -8 99 -8 minecraft:stone'); await sleep(600);
  cmd('/emberfall selectweapon EmberTester spectral_sickles'); await sleep(300);
  cmd('/emberfall granttome EmberTester widening_gyre'); await sleep(250);
  cmd('/emberfall granttome EmberTester widening_gyre'); await sleep(250);
  cmd('/emberfall granttome EmberTester widening_gyre'); await sleep(250);
  cmd('/kill @e[type=emberfall:horde_zombie]'); await sleep(300);
  cmd('/tp @s 0.5 100 0.5'); await sleep(300);

  log('=== filler (HP 1000, durable) planted on the orbit ring (radius 2.2, angle 0) so it keeps getting hit and holding the streak at max stacks. NOTE: inline Attributes NBT at /summon does NOT stick for this custom entity (confirmed via isolated repro - stays at vanilla Zombie default 20) - must use a separate /attribute ... base set command after summon instead ===');
  cmd('/summon emberfall:horde_zombie 2.7 100 0.5 {Silent:1b,PersistenceRequired:1b,NoAI:1b,Tags:["filler"]}');
  await sleep(300);
  cmd('/attribute @e[tag=filler,limit=1,sort=nearest] minecraft:max_health base set 1000'); await sleep(300);
  cmd('/data merge entity @e[tag=filler,limit=1,sort=nearest] {Health:1000.0f}'); await sleep(300);

  log('=== Waiting 8s for the blades to land several hits on filler and cap the streak at max stacks (4, +32%) ===');
  await sleep(8000);
  log(`filler health check (should still be very alive, confirms it survived the ramp-up): ${await readHealth('filler')}`);

  log('=== Spawning killtarget (HP 1.0, ring angle 200) and witness (HP 2.7, ring angle 215, 15 degrees further along the blades direction of travel) together. A blade reaches killtarget first, kills it (earning tier 3 overcap if stacks are maxed), then reaches witness about 0.09s later while the earned overcap is still fresh - deterministic via geometry, not dependent on script polling speed ===');
  cmd('/summon emberfall:horde_zombie -1.57 100 -0.25 {Silent:1b,PersistenceRequired:1b,NoAI:1b,Tags:["killtarget"],Attributes:[{id:"minecraft:max_health",base:20.0}]}');
  await sleep(300);
  cmd('/data merge entity @e[tag=killtarget,limit=1,sort=nearest] {Health:1.0f}'); await sleep(300);
  cmd('/summon emberfall:horde_zombie -1.30 100 -0.76 {Silent:1b,PersistenceRequired:1b,NoAI:1b,Tags:["witness"],Attributes:[{id:"minecraft:max_health",base:20.0}]}');
  await sleep(300);
  cmd('/data merge entity @e[tag=witness,limit=1,sort=nearest] {Health:2.7f}'); await sleep(300);

  log('=== Polling both every ~150ms for up to 2.5s (one full revolution), stopping as soon as witness shows its FIRST hit - a second blade pass finishing off an already-weakened witness would confound the result regardless of what the first hit rolled ===');
  let killtargetDead = false;
  let witnessResult = null;
  const pollDeadline = Date.now() + 2500;
  while (Date.now() < pollDeadline && witnessResult === null) {
    if (!killtargetDead) {
      const kh = await readHealth('killtarget');
      if (kh === null) killtargetDead = true;
    }
    const wh = await readHealth('witness');
    if (wh === null) { witnessResult = 'dead'; break; }
    if (Number(wh) < 2.7) { witnessResult = wh; break; }
    await sleep(100);
  }
  cmd('/tp @s 40 100 40'); await sleep(300);
  log(`killtarget died: ${killtargetDead} (confirms the kill-at-max-stacks moment happened)`);
  log(`witness first-hit result: ${witnessResult === null ? 'no hit detected in window' : witnessResult} -> "dead" or HP<0 means tier 3's 2.80 dmg overcap hit landed; surviving at ~0.06f means only a normal 4-stack (2.64) hit landed`);

  cmd('/kill @e[type=emberfall:horde_zombie]');
  await sleep(300);
  bot.quit(); await sleep(500); process.exit(0);
});
