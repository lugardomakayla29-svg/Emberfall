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
  cmd(c); await sleep(150);
  const m = regex.exec(lastMsg || '');
  return m ? m[1] : null;
}

async function spawnDummy(tag, x, y, z, hp) {
  cmd(`/summon emberfall:horde_zombie ${x} ${y} ${z} {Silent:1b,PersistenceRequired:1b,NoAI:1b,Tags:["${tag}"]}`);
  await sleep(250);
  cmd(`/attribute @e[tag=${tag},limit=1,sort=nearest] minecraft:max_health base set ${hp.toFixed(1)}`); await sleep(200);
  cmd(`/data merge entity @e[tag=${tag},limit=1,sort=nearest] {Health:${hp.toFixed(1)}f}`); await sleep(200);
}

bot.once('spawn', async () => {
  cmd('/gamemode creative'); await sleep(300);
  cmd('/execute in emberfall:expedition run tp EmberTester 0 100 0'); await sleep(400);
  cmd('/execute in emberfall:expedition run fill 8 99 8 -8 99 -8 minecraft:stone'); await sleep(600);
  cmd('/emberfall selectweapon EmberTester broadsword'); await sleep(300);
  cmd('/kill @e[type=emberfall:horde_zombie]'); await sleep(300);
  cmd('/tp @s 0.5 100 0.5'); await sleep(300);

  // ===== TEST A: Cinder Wisp (Fire tag) - on-kill ignite spread =====
  log('=== TEST A: Cinder Wisp - kill a 1hp primary, check a 20hp witness 2 blocks away catches fire ===');
  cmd('/emberfall granttome EmberTester cinder_wisp'); await sleep(300);
  await spawnDummy('firePrimary', 2.5, 100, 0.5, 1.0);
  await spawnDummy('fireWitness', 0.9, 100, 2.5, 20.0);
  cmd('/tp @s 0.5 100 0.5 facing 2.5 100 0.5'); await sleep(300);
  await sleep(2500);
  const fireWitnessFire = await query('/data get entity @e[tag=fireWitness,limit=1,sort=nearest] Fire', /: (-?[\d]+)/);
  log(`RESULT A: fireWitness Fire ticks = ${fireWitnessFire} (>0 means Cinder Wisp's spread ignited it)`);
  cmd('/kill @e[tag=firePrimary]'); await sleep(150);
  cmd('/kill @e[tag=fireWitness]'); await sleep(300);

  // ===== TEST B: Permafrost Shard (Frost tag) - on-kill chill spread =====
  log('=== TEST B: Permafrost Shard - kill a 1hp primary, check a 20hp witness catches Slowness ===');
  cmd('/emberfall granttome EmberTester permafrost_shard'); await sleep(300);
  await spawnDummy('frostPrimary', 2.5, 100, 0.5, 1.0);
  await spawnDummy('frostWitness', 0.9, 100, 2.5, 20.0);
  cmd('/tp @s 0.5 100 0.5 facing 2.5 100 0.5'); await sleep(300);
  await sleep(2500);
  const frostWitnessFx = await query('/data get entity @e[tag=frostWitness,limit=1,sort=nearest] active_effects', /(minecraft:slowness)/);
  log(`RESULT B: frostWitness has slowness = ${frostWitnessFx} (should be "minecraft:slowness")`);
  cmd('/kill @e[tag=frostPrimary]'); await sleep(150);
  cmd('/kill @e[tag=frostWitness]'); await sleep(300);

  // ===== TEST C: Serpent's Mark (Poison tag) - on-kill poison spread =====
  log('=== TEST C: Serpents Mark - kill a 1hp primary, check a 20hp witness catches Poison ===');
  cmd('/emberfall granttome EmberTester serpents_mark'); await sleep(300);
  await spawnDummy('poisonPrimary', 2.5, 100, 0.5, 1.0);
  await spawnDummy('poisonWitness', 0.9, 100, 2.5, 20.0);
  cmd('/tp @s 0.5 100 0.5 facing 2.5 100 0.5'); await sleep(300);
  await sleep(2500);
  const poisonWitnessFx = await query('/data get entity @e[tag=poisonWitness,limit=1,sort=nearest] active_effects', /(minecraft:poison)/);
  log(`RESULT C: poisonWitness has poison = ${poisonWitnessFx} (should be "minecraft:poison")`);
  cmd('/kill @e[tag=poisonPrimary]'); await sleep(150);
  cmd('/kill @e[tag=poisonWitness]'); await sleep(300);

  // ===== TEST D: Storm Sigil (Lightning tag) - guaranteed every-4th-hit chain =====
  log('=== TEST D: Storm Sigil x3 (interval=4) - durable filler takes repeated hits, chain should reach a witness 3 blocks away periodically ===');
  cmd('/emberfall granttome EmberTester storm_sigil'); await sleep(250);
  cmd('/emberfall granttome EmberTester storm_sigil'); await sleep(250);
  cmd('/emberfall granttome EmberTester storm_sigil'); await sleep(250);
  await spawnDummy('stormFiller', 2.5, 100, 0.5, 1000.0);
  await spawnDummy('stormWitness', 1.2, 100, 3.2, 20.0);
  cmd('/tp @s 0.5 100 0.5 facing 2.5 100 0.5'); await sleep(300);
  let stormWitnessHp = 20.0;
  const deadline = Date.now() + 6000;
  let hitsObserved = 0;
  while (Date.now() < deadline) {
    const h = await query('/data get entity @e[tag=stormWitness,limit=1,sort=nearest] Health', /: ([\d.]+)f/);
    if (h !== null && Number(h) < stormWitnessHp - 0.01) {
      hitsObserved++;
      log(`stormWitness HP dropped: ${stormWitnessHp} -> ${h} (chain hit #${hitsObserved})`);
      stormWitnessHp = Number(h);
    }
    await sleep(200);
  }
  log(`RESULT D: stormWitness took ${hitsObserved} chain hit(s) total over 6s (expect roughly 1 hit per 4 landed Broadsword swings)`);
  cmd('/kill @e[tag=stormFiller]'); await sleep(150);
  cmd('/kill @e[tag=stormWitness]'); await sleep(300);

  // ===== TEST E: Unstable Core (Explosive tag) - independent on-kill chain detonate =====
  log('=== TEST E: Unstable Core x3 (60% chance, own detonate, NOT Volatile Rounds) - repeat 1hp kills near a witness, looking for any HP drop over several trials ===');
  cmd('/emberfall granttome EmberTester unstable_core'); await sleep(250);
  cmd('/emberfall granttome EmberTester unstable_core'); await sleep(250);
  cmd('/emberfall granttome EmberTester unstable_core'); await sleep(250);
  await spawnDummy('unstableWitness', 0.9, 100, 2.0, 100.0);
  cmd('/tp @s 0.5 100 0.5 facing 2.5 100 0.5'); await sleep(300);
  let unstableProcSeen = false;
  for (let i = 0; i < 8 && !unstableProcSeen; i++) {
    await spawnDummy('unstablePrimary', 2.5, 100, 0.5, 1.0);
    await sleep(1200);
    const wh = await query('/data get entity @e[tag=unstableWitness,limit=1,sort=nearest] Health', /: ([\d.]+)f/);
    log(`trial ${i+1}: unstableWitness HP = ${wh}`);
    if (wh === null || Number(wh) < 100.0) { unstableProcSeen = true; }
    cmd('/kill @e[tag=unstablePrimary]'); await sleep(200);
  }
  log(`RESULT E: Unstable Core chain-detonate proc observed at least once = ${unstableProcSeen}`);
  cmd('/kill @e[tag=unstableWitness]'); await sleep(300);

  // ===== TEST F: Sanguine Locket (Lifesteal tag) - on-kill Absorption shield =====
  log('=== TEST F: Sanguine Locket x2 (Absorption II on kill) - kill a 1hp target, check own active_effects ===');
  cmd('/emberfall granttome EmberTester sanguine_locket'); await sleep(250);
  cmd('/emberfall granttome EmberTester sanguine_locket'); await sleep(250);
  await spawnDummy('shieldPrimary', 2.5, 100, 0.5, 1.0);
  cmd('/tp @s 0.5 100 0.5 facing 2.5 100 0.5'); await sleep(300);
  await sleep(2000);
  const ownAbsorb = await query('/data get entity @s active_effects', /(minecraft:absorption)/);
  log(`RESULT F: own active_effects has absorption = ${ownAbsorb} (should be "minecraft:absorption")`);
  cmd('/kill @e[tag=shieldPrimary]'); await sleep(300);

  // ===== TEST G: Reliquary Shard (Summon tag) - retroactive companion buff =====
  log('=== TEST G: Reliquary Shard x2 - grant Loyal Hound first, check base stats, then grant Reliquary Shard x2 and check the SAME wolf gets +2 dmg / +40% HP retroactively ===');
  cmd('/emberfall granttome EmberTester loyal_hound'); await sleep(400);
  const dmgBefore = await query('/attribute @e[type=minecraft:wolf,limit=1,sort=nearest] minecraft:attack_damage base get', /is ([\d.]+)/);
  const hpBefore = await query('/attribute @e[type=minecraft:wolf,limit=1,sort=nearest] minecraft:max_health base get', /is ([\d.]+)/);
  log(`wolf before Reliquary Shard: attack=${dmgBefore} maxHealth=${hpBefore}`);
  cmd('/emberfall granttome EmberTester reliquary_shard'); await sleep(300);
  cmd('/emberfall granttome EmberTester reliquary_shard'); await sleep(300);
  const dmgAfter = await query('/attribute @e[type=minecraft:wolf,limit=1,sort=nearest] minecraft:attack_damage get', /is ([\d.]+)/);
  const hpAfter = await query('/attribute @e[type=minecraft:wolf,limit=1,sort=nearest] minecraft:max_health get', /is ([\d.]+)/);
  log(`RESULT G: wolf after Reliquary Shard x2: attack(total)=${dmgAfter} (expect +2 over base) maxHealth(total)=${hpAfter} (expect +40% over base)`);
  cmd('/kill @e[type=minecraft:wolf]'); await sleep(300);

  cmd('/kill @e[type=emberfall:horde_zombie]');
  await sleep(300);
  cmd('/expedition leave'); await sleep(1000);
  bot.quit(); await sleep(500); process.exit(0);
});
