// Offence relics through the REAL weapon path. The foe is a NoAI 1,000,000 hp zombie, so damage dealt is pure weapon output.
// Each relic is measured in a PAIR: baseline (no relic) immediately before it and immediately after it is removed again.
const mineflayer = require('mineflayer');
const bot = mineflayer.createBot({ host: '127.0.0.1', port: 25565, username: 'EmberTester', version: '1.21.11', auth: 'offline' });
const sleep = ms => new Promise(r => setTimeout(r, ms));
const lines = []; bot.on('message', m => lines.push(m.toString()));
let fails = 0; const check = (l, ok, e = '') => { console.log((ok ? 'PASS ' : 'FAIL ') + l + (e ? '  ' + e : '')); if (!ok) fails++; };
const BIG = 1000000, WINDOW = +(process.env.REL_WINDOW || 8000);
bot.once('spawn', async () => {
  await sleep(6000);
  const c = async (x, w = 600) => { bot.chat(x); await sleep(w); };
  const ask = async (x, w = 600) => { const n = lines.length; bot.chat(x); await sleep(w); return lines.slice(n).join(' | '); };
  const hp = async tag => { for (let k = 0; k < 4; k++) { const t = await ask(`/data get entity @e[tag=${tag},limit=1] Health`, 450 + k * 200); const m = t.match(/entity data: (-?[\d.]+)f/); if (m) return parseFloat(m[1]); } return null; };
  const startRun = async () => { await c('/expedition leave', 900); await c('/expedition', 4000); for (let i = 0; i < 30; i++) { await sleep(1500); if (/expedition/.test(await ask('/data get entity @s Dimension', 400))) break; } await sleep(1500); };
  await c('/gamemode survival'); await c('/character select vanguard'); // broadsword: a steady melee weapon
  await startRun();
  await c('/effect give EmberTester minecraft:resistance 999 4 true', 300); await c('/effect give EmberTester minecraft:regeneration 999 4 true', 300);
  const purge = setInterval(() => bot.chat('/kill @e[type=!player,type=!item,tag=!keep]'), 1500);
  await sleep(2500);
  await c(`/execute at @s run summon emberfall:horde_zombie ~2 ~ ~0 {Tags:["f1","keep"],NoAI:1b,Silent:1b,PersistenceRequired:1b}`, 500);
  await c(`/attribute @e[tag=f1,limit=1] minecraft:max_health base set ${BIG}`, 200);
  // Damage dealt to the foe over one window. The foe is refilled first so the reading never approaches a floor.
  const dmg = async () => {
    await c(`/data modify entity @e[tag=f1,limit=1] Health set value ${BIG}.0f`, 300);
    const a = await hp('f1'); await sleep(WINDOW); const b = await hp('f1');
    return (a === null || b === null) ? NaN : a - b;
  };
  const mean = async (n) => { const v = []; for (let i = 0; i < n; i++) v.push(await dmg()); return { v, m: v.reduce((x, y) => x + y, 0) / v.length }; };
  const give = async (id, n) => c(`/emberfall relic give EmberTester ${id} ${n}`, 400);
  const take = async (id, n) => { for (let i = 0; i < n; i++) await c(`/emberfall relic take EmberTester ${id}`, 150); };
  const fmt = r => `${r.v.map(x => x.toFixed(0)).join(', ')} (mean ${r.m.toFixed(1)})`;
  const results = {};
  for (const [id, n, lo, hi, label] of [['anvil_of_dawn', 1, 1.2, 1.3, 'Anvil of Dawn (cap 1 stack) = +25% damage'], ['wither_crown', 1, 1.42, 1.58, 'Wither Crown = +50% damage']]) {
    const before = await mean(3);
    await give(id, n); const during = await mean(3); await take(id, n);
    const after = await mean(3);
    const med = a => [...a].sort((x, y) => x - y)[Math.floor(a.length / 2)]; const ref = (med(before.v) + med(after.v)) / 2, ratio = med(during.v) / ref;
    console.log(`     ${id}: before ${fmt(before)} | with ${fmt(during)} | after ${fmt(after)} | ratio ${ratio.toFixed(2)}`);
    check(`${label} (expected x${((lo + hi) / 2).toFixed(1)}, got x${ratio.toFixed(2)})`, Number.isFinite(ratio) && ratio >= lo && ratio <= hi, `window ${lo}-${hi}`);
    check(`${id}: removing it returns damage to baseline (within 25%)`, Number.isFinite(after.m) && Math.abs(after.m / before.m - 1) < 0.25, `${before.m.toFixed(1)} -> ${after.m.toFixed(1)}`);
  }
  console.log(fails ? `RESULT: ${fails} FAILED` : 'RESULT: ALL PASSED');
  clearInterval(purge); await c('/kill @e[tag=keep]', 300); await c('/expedition leave', 800); bot.quit(); setTimeout(() => process.exit(fails ? 1 : 0), 500);
});
