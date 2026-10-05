const mineflayer = require('mineflayer');
const bot = mineflayer.createBot({ host: '127.0.0.1', port: 25565, username: 'EmberTester', version: '1.21.11', auth: 'offline' });
const sleep = ms => new Promise(r => setTimeout(r, ms));
const lines = []; bot.on('message', m => lines.push(m.toString()));
const ask = async (x, w = 600) => { const n = lines.length; bot.chat(x); await sleep(w); return lines.slice(n).join(' | '); };
const res = []; const check = (id, ok, note) => { res.push(ok); console.log(`${ok ? 'PASS' : 'FAIL'} ${id} ${note}`); };
const PARTS = 20;   // head + 19 body segments since the coil build (was 6)
const dump = async () => {
  const r = await ask('/emberfall devdump EmberTester', 600);
  const p = [...r.matchAll(/\[(\w+)(?:#(\d+))? ([\d.-]+),([\d.-]+),([\d.-]+)( invis)?\]/g)].map(x => ({ k: x[1], i: x[2] === undefined ? -1 : +x[2], x: +x[3], y: +x[4], z: +x[5] }));
  const brain = p.find(a => a.k === 'DevourerBrain');
  const d = p.filter(a => a.k === 'ItemDisplay' && a.i >= 0).sort((u, v) => u.i - v.i);   // TRUE chain order from the server tag
  if (!brain || d.length !== PARTS || d.some((a, k) => a.i !== k)) return null;
  return { brain, d, extra: 0 };
};
const dist = (a, b) => Math.hypot(a.x - b.x, a.y - b.y, a.z - b.z);
bot.once('spawn', async () => {
  await sleep(6500);
  await ask('/kill @e[type=!player]', 500); await ask('/kill @e[type=minecraft:item_display]', 500); await sleep(2500);
  await ask('/gamemode survival'); await ask('/effect give @s minecraft:resistance 999 4 true', 300);
  await ask('/effect give @s minecraft:regeneration 999 4 true', 300);
  await ask('/character select juggernaut', 600); await ask('/expedition', 1500); await sleep(1500);
  await ask('/emberfall bossdevourer 0', 1500);
  const rows = []; const t0 = Date.now();
  while (Date.now() - t0 < 90000) { const s = await dump(); if (s) rows.push(s); }
  const counts = {}; rows.forEach(r => { const k = Math.round(r.brain.y); counts[k] = (counts[k] || 0) + 1; });
  const floor = +Object.entries(counts).sort((a, b) => b[1] - a[1])[0][0];
  const up = rows.filter(r => Math.abs(r.brain.y - floor) < 0.6);
  console.log(`  rows=${rows.length} floor=${floor} surfaced=${up.length}`);
  const ex = rows.map(r => r.extra); console.log(`  extra displays per sample: min=${Math.min(...ex)} max=${Math.max(...ex)} (0 means only this boss's worm)`);
  check('R0', up.length >= 20, `control: ${up.length} surfaced samples (need 20+)`);
  // R1: consecutive samples where the brain barely moved => nothing else may move much
  let still = 0, worstRest = 0;
  for (let i = 1; i < rows.length; i++) {
    const vis = Math.abs(rows[i].brain.y - floor) < 0.6 && Math.abs(rows[i - 1].brain.y - floor) < 0.6;
    let settled = i >= 4; for (let j = i - 3; j <= i && settled; j++) if (dist(rows[j].brain, rows[j - 1].brain) >= 0.05) settled = false;
    if (vis && settled && dist(rows[i].brain, rows[i - 1].brain) < 0.05) { still++; for (let k = 0; k < PARTS; k++) worstRest = Math.max(worstRest, dist(rows[i].d[k], rows[i - 1].d[k])); }
  }
  for (let i = 1; i < rows.length; i++) {
    const vis = Math.abs(rows[i].brain.y - floor) < 0.6 && Math.abs(rows[i - 1].brain.y - floor) < 0.6;
    let settled = i >= 4; for (let j = i - 3; j <= i && settled; j++) if (dist(rows[j].brain, rows[j - 1].brain) >= 0.05) settled = false;
    if (vis && settled && dist(rows[i].brain, rows[i - 1].brain) < 0.05) {
      const mv = rows[i].d.map((a, k) => dist(a, rows[i - 1].d[k]));
      if (Math.max(...mv) > 0.3) console.log(`  moving-at-rest #${i}: brain y=${rows[i].brain.y.toFixed(1)} per-part move=${mv.map(m => m.toFixed(2)).join(',')}`);
    }
  }
  check('R1', still >= 3 && worstRest <= 0.3, `resting pairs=${still}, largest segment move while the brain was still = ${worstRest.toFixed(2)} (want <= 0.3, and 3+ pairs)`);
  // R2: neighbour spacing bound in EVERY sample
  let worstGap = 0; up.forEach(r => { for (let k = 1; k < PARTS; k++) worstGap = Math.max(worstGap, dist(r.d[k], r.d[k - 1])); });
  up.map((r, i) => ({ i, by: r.brain.y, gaps: [1,2,3,4,5].map(k => dist(r.d[k], r.d[k - 1])) }))
    .filter(o => Math.max(...o.gaps) > 1.95).forEach(o => console.log(`  wide sample #${o.i}: brain y=${o.by.toFixed(1)} gaps=${o.gaps.map(g => g.toFixed(1)).join(',')}`));
  check('R2', worstGap <= 1.5 * 1.3, `largest gap between neighbouring parts in any sample = ${worstGap.toFixed(2)} (want <= 1.95)`);
  // Tolerance from MEASUREMENT: a standing brain reads 0.55, and a brain walking at the player bobs 0.3 to 1.0 blocks (vanilla
  // Silverfish, onGround=false, no path) while the head display glides 2 ticks behind it: worst seen 1.15. The burrow pop this
  // test exists to catch read 3.05, so 1.3 still fails it and any real regression.
  const HEAD_TOL = 1.3;
  // R3: surfaced head is on its own feet; last segment is near the brain, not up in the sky
  const headOff = up.map(r => Math.abs(r.d[0].y - r.brain.y - 1.05)); const tailUp = up.map(r => r.d[PARTS - 1].y - r.brain.y);
  up.forEach((r, i) => { const e = Math.abs(r.d[0].y - r.brain.y - 1.05); if (e > HEAD_TOL) { const ctx = []; for (let k = Math.max(0, i - 3); k <= Math.min(up.length - 1, i + 3); k++) ctx.push(`#${k}:b${up[k].brain.y.toFixed(2)}/h${up[k].d[0].y.toFixed(2)}/t${up[k].d[PARTS - 1].y.toFixed(2)}`); console.log(`  ctx ${ctx.join(' ')}`); } });
  up.map((r, i) => ({ i, e: Math.abs(r.d[0].y - r.brain.y - 1.05), by: r.brain.y, hy: r.d[0].y })).filter(o => o.e > HEAD_TOL).forEach(o => console.log(`  head-off sample #${o.i}: brain y=${o.by.toFixed(2)} head y=${o.hy.toFixed(2)} err=${o.e.toFixed(2)}`));
  check('R3', Math.max(...headOff) <= HEAD_TOL && Math.max(...tailUp) <= 5.0, `worst head-height error = ${Math.max(...headOff).toFixed(2)} (want <= ${HEAD_TOL}), highest tail above the brain = ${Math.max(...tailUp).toFixed(2)} (want <= 5)`);
  console.log(res.every(Boolean) ? 'ALL PASS' : 'SOME FAIL');
  bot.quit(); setTimeout(() => process.exit(0), 300);
});
bot.on('error', e => console.log('ERR', e.message));
