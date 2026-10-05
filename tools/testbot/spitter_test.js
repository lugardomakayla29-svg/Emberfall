// Horde Spitter: telegraphs, keeps its distance, spits acid that hurts and slows, and leaves a capped, burning puddle.
// S1 every spit is preceded by a telegraph exactly 12 ticks earlier (server trace), S2 it never closes inside 5.5 blocks,
// S3 a glob hits a standing player and the player loses health, S4 globs that MISS land puddles,
// S5 standing in a puddle burns (server trace), S6 the puddle list never exceeds 24 even with four spitters.
const fs = require('fs');
const mineflayer = require('mineflayer');
const sleep = ms => new Promise(r => setTimeout(r, ms));
const bot = mineflayer.createBot({ host: '127.0.0.1', port: 25565, username: 'EmberTester', version: '1.21.11', auth: 'offline' });
const lines = []; bot.on('message', m => lines.push(m.toString())); bot.on('error', e => console.log('ERR', e.message));
const ask = async (x, w = 500) => { const n = lines.length; bot.chat(x); await sleep(w); return lines.slice(n).join(' | '); };
let fails = 0; const R = (n, ok, extra = '') => { console.log(`${ok ? 'PASS' : 'FAIL'} ${n} ${extra}`); if (!ok) fails++; };
const LOG = (process.env.EMBERFALL_HOME || '.') + '/run/server_run.log';
const trace = () => fs.readFileSync(LOG, 'utf8').split('\n').filter(l => l.includes('SPITTER_TEST'));
const num = (l, k) => { const m = new RegExp(k + '=(\\d+)').exec(l); return m ? +m[1] : NaN; };
const pos = async tag => { const r = await ask(`/data get entity @e[tag=${tag},limit=1] Pos`, 120); const m = /\[(-?[\d.]+)d, (-?[\d.]+)d, (-?[\d.]+)d\]/.exec(r); return m ? [+m[1], +m[2], +m[3]] : null; };
const hp = async () => { const r = await ask('/data get entity @s Health', 150); const m = /(-?[\d.]+)f/.exec(r); return m ? +m[1] : NaN; };
bot.once('spawn', async () => {
  await sleep(5000);
  await ask('/gamemode survival'); await ask('/character select juggernaut'); await ask('/expedition', 2500);
  for (let i = 0; i < 40; i++) { await sleep(2000); if (/expedition/.test(await ask('/data get entity @s Dimension', 400))) break; }
  await sleep(2500);
  await ask('/emberfall wavestop 0', 300); await ask('/time set midnight', 200);
  await ask('/effect give @s minecraft:regeneration 999 4 true', 200);   // NO resistance: damage must be visible
  await ask('/kill @e[type=!player,type=!minecraft:item_display,type=!minecraft:interaction]', 900);
  const bx = bot.entity.position.x, by = bot.entity.position.y, bz = bot.entity.position.z;
  let hold = setInterval(() => bot.chat(`/tp @s ${bx.toFixed(2)} ${by} ${bz.toFixed(2)} 0 0`), 400);
  await ask('/execute at @s positioned ~10 ~ ~ run emberfall spawnveteran horde_spitter', 500);
  await ask('/tag @e[type=emberfall:horde_spitter,limit=1] add sp', 200);
  await ask('/data merge entity @e[tag=sp,limit=1] {Invulnerable:1b,PersistenceRequired:1b}', 300);

  // --- phase A: standing player, watch 14 s of behaviour
  const t0 = trace().length; const ds = []; let minHp = 99;
  for (let i = 0; i < 40; i++) {
    const p = await pos('sp'); if (p) ds.push(Math.hypot(p[0] - bx, p[2] - bz));
    const h = await hp(); if (!Number.isNaN(h) && h < minHp) minHp = h;
    await sleep(250);
  }
  const A = trace().slice(t0);
  const tele = A.filter(l => l.includes('telegraph')).map(l => num(l, 'tick'));
  const spits = A.filter(l => l.includes(' spit tick=')).map(l => num(l, 'tick'));
  const hits = A.filter(l => l.includes('spit hit')).length, landed = A.filter(l => l.includes('spit landed')).length;
  // pair each spit with the LATEST telegraph at or before it (index pairing drifts if the slice starts mid-cycle)
  const gaps = spits.map(sp => { const prior = tele.filter(t => t <= sp); return prior.length ? sp - prior[prior.length - 1] : NaN; });
  console.log(`  A: ${tele.length} telegraphs, ${spits.length} spits, telegraph-to-spit gaps ${gaps.join(' ')} ticks, hits ${hits}, landed-without-hit ${landed}, lowest hp ${minHp}`);
  console.log(`  distance to player: min ${Math.min(...ds).toFixed(1)} max ${Math.max(...ds).toFixed(1)} over ${ds.length} samples`);
  const okGaps = gaps.filter(g => !Number.isNaN(g));   // a spit whose telegraph predates the window has no partner
  R('S1 every spit follows its telegraph by exactly 12 ticks', okGaps.length >= 3 && okGaps.every(g => g === 12), okGaps.join(','));
  R('S2 it never closes inside 5.5 blocks (ranged, not melee)', ds.length >= 20 && Math.min(...ds) >= 5.5, `min ${Math.min(...ds).toFixed(1)}`);
  const mh = await ask('/attribute @s minecraft:max_health get', 250); const mhv = +((/is ([\d.]+)/.exec(mh) || [])[1]);
  console.log(`  player max health ${mhv}`);
  R('S3 globs hit the standing player and health dropped below max', hits >= 3 && minHp < mhv, `${hits} hits, min hp ${minHp} of ${mhv}`);

  // --- phase B: geometry worked out from the flight log. Bolts fly at head height and the tracker tests only the CELL the
  // bolt is in, so a 1.5-high wall is cleared from above. Use a 3-high stone wall at x+5 and lift the spitter onto a 3-high
  // platform at x+10: its sight ray passes the wall top at 68.18 (top is 68.0, clear) while the descending glob is at
  // 67.75 there (blocked). Every glob must END at the wall ('landed'), hit nothing and leave a puddle.
  const X = Math.floor(bx), Y = Math.floor(by), Z = Math.floor(bz);
  await ask(`/fill ${X + 5} ${Y} ${Z - 3} ${X + 5} ${Y + 2} ${Z + 3} minecraft:stone`, 400);
  await ask(`/fill ${X + 9} ${Y} ${Z - 1} ${X + 11} ${Y + 2} ${Z + 1} minecraft:stone`, 400);
  await ask(`/tp @e[tag=sp,limit=1] ${X + 10.5} ${Y + 3} ${Z + 0.5}`, 400);
  await sleep(1500);
  const b0 = trace().length;
  await sleep(9000);
  const B = trace().slice(b0);
  const bTele = B.filter(l => l.includes('telegraph')).length, bHit = B.filter(l => l.includes('spit hit')).length;
  const bLanded = B.filter(l => l.includes('spit landed')).length, bPools = B.filter(l => l.includes('pool add')).length;
  console.log(`  B (wall): ${bTele} telegraphs, ${bHit} hits, ${bLanded} landed at the wall, ${bPools} new pools`);
  R('S4a the wall stops the glob: it still telegraphs but nothing hits', bTele >= 3 && bHit === 0, `${bTele} telegraphs, ${bHit} hits`);
  R('S4b every stopped glob calls the end hook (landed)', bLanded >= 3 && bLanded >= bTele - 1, `${bLanded} landed of ${bTele}`);
  const bRefresh = B.filter(l => l.includes('pool refresh')).length;
  // every landing must either create a puddle or refresh the one already there: landings = adds + refreshes
  R('S4c every stopped glob leaves or refreshes a puddle', bLanded >= 3 && bPools + bRefresh === bLanded, `${bLanded} landed = ${bPools} new + ${bRefresh} refreshed`);
  await ask(`/fill ${X + 5} ${Y} ${Z - 3} ${X + 5} ${Y + 2} ${Z + 3} minecraft:air`, 400);
  await ask(`/fill ${X + 9} ${Y} ${Z - 1} ${X + 11} ${Y + 2} ${Z + 1} minecraft:air`, 400);
  await ask(`/tp @e[tag=sp,limit=1] ${X + 10.5} ${Y} ${Z + 0.5}`, 400);

  // --- phase C: stand in a puddle and count burns
  const burn0 = trace().filter(l => l.includes('pool burn')).length;
  await sleep(4000);
  const burned = trace().filter(l => l.includes('pool burn')).length - burn0;
  R('S5 standing where globs land burns (at least one burn tick)', burned >= 1, `${burned} burn ticks in 4 s`);

  // --- phase D: four spitters, long watch, cap must hold
  for (let k = 0; k < 3; k++) await ask('/execute at @s positioned ~' + (8 + k * 2) + ' ~ ~' + (k - 1) * 3 + ' run emberfall spawnveteran horde_spitter', 300);
  await ask('/data merge entity @e[type=emberfall:horde_spitter] {Invulnerable:1b,PersistenceRequired:1b}', 300);
  await sleep(26000);
  const totals = trace().filter(l => l.includes('pool add')).map(l => num(l, 'total')).filter(x => !Number.isNaN(x));
  console.log(`  pool totals seen: max ${Math.max(...totals)} over ${totals.length} additions`);
  R('S6 the puddle list never exceeds 24', totals.length >= 1 && Math.max(...totals) <= 24, `max ${Math.max(...totals)}`);

  clearInterval(hold);
  await ask('/kill @e[type=emberfall:horde_spitter]', 300); await ask('/expedition leave', 800);
  console.log(fails === 0 ? 'ALL PASS' : 'SOME FAIL ' + fails);
  bot.quit(); setTimeout(() => process.exit(0), 300);
});
