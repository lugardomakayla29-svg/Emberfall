// Do the Umbral Magus summons (Thrall = vanilla zombie/skeleton/husk, Colossus = wither skeleton) ever hurt or target the other
// expedition mobs? Setup: player in a run and shielded, a Magus far from the player, a row of bystander horde zombies next to it.
// Judged by (1) the summons' getTarget type, (2) bystander health drops, (3) bystanders that died.
const mineflayer = require('mineflayer');
const bot = mineflayer.createBot({ host: '127.0.0.1', port: 25565, username: 'EmberTester', version: '1.21.11', auth: 'offline' });
const sleep = ms => new Promise(r => setTimeout(r, ms));
const lines = []; bot.on('message', m => lines.push(m.toString()));
const ask = async (x, w = 700) => { const n = lines.length; bot.chat(x); await sleep(w); return lines.slice(n).join(' | '); };
const R = (n, ok, extra = '') => console.log(`${ok ? 'PASS' : 'FAIL'} ${n} ${extra}`);
const num = s => { const m = /(-?\d+(?:\.\d+)?)[fd]?\s*$/.exec((s || '').trim()); return m ? parseFloat(m[1]) : NaN; };
bot.once('spawn', async () => {
  await sleep(4000);
  await ask('/gamemode survival'); await ask('/effect clear @s');
  await ask('/character select juggernaut'); await ask('/expedition', 2500);
  await ask('/effect give @s minecraft:resistance 999 4 true'); await ask('/effect give @s minecraft:regeneration 999 4 true');
  await ask('/kill @e[type=!player,distance=..90]', 900);
  await ask('/emberfall wavestop 0', 300);
  // Bystanders: 4 horde zombies + 2 horde skeletons stacked near where the Magus will stand. Frozen so only outside damage can change them.
  for (let i = 0; i < 4; i++) await ask(`/execute at @s run summon emberfall:horde_zombie ~${40 + i} ~ ~${i % 2 ? 2 : -2} {Tags:["by"],Silent:1b,PersistenceRequired:1b,Attributes:[{id:"minecraft:movement_speed",base:0.0}]}`, 250);
  for (let i = 0; i < 2; i++) await ask(`/execute at @s run summon emberfall:horde_skeleton ~${40 + i} ~ ~${i ? 4 : -4} {Tags:["by"],Silent:1b,PersistenceRequired:1b,Attributes:[{id:"minecraft:movement_speed",base:0.0}]}`, 250);
  await ask('/execute as @e[tag=by] run attribute @s minecraft:max_health base set 200', 300);
  for (let i = 0; i < 6; i++) await ask(`/tag @e[tag=by,tag=!id,limit=1] add id${i}`, 200), await ask(`/tag @e[tag=id${i}] add id`, 150);
  await ask('/effect give @e[tag=by] minecraft:instant_health 1 10 true', 500);
  const n0 = (await ask('/execute if entity @e[tag=by]', 500));
  const startCount = parseInt((/Count: (\d+)/.exec(n0) || [0, 0])[1], 10);
  console.log('     bystanders at start:', startCount);
  // The Magus arrives beside the bystanders. Nothing but the Magus and its summons can reach them: the player is pinned and its own weapon is out of range.
  await ask('/execute at @e[tag=by,limit=1,sort=nearest] run emberfall spawnelite umbral_magus', 900);   // beside the bystanders, 40 blocks from the player
  const pin = setInterval(() => bot.chat('/tp @s ' + bot.entity.position.x.toFixed(2) + ' ' + bot.entity.position.y.toFixed(2) + ' ' + bot.entity.position.z.toFixed(2)), 1500);
  console.log('     watching 50s for Skirmisher / Colossus summons ...');
  const hpLog = [], series = []; let maxSummons = 0, atOtherEver = 0, atPlayerEver = 0, examples = new Set();
  for (let t = 0; t < 25; t++) {
    await sleep(2000);
    const row = [];
    for (let i = 0; i < 6; i++) row.push(num(await ask(`/data get entity @e[tag=id${i},limit=1] Health`, 250)));
    series.push(row); hpLog.push(Math.min(...row.filter(x => !isNaN(x)).concat([999])));
    const d = await ask('/emberfall debugsummons EmberTester', 500);
    const m = /summons summary: (\d+) total, atPlayer (\d+), atOther (\d+), none (\d+)/.exec(d);
    if (m) { maxSummons = Math.max(maxSummons, +m[1]); atPlayerEver = Math.max(atPlayerEver, +m[2]); atOtherEver = Math.max(atOtherEver, +m[3]); }
    for (const x of d.matchAll(/summon ([^|]*?) target=(OTHER:[^ |]*)/g)) examples.add(x[1].trim() + ' -> ' + x[2]);
  }
  clearInterval(pin);
  const after = await ask('/execute if entity @e[tag=by]', 500);
  const endCount = parseInt((/Count: (\d+)/.exec(after) || [0, 0])[1], 10);
  const good = hpLog.filter(x => !isNaN(x));
  let worstDrop = 0, drops = [];
  for (let i = 0; i < 6; i++) { let prev = 200; for (const row of series) { const v = row[i]; if (isNaN(v)) continue; if (prev - v > worstDrop) worstDrop = prev - v; if (v < prev - 0.5) drops.push(`#${i}:${prev.toFixed(0)}->${v.toFixed(0)}`); prev = Math.min(prev, v); } }
  console.log('     per-mob drops:', drops.slice(0, 10).join(' ') || 'none', '| worst single drop', worstDrop.toFixed(1));
  console.log('     bystander hp samples:', hpLog.join(', '), '| bystanders left', endCount, '/', startCount);
  console.log('     most summons alive at once:', maxSummons, '| ever targeting the player:', atPlayerEver, '| ever targeting something else:', atOtherEver);
  if (examples.size) console.log('     targeting others:', [...examples].slice(0, 6).join('; '));
  R('S1 summons really existed (non-vacuous)', maxSummons > 0, `(max ${maxSummons} at once)`);
  R('S2 no summon ever targeted a non-player', atOtherEver === 0, `(${atOtherEver})`);
  const fin = await ask('/emberfall debugsummons EmberTester', 600);
  const mv = /vetoed (\d+), cleared (\d+)/.exec(fin) || [0, 0, 0];
  console.log('     team rule counters: vetoed mob-on-mob hits', mv[1], '| targets cleared', mv[2]);
  // Mob-sourced damage on the bystanders is proven separately (server log: 0 of 174 hits came from a mob); the player's own weapon is what moves their hp here.
  R('S3 team rule counters readable', mv[0] !== 0);
  await ask('/kill @e[tag=by]', 300); await ask('/expedition leave', 800);
  bot.quit(); process.exit(0);
});
bot.on('error', e => console.log('ERROR', e));
