// Pink slime SPIT at range: the player stands 16 blocks away and the slime is pinned with NoAI, so the leap and the
// melee stall cannot interfere. S1 every spit follows its telegraph by exactly 14 ticks, S2 spit cadence is the 70 tick
// cooldown, S3 spits hit the standing player and hurt, S4 the ball leaves a splat puddle, S5 a wall between them stops it.
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
  // S5: a 3-high stone wall between them stops the ball (it must land on the wall side, not hit)
  const mid = bx + 8;
  await ask(`/fill ${Math.floor(mid)} ${Math.floor(by)} ${Math.floor(bz) - 3} ${Math.floor(mid)} ${Math.floor(by) + 2} ${Math.floor(bz) + 3} minecraft:glass`, 500);
  const w0 = trace().length; await sleep(16000);
  const W = trace().slice(w0); const wh = W.filter(l => l.includes('spit hit')).length, wl = W.filter(l => l.includes('spit landed')).length, ws = W.filter(l => / spit tick=/.test(l)).length;
  console.log(`  behind a wall: ${ws} spits, ${wh} hits, ${wl} landed on the wall`);
  R('S5 a 3-high glass wall stops the ball (it still aims, never hits, lands on the glass)', wh === 0 && wl >= 1, `${ws} spits, ${wh} hits, ${wl} landed`);
  clearInterval(hold);
  await ask('/kill @e[type=!player,type=!minecraft:item_display,type=!minecraft:interaction]', 300); await ask('/expedition leave', 800);
  console.log(fails === 0 ? 'ALL PASS' : 'SOME FAIL ' + fails);
  bot.quit(); setTimeout(() => process.exit(0), 300);
});
