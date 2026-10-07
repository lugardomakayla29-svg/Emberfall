// Pink slime SPIT at range: the player stands 16 blocks away and the slime is pinned with NoAI, so the leap and the
// melee stall cannot interfere. S1 every spit follows its telegraph by exactly 14 ticks, S2 spit cadence is the 70 tick
// cooldown, S3 spits hit the standing player and hurt, S4 the ball leaves a splat puddle, S5 a glass wall blocks line of sight so no NEW spit starts (see the S5 block).
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
bot.once('spawn', async () => {
  await sleep(5000);
  await ask('/gamemode survival'); await ask('/character select juggernaut'); await ask('/expedition', 2500);
  for (let i = 0; i < 40; i++) { await sleep(2000); if (/expedition/.test(await ask('/data get entity @s Dimension', 400))) break; }
  await sleep(2500);
  await ask('/emberfall wavestop 0', 300); await ask('/time set midnight', 200);
  await ask('/effect give @s minecraft:regeneration 999 4 true', 200);
  await ask('/kill @e[type=!player,type=!minecraft:item_display,type=!minecraft:interaction]', 900);
  const bx = bot.entity.position.x, by = bot.entity.position.y, bz = bot.entity.position.z;
  let hold = setInterval(() => bot.chat(`/tp @s ${bx.toFixed(2)} ${by} ${bz.toFixed(2)} 0 0`), 500);
  const t0 = trace().length;
  await ask('/execute at @s positioned ~16 ~ ~ run emberfall spawnelite pink_slime', 400);
  await ask('/tag @e[type=emberfall:pink_slime,limit=1] add ps', 200);
  await ask('/data merge entity @e[tag=ps,limit=1] {Invulnerable:1b,PersistenceRequired:1b}', 300);
  // keep it at range: every 400 ms, if it has come within 9 blocks, teleport it back to 16 blocks out (its AI keeps running)
  const t1 = Date.now(); let resets = 0;
  while (Date.now() - t1 < 30000) {
    const r = await ask('/execute as @e[tag=ps,limit=1] at @s if entity @p[distance=..9] run tp @s ~16 ~ ~', 150);
    if (/Teleported/.test(r)) resets++;
    await sleep(250);
  }
  console.log(`  pulled back ${resets} times`);
  const T = trace().slice(t0);
  const tele = T.filter(l => l.includes('spit telegraph')).map(l => num(l, 'tick'));
  const spits = T.filter(l => / spit tick=/.test(l)).map(l => num(l, 'tick'));
  const gaps = spits.map(sp => { const pr = tele.filter(t => t <= sp); return pr.length ? sp - pr[pr.length - 1] : NaN; });
  const cad = spits.slice(1).map((s, i) => s - spits[i]);
  const hits = T.filter(l => l.includes('spit hit')).length, landed = T.filter(l => l.includes('spit landed')).length;
  const splats = T.filter(l => l.includes('kind=splat')).length;
  console.log(`  spits ${spits.length}, telegraph gaps ${gaps.join(' ')}, cadence ${cad.join(' ')}, hits ${hits}, landed ${landed}, splat pool events ${splats}`);
  R('S1 every spit follows its telegraph by exactly 14 ticks', gaps.length >= 5 && gaps.every(g => g === 14), gaps.join(','));
  R('S2 the cooldown holds: no two spits closer than 70 ticks, and most are exactly 70', cad.length >= 4 && cad.every(c => c >= 70) && cad.filter(c => c === 70).length >= cad.length / 2, cad.join(','));
  R('S3 spits hit the standing player', hits >= Math.floor(spits.length * 0.8) && hits >= 4, `${hits} of ${spits.length}`);
  R('S4 every ball leaves a splat puddle event', splats >= hits, `${splats} splat events`);
  // S5: a 3-high GLASS wall blocks line of sight, so a slime walking toward it starts NO new spit. Reworked after V14 (docs/audit/S5_flake.md).
  // WHAT THE PROBE SHOWED (tools/testbot/ref/s5_glass_probe.js, same fixture, two runs): no wall = 4 telegraphs / 4 spits in 15 s; with the glass up the
  // slime walks toward it with NO telegraph for ~18 s, then LEAPS (leap telegraph ~14 blocks out), clears the wall, lands and spits again. So the wall
  // is a test of the stretch BEFORE the first leap, and a window that runs past the leap measures the leap, not the wall. The old S5 ran after the
  // pull-back loop ended, so it measured leaps and a ball fired BEFORE the wall (counted as "landed on the wall"). Now: S5a CONTROL (spits at range with
  // no wall), S5b the stretch is long enough to mean something (>= 8 s from wall to first leap or end) and the slime was alive, S5c no telegraph, no
  // spit, no hit in that stretch. 5 s because the control cadence is a 70 tick cooldown (3.5 s), so a slime with line of sight telegraphs at least once in 5 s.
  const ctlSpits = spits.length;
  R('S5a CONTROL: the same slime did spit at range with no wall', ctlSpits >= 4, `${ctlSpits} spits before the wall`);
  const mid = bx + 8;
  const pull = setInterval(() => bot.chat('/execute as @e[tag=ps,limit=1] at @s if entity @p[distance=..15] run tp @s ~16 ~ ~'), 400);   // 15 not 9: LEAP_MAX is 14, so inside 15 it would leap over the wall
  await ask(`/fill ${Math.floor(mid)} ${Math.floor(by)} ${Math.floor(bz) - 3} ${Math.floor(mid)} ${Math.floor(by) + 2} ${Math.floor(bz) + 3} minecraft:glass`, 500);
  await sleep(3000);   // a ball fired just before the wall finishes its 14 tick telegraph + flight within ~2 s; let it land BEFORE the window
  const w0 = trace().length, tw0 = Date.now();
  let tLeap = null;
  while (Date.now() - tw0 < 12000) {
    if (trace().slice(w0).some(l => l.includes('leap telegraph'))) { tLeap = Date.now(); break; }
    await sleep(250);
  }
  // S5b and S5c MUST measure the SAME stretch: from the wall to the first leap telegraph (or the 12 s end). The first version timed the loop for S5b
  // but cut the log at the leap for S5c, so a mutant with NO wall (leap after 2 s) reported "12.0 s, 0 spits" (found by the red run).
  const secs = ((tLeap || Date.now()) - tw0) / 1000;
  const W = trace().slice(w0);
  const cut = W.findIndex(l => l.includes('leap telegraph'));
  const S = cut >= 0 ? W.slice(0, cut) : W;
  const wTele = S.filter(l => l.includes('spit telegraph')).length, ws = S.filter(l => / spit tick=/.test(l)).length, wh = S.filter(l => l.includes('spit hit')).length;
  const alive = /passed/i.test(await ask('/execute if entity @e[tag=ps]', 400));
  if (process.env.S5_DEBUG) console.log('  S5 window lines (' + W.length + ', cut at ' + cut + '):\n    ' + W.filter(l => /spit|leap|slam|burst/.test(l)).map(l => l.replace(/^.*PINK_TEST /, '')).join('\n    '));
  console.log(`  behind glass for ${secs.toFixed(1)} s before ${tLeap ? 'the first leap' : 'the window end (no leap)'}: ${wTele} telegraphs, ${ws} spits, ${wh} hits; slime alive ${alive}`);
  R('S5b the stretch is long enough that a wall-less slime would have spat: >= 5 s (cooldown is 3.5 s) with the slime alive', secs >= 5 && alive, `${secs.toFixed(1)} s, alive ${alive}`);
  R('S5c a 3-high glass wall stops the slime: no telegraph, no spit, no hit in that stretch', wTele === 0 && ws === 0 && wh === 0, `${wTele} telegraphs, ${ws} spits, ${wh} hits`);
  clearInterval(pull);
  clearInterval(hold);
  await ask('/kill @e[type=!player,type=!minecraft:item_display,type=!minecraft:interaction]', 300); await ask('/expedition leave', 800);
  console.log(fails === 0 ? 'ALL PASS' : 'SOME FAIL ' + fails);
  bot.quit(); setTimeout(() => process.exit(0), 300);
});
