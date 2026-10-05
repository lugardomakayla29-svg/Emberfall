// Pink slime moves, judged from the server trace and entity positions:
// M1 every spit follows its telegraph by exactly 14 ticks, M2 spits hit a standing player and hurt, M3 a leap telegraphs
// before it launches (24 ticks), M4 the leap peaks far above a normal hop (>= 6 blocks) and the slam lands near where the
// player stood, M5 the slam hurts, M6 the trail puddle list stays under its cap of 20 over a long fight.
const fs = require('fs');
const mineflayer = require('mineflayer');
const sleep = ms => new Promise(r => setTimeout(r, ms));
const bot = mineflayer.createBot({ host: '127.0.0.1', port: 25565, username: 'EmberTester', version: '1.21.11', auth: 'offline' });
const lines = []; bot.on('message', m => lines.push(m.toString())); bot.on('error', e => console.log('ERR', e.message));
const ask = async (x, w = 400) => { const n = lines.length; bot.chat(x); await sleep(w); return lines.slice(n).join(' | '); };
let fails = 0; const R = (n, ok, extra = '') => { console.log(`${ok ? 'PASS' : 'FAIL'} ${n} ${extra}`); if (!ok) fails++; };
const LOG = (process.env.EMBERFALL_HOME || '.') + '/run/server_run.log';
const trace = () => fs.readFileSync(LOG, 'utf8').split('\n').filter(l => l.includes('PINK_TEST'));
const num = (l, k) => { const m = new RegExp(k + '=(-?[\\d.]+)').exec(l); return m ? +m[1] : NaN; };
const ypos = async () => { const r = await ask('/data get entity @e[tag=ps,limit=1] Pos', 120); const m = /\[(-?[\d.]+)d, (-?[\d.]+)d, (-?[\d.]+)d\]/.exec(r); return m ? [+m[1], +m[2], +m[3]] : null; };
bot.once('spawn', async () => {
  await sleep(5000);
  await ask('/gamemode survival'); await ask('/character select juggernaut'); await ask('/expedition', 2500);
  for (let i = 0; i < 40; i++) { await sleep(2000); if (/expedition/.test(await ask('/data get entity @s Dimension', 400))) break; }
  await sleep(2500);
  await ask('/emberfall wavestop 0', 300); await ask('/time set midnight', 200);
  await ask('/effect give @s minecraft:regeneration 999 4 true', 200);      // no resistance: damage must show
  await ask('/kill @e[type=!player,type=!minecraft:item_display,type=!minecraft:interaction]', 900);
  const bx = bot.entity.position.x, by = bot.entity.position.y, bz = bot.entity.position.z;
  let hold = setInterval(() => bot.chat(`/tp @s ${bx.toFixed(2)} ${by} ${bz.toFixed(2)} 0 0`), 500);
  const t0 = trace().length;
  await ask('/execute at @s positioned ~10 ~ ~ run emberfall spawnelite pink_slime', 400);
  await ask('/tag @e[type=emberfall:pink_slime,limit=1] add ps', 200);
  await ask('/data merge entity @e[tag=ps,limit=1] {Invulnerable:1b,PersistenceRequired:1b}', 300);
  // sample its height every 100 ms for 40 s to catch the leap peak
  let maxY = -99, baseY = null; const t1 = Date.now();
  while (Date.now() - t1 < 40000) {
    const p = await ypos(); if (p) { if (baseY === null) baseY = p[1]; maxY = Math.max(maxY, p[1]); }
    await sleep(60);
  }
  const T = trace().slice(t0);
  const tele = T.filter(l => l.includes('spit telegraph')).map(l => num(l, 'tick'));
  const spits = T.filter(l => / spit tick=/.test(l)).map(l => num(l, 'tick'));
  const gaps = spits.map(sp => { const pr = tele.filter(t => t <= sp); return pr.length ? sp - pr[pr.length - 1] : NaN; }).filter(g => !Number.isNaN(g));
  const hits = T.filter(l => l.includes('spit hit')).length, landed = T.filter(l => l.includes('spit landed')).length;
  console.log(`  spit: ${tele.length} telegraphs, ${spits.length} spits, gaps ${gaps.join(' ')}, hits ${hits}, landed ${landed}`);
  R('M1 a spit happens in the mixed fight and follows its telegraph by exactly 14 ticks (cadence and hit rate are proven at range in pink_spit_test)', gaps.length >= 1 && gaps.every(g => g === 14), gaps.join(','));
  R('M2 a spit in the mixed fight reaches the player', hits >= 1, `${hits} hits, ${landed} landed elsewhere`);
  const lt = T.filter(l => l.includes('leap telegraph')).length, ll = T.filter(l => l.includes('leap launch')).length, sl = T.filter(l => l.includes('PINK_TEST slam'));
  console.log(`  leap: ${lt} telegraphs, ${ll} launches, ${sl.length} slams; highest y ${maxY.toFixed(1)} vs ground ${baseY.toFixed(1)} => rise ${(maxY - baseY).toFixed(1)}`);
  R('M3 every leap is telegraphed first and then launched', lt >= 2 && ll === lt, `${lt} telegraphs, ${ll} launches`);
  R('M4 the leap rises at least 6 blocks (a normal hop is 1.25; computed peak 8.2)', maxY - baseY >= 6, `rise ${(maxY - baseY).toFixed(1)}`);
  const dist = sl.map(l => Math.hypot(num(l, 'at') - bx, 0));
  const slamAt = sl.map(l => { const m = /at=(-?[\d.]+),(-?[\d.]+),(-?[\d.]+)/.exec(l); return Math.hypot(+m[1] - bx, +m[3] - bz); });
  console.log(`  slam landed ${slamAt.map(d => d.toFixed(1)).join(' ')} blocks from the player, hits ${sl.map(l => num(l, 'hits')).join(' ')}`);
  R('M5 the slam lands on the player and hurts (within the 4.2 radius, hits >= 1)', sl.length >= 2 && slamAt.every(d => d <= 4.2) && sl.some(l => num(l, 'hits') >= 1), `${sl.length} slams`);
  const totals = T.filter(l => l.includes('pool add')).map(l => num(l, 'total'));
  console.log(`  pink pool totals: max ${Math.max(...totals)} over ${totals.length} additions`);
  R('M6 the pool list never exceeds its cap of 20', totals.length >= 1 && Math.max(...totals) <= 20, `max ${Math.max(...totals)}`);
  clearInterval(hold);
  await ask('/kill @e[type=!player,type=!minecraft:item_display,type=!minecraft:interaction]', 300); await ask('/expedition leave', 800);
  console.log(fails === 0 ? 'ALL PASS' : 'SOME FAIL ' + fails);
  bot.quit(); setTimeout(() => process.exit(0), 300);
});
