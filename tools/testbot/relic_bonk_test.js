// Big Bonk Hammer x3: 6% of hits deal x20. Watch the foe's health every 0.5 s; a proc is a single step of ~20x a normal hit.
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
  const hp = async () => { for (let k = 0; k < 4; k++) { const t = await ask('/data get entity @e[tag=f1,limit=1] Health', 350 + k * 150); const m = t.match(/entity data: (-?[\d.]+)f/); if (m) return parseFloat(m[1]); } return null; };
  const startRun = async () => { await c('/expedition leave', 900); await c('/expedition', 4000); for (let i = 0; i < 30; i++) { await sleep(1500); if (/expedition/.test(await ask('/data get entity @s Dimension', 400))) break; } await sleep(1500); };
  await c('/gamemode survival'); await c('/character select vanguard');
  await startRun();
  await c('/effect give EmberTester minecraft:resistance 999 4 true', 300); await c('/effect give EmberTester minecraft:regeneration 999 4 true', 300);
  const purge = setInterval(() => bot.chat('/kill @e[type=!player,type=!item,tag=!keep]'), 1500);
  await sleep(2500);
  await c(`/execute at @s run summon emberfall:horde_zombie ~2 ~ ~0 {Tags:["f1","keep"],NoAI:1b,Silent:1b,PersistenceRequired:1b}`, 500);
  await c(`/attribute @e[tag=f1,limit=1] minecraft:max_health base set ${BIG}`, 200);
  // Sample the per-step loss for ~ 60 s. "big" = a step of at least 8x the median normal step.
  const sample = async (seconds) => {
    const steps = []; let prev = null;
    await c(`/data modify entity @e[tag=f1,limit=1] Health set value ${BIG}.0f`, 250);
    prev = await hp(); const end = Date.now() + seconds * 1000;
    while (Date.now() < end) { const h = await hp(); if (h === null || prev === null) continue; const d = prev - h; if (d > 0.5) steps.push(d); prev = h; if (h < BIG * 0.9) { await c(`/data modify entity @e[tag=f1,limit=1] Health set value ${BIG}.0f`, 200); prev = await hp(); } }
    return steps;
  };
  const med = a => a.length ? [...a].sort((x, y) => x - y)[Math.floor(a.length / 2)] : NaN;
  const base = await sample(40);
  await c('/emberfall relic give EmberTester big_bonk 3', 400);
  const bonk = await sample(60);
  await c('/emberfall relic take EmberTester big_bonk', 150); await c('/emberfall relic take EmberTester big_bonk', 150); await c('/emberfall relic take EmberTester big_bonk', 150);
  const m0 = med(base), bigBase = base.filter(d => d >= m0 * 8).length, bigBonk = bonk.filter(d => d >= m0 * 8).length;
  console.log(`     steps: baseline ${base.length} (median ${m0.toFixed(1)}, max ${Math.max(0, ...base).toFixed(0)}) | with x3 ${bonk.length} (max ${Math.max(0, ...bonk).toFixed(0)})  big steps: ${bigBase} vs ${bigBonk}`);
  check('baseline has no huge hits (control is honest)', bigBase === 0, `${bigBase}`);
  check('Big Bonk x3 produces at least one huge hit (>= 8x a normal step) in 60 s', bigBonk >= 1, `${bigBonk}`);
  console.log(fails ? `RESULT: ${fails} FAILED` : 'RESULT: ALL PASSED');
  clearInterval(purge); await c('/kill @e[tag=keep]', 300); await c('/expedition leave', 800); bot.quit(); setTimeout(() => process.exit(fails ? 1 : 0), 500);
});
