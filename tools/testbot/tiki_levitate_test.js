// Levitation per tier: lifts off to about the tier height above the floor, glides toward the player, then lands again.
const mineflayer = require('mineflayer');
const sleep = ms => new Promise(r => setTimeout(r, ms));
const bot = mineflayer.createBot({ host: '127.0.0.1', port: 25565, username: 'EmberTester', version: '1.21.11', auth: 'offline' });
const chat = []; bot.on('message', m => chat.push(m.toString())); bot.on('error', e => console.log('ERR', e.message));
const ask = async (c, w = 450) => { chat.length = 0; bot.chat(c); await sleep(w); return chat.join(' | '); };
let fails = 0; const check = (n, ok, d = '') => { console.log((ok ? 'PASS ' : 'FAIL ') + n + (d ? '  ' + d : '')); if (!ok) fails++; };
const nums = r => [...r.matchAll(/(-?\d+\.?\d*(?:E-?\d+)?)[df]/g)].map(m => parseFloat(m[1]));
const FLOOR = 200;
async function trial(label, cmd, wantHeight, secs) {
  await ask('/kill @e[type=!player]', 700); await sleep(1500);
  await ask(cmd, 1500);
  await ask('/tag @e[type=emberfall:tiki_magma,limit=1] add mine', 300);
  await ask('/execute at @s run tp @e[tag=mine,limit=1] 14.5 200 0.5', 500);
  const trace = []; const t0 = Date.now();
  while (Date.now() - t0 < secs * 1000) {
    const p = nums(await ask('/data get entity @e[tag=mine,limit=1] Pos', 300));
    if (p.length >= 3) trace.push({ t: Date.now() - t0, h: p[1] - FLOOR, d: Math.hypot(p[0] - 0.5, p[2] - 0.5) });
    await sleep(60);
  }
  // A slime hop also clears 0.8 for about 0.4 s, so judge LIFTS: contiguous stretches held near the tier height for >= 1 s.
  const nearTop = x => x.h >= wantHeight * 0.75;
  const lifts = []; let cur = null;
  for (const x of trace) {
    if (nearTop(x)) { if (!cur) cur = { t0: x.t, t1: x.t, d0: x.d, d1: x.d, max: x.h }; cur.t1 = x.t; cur.d1 = x.d; cur.max = Math.max(cur.max, x.h); }
    else if (cur) { lifts.push(cur); cur = null; }
  }
  if (cur) lifts.push(cur);
  const held = lifts.filter(l => (l.t1 - l.t0) >= 1000);
  const best = held.sort((p, q) => (q.t1 - q.t0) - (p.t1 - p.t0))[0];
  console.log('   trace: ' + trace.filter((x, i) => i % 2 === 0).map(x => x.h.toFixed(1)).join(' '));
  console.log(`   ${label}: ${lifts.length} stretches near ${wantHeight}, ${held.length} held >= 1 s` + (best ? `, best ${((best.t1 - best.t0) / 1000).toFixed(1)} s at max ${best.max.toFixed(2)}, distance ${best.d0.toFixed(1)} -> ${best.d1.toFixed(1)}` : ''));
  check(label + ' L1 holds a hover for at least 1 s', !!best, `${held.length} held stretches`);
  check(label + ' L2 hover height is about the tier height', !!best && best.max >= wantHeight * 0.85 && best.max <= wantHeight + 0.6, best ? `max ${best.max.toFixed(2)} (want ${wantHeight})` : 'no hover');
  check(label + ' L3 glides toward the player while aloft', !!best && best.d0 - best.d1 >= 1.0, best ? `${best.d0.toFixed(1)} -> ${best.d1.toFixed(1)}` : 'no hover');
  const after = best ? trace.filter(x => x.t > best.t1) : [];
  check(label + ' L4 comes back down to the floor', after.some(x => x.h < 0.3), `${after.length} samples after the hover`);
}
bot.once('spawn', async () => {
  await sleep(5000); await ask('/op EmberTester'); await ask('/gamemode survival');
  await ask('/fill -20 199 -20 20 199 20 minecraft:stone', 1800); await ask('/tp @s 0.5 201 0.5', 1500);
  await ask('/effect give @s minecraft:resistance 999 4 true'); await ask('/effect give @s minecraft:regeneration 999 4 true');
    await trial('CORRUPTED', '/execute at @s run emberfall spawnelite tiki_magma_corrupted', 3.6, 24);
  console.log(fails === 0 ? 'ALL PASS' : 'SOME FAIL ' + fails);
  bot.quit(); setTimeout(() => process.exit(0), 300);
});
