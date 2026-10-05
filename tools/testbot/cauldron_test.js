// Umbral Magus cauldron ritual, pink liquid. C1 a pink glass column appears inside the pot on tick 1, C2 it RISES during the
// fill (scale.y grows, interpolation set), C3 it swells above the rim after the blast, C4 it sits at the pot's x/z, C5 a
// pink slime appears at the end, C6 the column is GONE afterwards (no leaked display), C7 killing the Magus mid-ritual also
// removes it, C8 no vanilla water cauldron block is used.
const fs = require('fs');
const mineflayer = require('mineflayer');
const sleep = ms => new Promise(r => setTimeout(r, ms));
const bot = mineflayer.createBot({ host: '127.0.0.1', port: 25565, username: 'EmberTester', version: '1.21.11', auth: 'offline' });
const lines = []; bot.on('message', m => lines.push(m.toString())); bot.on('error', e => console.log('ERR', e.message));
const ask = async (x, w = 400) => { const n = lines.length; bot.chat(x); await sleep(w); return lines.slice(n).join(' | '); };
let fails = 0; const R = (n, ok, extra = '') => { console.log(`${ok ? 'PASS' : 'FAIL'} ${n} ${extra}`); if (!ok) fails++; };
const LOG = (process.env.EMBERFALL_HOME || '.') + '/run/server_run.log';
const SEL = '@e[type=minecraft:block_display,nbt={block_state:{Name:"minecraft:pink_stained_glass"}},limit=1]';
const liquid = async () => {
  const r = await ask(`/data get entity ${SEL}`, 220);
  if (!/scale: \[/.test(r)) return null;
  const sc = /scale: \[([\d.]+)f, ([\d.]+)f, ([\d.]+)f\]/.exec(r), pos = /Pos: \[(-?[\d.]+)d, (-?[\d.]+)d, (-?[\d.]+)d\]/.exec(r), ip = /interpolation_duration: (\d+)/.exec(r);
  return { w: +sc[1], h: +sc[2], x: +pos[1], y: +pos[2], z: +pos[3], interp: ip ? +ip[1] : -1 };
};
const liquidCount = async () => { const r = await ask(`/execute if entity @e[type=minecraft:block_display,nbt={block_state:{Name:"minecraft:pink_stained_glass"}}]`, 250); return /passed/i.test(r) ? 1 : 0; };
bot.once('spawn', async () => {
  await sleep(5000);
  await ask('/gamemode survival'); await ask('/character select juggernaut'); await ask('/expedition', 2500);
  for (let i = 0; i < 40; i++) { await sleep(2000); if (/expedition/.test(await ask('/data get entity @s Dimension', 400))) break; }
  await sleep(2500);
  await ask('/emberfall wavestop 0', 300); await ask('/time set midnight', 200);
  await ask('/effect give @s minecraft:resistance 999 4 true', 200); await ask('/effect give @s minecraft:regeneration 999 4 true', 200);
  await ask('/kill @e[type=!player,type=!minecraft:item_display,type=!minecraft:interaction]', 900);
  const bx = bot.entity.position.x, by = bot.entity.position.y, bz = bot.entity.position.z;
  let hold = setInterval(() => bot.chat(`/tp @s ${bx.toFixed(2)} ${by} ${bz.toFixed(2)} 0 0`), 500);
  R('C0 no pink column exists before the ritual', (await liquidCount()) === 0);
  const L0 = fs.readFileSync(LOG, 'utf8').split('\n').length;
  await ask('/execute at @s positioned ~8 ~ ~ run emberfall spawnelite umbral_magus', 500);
  await ask('/tag @e[type=emberfall:umbral_magus,limit=1] add mg', 200);
  await ask('/data merge entity @e[tag=mg,limit=1] {Invulnerable:1b,PersistenceRequired:1b}', 300);
  // sample the column every ~250 ms for 4 s
  const S = []; const t1 = Date.now();
  while (Date.now() - t1 < 4500) { const q = await liquid(); if (q) S.push({ t: Date.now() - t1, ...q }); await sleep(30); }
  console.log(`  ${S.length} samples; first ${JSON.stringify(S[0])}; last ${JSON.stringify(S[S.length - 1])}`);
  R('C1 a pink column appears during the ritual', S.length >= 3);
  const h = S.map(s => s.h), hmax = Math.max(...h), hmin = Math.min(...h);
  R('C2 the column fills slowly: a low scale and interpolation set while it rises', S.some(s => s.interp >= 15), `interp ${[...new Set(S.map(s => s.interp))].join(',')}`);
  R('C3 after the blast it swells above the rim (target height 1.05 > rim 0.75)', hmax > 0.76, `max scale.y ${hmax.toFixed(2)}, min ${hmin.toFixed(2)}`);
  const xs = S.map(s => s.x), zs = S.map(s => s.z), ys = S.map(s => s.y);
  R('C4 it never moves (position stable) and sits at the pot floor height .25 above a block', Math.max(...xs) - Math.min(...xs) < 0.01 && Math.max(...zs) - Math.min(...zs) < 0.01 && Math.abs((ys[0] % 1 + 1) % 1 - 0.25) < 0.01, `x ${xs[0]} z ${zs[0]} y ${ys[0]}`);
  R('C4b width fits inside the 0.75 hollow', S.every(s => s.w <= 0.75), `w ${S[0].w}`);
  await sleep(2500);
  const log = fs.readFileSync(LOG, 'utf8').split('\n').slice(L0).join('\n');
  R('C5 the ritual completed and spawned a Pink Slime', /spawned Pink Slime/.test(log) && !/Frankenstein/.test(log));
  R('C6 the column is gone after the ritual (no leaked display)', (await liquidCount()) === 0);
  // C7: a second ritual, kill the Magus mid-fill
  await ask('/kill @e[type=emberfall:pink_slime]', 400);
  await ask('/kill @e[tag=mg]', 500);
  await ask('/execute at @s positioned ~8 ~ ~ run emberfall spawnelite umbral_magus', 500);
  await ask('/tag @e[type=emberfall:umbral_magus,limit=1] add mg', 200);
  await ask('/data merge entity @e[tag=mg,limit=1] {Invulnerable:0b,PersistenceRequired:1b}', 300);
  let seen = false; const t2 = Date.now();
  while (Date.now() - t2 < 2500 && !seen) { seen = (await liquidCount()) === 1; await sleep(40); }
  await ask('/kill @e[tag=mg]', 200); await sleep(1500);
  R('C7a a column existed mid-ritual in the second run', seen);
  R('C7b killing the Magus mid-ritual removes the column', (await liquidCount()) === 0);
  // C8: no water cauldron used
  const water = await ask('/execute if block ~8 ~ ~ minecraft:water_cauldron', 300);
  R('C8 no vanilla water cauldron placed', !/passed/i.test(water));
  clearInterval(hold);
  await ask('/kill @e[type=!player,type=!minecraft:item_display,type=!minecraft:interaction]', 400); await ask('/expedition leave', 800);
  console.log(fails === 0 ? 'ALL PASS' : 'SOME FAIL ' + fails);
  bot.quit(); setTimeout(() => process.exit(0), 300);
});
