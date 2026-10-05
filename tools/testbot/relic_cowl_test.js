// Wizard's Cowl shortens the gap between swings. Count REAL swings on a pinned foe (each swing is one health step) over a fixed window.
const mineflayer = require('mineflayer');
const bot = mineflayer.createBot({ host: '127.0.0.1', port: 25565, username: 'EmberTester', version: '1.21.11', auth: 'offline' });
const sleep = ms => new Promise(r => setTimeout(r, ms));
const lines = []; bot.on('message', m => lines.push(m.toString()));
let fails = 0; const check = (l, ok, e = '') => { console.log((ok ? 'PASS ' : 'FAIL ') + l + (e ? '  ' + e : '')); if (!ok) fails++; };
const BIG = 1000000;
bot.once('spawn', async () => {
  await sleep(6000);
  const c = async (x, w = 600) => { bot.chat(x); await sleep(w); };
  const ask = async (x, w = 600) => { const n = lines.length; bot.chat(x); await sleep(w); return lines.slice(n).join(' | '); };
  const hp = async () => { for (let k = 0; k < 4; k++) { const t = await ask('/data get entity @e[tag=f1,limit=1] Health', 300 + k * 150); const m = t.match(/entity data: (-?[\d.]+)f/); if (m) return parseFloat(m[1]); } return null; };
  const startRun = async () => { await c('/expedition leave', 900); await c('/expedition', 4000); for (let i = 0; i < 30; i++) { await sleep(1500); if (/expedition/.test(await ask('/data get entity @s Dimension', 400))) break; } await sleep(1500); };
  await c('/gamemode survival'); await c('/character select vanguard');
  await startRun();
  await c('/effect give EmberTester minecraft:resistance 999 4 true', 300); await c('/effect give EmberTester minecraft:regeneration 999 4 true', 300);
  const purge = setInterval(() => bot.chat('/kill @e[type=!player,type=!item,tag=!keep]'), 1500);
  await sleep(2500);
  await c(`/execute at @s run summon emberfall:horde_zombie ~2 ~ ~0 {Tags:["f1","keep"],NoAI:1b,Silent:1b,PersistenceRequired:1b}`, 500);
  await c(`/attribute @e[tag=f1,limit=1] minecraft:max_health base set ${BIG}`, 200);
  // Count HITS, not damage: sample the foe's health several times a second and count every drop. The Broadsword's cadence is slow
  // (a hit every few seconds), so an 8 s damage total held only ~1 swing and was pure rounding noise. 36 s holds enough hits to compare.
  const WINDOW_MS = 36000;
  const hits = async () => {
    await c(`/data modify entity @e[tag=f1,limit=1] Health set value ${BIG}.0f`, 300);
    let prev = await hp(), n = 0, dealt = 0; const end = Date.now() + WINDOW_MS;
    while (Date.now() < end) { const h = await hp(); if (h === null || prev === null) continue; if (prev - h > 1.0) { n++; dealt += prev - h; } prev = h; }
    return { n, dealt };
  };
  const before = await hits();
  await c('/emberfall relic give EmberTester wizards_cowl 3', 500);
  const during = await hits();
  for (let i = 0; i < 3; i++) await c('/emberfall relic take EmberTester wizards_cowl', 150);
  const after = await hits();
  const ref = (before.n + after.n) / 2, ratio = during.n / ref;
  console.log(`     hits in ${WINDOW_MS / 1000} s: before ${before.n} (${before.dealt.toFixed(0)} dmg) | cowl x5 ${during.n} (${during.dealt.toFixed(0)} dmg) | after ${after.n} (${after.dealt.toFixed(0)} dmg) | ratio ${ratio.toFixed(2)}`);
  // The relic's real cap is 3 stacks (RelicPool), so delay x0.7 and attack speed x1/0.7 = x1.43. Attack speed is exact (probe: 1.6 -> 2.286).
  check("Wizard's Cowl x3 (its max; -30% delay) gives x1.43 swings (band 1.3-1.56)", ratio >= 1.3 && ratio <= 1.56, `x${ratio.toFixed(2)}`);
  check('removed: back to baseline (within 25%)', Math.abs(after.n / before.n - 1) < 0.25, `${before.n} -> ${after.n} hits`);
  console.log(fails ? `RESULT: ${fails} FAILED` : 'RESULT: ALL PASSED');
  clearInterval(purge); await c('/kill @e[tag=keep]', 300); await c('/expedition leave', 800); bot.quit(); setTimeout(() => process.exit(fails ? 1 : 0), 500);
});
