// Pink slime SPIT at range: the player stands 16 blocks away and the slime is pinned with NoAI, so the leap and the
// melee stall cannot interfere. S1 every spit follows its telegraph by exactly 14 ticks, S2 spit cadence is the 70 tick
// cooldown, S3 spits hit the standing player and hurt, S4 the ball leaves a splat puddle, S5 a glass wall between them stops a NEW ball (see the S5 block).
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
  while (Date.now() - t1 < 15000) {
    const r = await ask('/execute as @e[tag=ps,limit=1] at @s if entity @p[distance=..9] run tp @s ~16 ~ ~', 150);
    if (/Teleported/.test(r)) resets++;
    await sleep(250);
  }
  console.log(`  pulled back ${resets} times`);
  const C = trace().slice(t0);
  console.log(`CONTROL (no wall, 15 s at range): telegraphs ${C.filter(l=>l.includes('spit telegraph')).length}, spits ${C.filter(l=>/ spit tick=/.test(l)).length}, hits ${C.filter(l=>l.includes('spit hit')).length}, landed ${C.filter(l=>l.includes('spit landed')).length}`);
  const mid = bx + 8;
  const pull = setInterval(() => bot.chat('/execute as @e[tag=ps,limit=1] at @s if entity @p[distance=..9] run tp @s ~16 ~ ~'), 400);
  await ask(`/fill ${Math.floor(mid)} ${Math.floor(by)} ${Math.floor(bz) - 3} ${Math.floor(mid)} ${Math.floor(by) + 2} ${Math.floor(bz) + 3} minecraft:glass`, 500);
  await sleep(3000);
  const w0 = trace().length;
  const sx = []; const t2 = Date.now();
  while (Date.now() - t2 < 20000) { const r = await ask('/data get entity @e[tag=ps,limit=1] Pos', 250); const m = [...r.matchAll(/(-?\d+\.?\d*)d/g)].map(x => +x[1]); if (m.length >= 3) sx.push(m[0].toFixed(1) + ',' + m[1].toFixed(1)); await sleep(500); }
  console.log('SLIME x,y every ~0.75 s (wall at x=' + mid.toFixed(1) + ', player x=' + bx.toFixed(1) + '):', sx.join(' '));
  const W = trace().slice(w0);
  console.log(`WALL (glass, 20 s at range): telegraphs ${W.filter(l=>l.includes('spit telegraph')).length}, spits ${W.filter(l=>/ spit tick=/.test(l)).length}, hits ${W.filter(l=>l.includes('spit hit')).length}, landed ${W.filter(l=>l.includes('spit landed')).length}, burst ${W.filter(l=>l.includes('burst')).length}`);
  console.log('slime alive:', /passed/i.test(await ask('/execute if entity @e[tag=ps]', 400)));
  clearInterval(pull); clearInterval(hold);
  await ask('/kill @e[type=!player,type=!minecraft:item_display,type=!minecraft:interaction]', 300); await ask('/expedition leave', 800);
  console.log('PROBE_DONE'); bot.quit(); setTimeout(() => process.exit(0), 300);
});
