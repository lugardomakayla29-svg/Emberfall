// Proves the three tome/popup fixes on a live server, Ranger (hunting bow only, 1 weapon slot, 1 tome slot).
const mineflayer = require('mineflayer');
const bot = mineflayer.createBot({ host: '127.0.0.1', port: 25565, username: 'EmberTester', version: '1.21.11', auth: 'offline' });
const sleep = ms => new Promise(r => setTimeout(r, ms));
const lines = []; bot.on('message', m => lines.push(m.toString())); bot.on('error', e => console.log('ERR', e.message));
const ask = async (x, w = 450) => { const n = lines.length; bot.chat(x); await sleep(w); return lines.slice(n).join(' | '); };
let fails = 0; const R = (n, ok, d) => { if (!ok) fails++; console.log((ok ? 'PASS ' : 'FAIL ') + n + ' :: ' + d); };
const WRONG = ['steady_hand', 'bleeding_edge', 'sundering_wake', 'grave_anchor', 'widening_gyre', 'undying_embers', 'arcane_convergence', 'piercing_laser'];
const BOW = ['hunters_instinct', 'spin_barrage'];
bot.once('spawn', async () => {
  await sleep(5000);
  await ask('/gamemode survival'); await ask('/effect clear @s');
  await ask('/character select ranger'); await ask('/expedition', 2500);
  for (let i = 0; i < 40; i++) { await sleep(2000); if (/expedition/.test(await ask('/data get entity @s Dimension', 400))) break; }
  await sleep(2000);
  await ask('/emberfall wavestop 0', 300);

  // ---- 1. GATING: 60 rolls, none may contain a tome gated to a weapon the Ranger does not carry.
  const seen = new Set(); let bad = [];
  for (let i = 0; i < 60; i++) {
    const r = await ask('/emberfall rolloffers EmberTester', 180);
    const m = r.match(/ROLL \[([^\]]*)\]/); if (!m) continue;
    for (const id of m[1].split(',').map(s => s.trim()).filter(Boolean)) { seen.add(id); if (WRONG.includes(id)) bad.push(id); }
  }
  R('T1 60 rolls never offered a tome for a weapon the Ranger does not carry', bad.length === 0, 'wrong=' + JSON.stringify([...new Set(bad)]) + ' distinct seen=' + seen.size);
  R('T1b the rolls do include ordinary tomes (test is not vacuous)', seen.size >= 5, [...seen].join(','));

  // ---- 2. SLOTS FULL: a one-slot Ranger with the bow must get NO weapon offer at all.
  await ask('/emberfall weaponoffer EmberTester', 500);
  const wp = await ask('/emberfall weaponpending EmberTester', 500);
  R('T2 a full weapon loadout gets no weapon offer (nothing pending)', !/WPEND .*(broadsword|twin_daggers|war_halberd|arcane_staff|gravechain|spectral_sickles|ashen_beacon)/.test(wp), wp.slice(-140));

  // ---- 3. STAT SERIES: vitality 2.0 flat; the n-th stack is 2 * 1.3479 * 0.85^(n-1).
  const hp0 = await ask('/attribute @s minecraft:max_health get', 400);
  const base = parseFloat((hp0.match(/-?[\d.]+\s*$/) || ['NaN'])[0]);
  const expected = n => { const D = 0.85, sc = 5 * (1 - D) / (1 - Math.pow(D, 5)); let t = 0; for (let k = 1; k <= n; k++) t += 2 * sc * Math.pow(D, k - 1); return t; };
  const got = {};
  let have = 0;
  for (const n of [1, 2, 5, 12]) {
    while (have < n) { await ask('/emberfall granttome EmberTester vitality_charm', 250); have++; }
    const t = await ask('/attribute @s minecraft:max_health get', 400);
    got[n] = parseFloat((t.match(/-?[\d.]+\s*$/) || ['NaN'])[0]) - base;
  }
  console.log('BASE', base, 'GOT', JSON.stringify(got));
  for (const n of [1, 2, 5, 12]) R('T3 vitality x' + n + ' adds ' + expected(n).toFixed(2), Math.abs(got[n] - expected(n)) < 0.05, 'got ' + (got[n] || NaN).toFixed(3));
  R('T3b 5 stacks equal the old cap of 10.0', Math.abs(got[5] - 10.0) < 0.05, got[5]);
  R('T3c stack 12 is still rising but under the limit 17.98', got[12] > got[5] && got[12] < 17.98, got[12]);

  await ask('/expedition leave', 800);
  console.log(fails === 0 ? 'ALL PASS' : 'SOME FAIL ' + fails);
  bot.quit(); setTimeout(() => process.exit(0), 300);
});
