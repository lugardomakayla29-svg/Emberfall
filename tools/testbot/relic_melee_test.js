// Thorn Vest against a REAL melee attacker. A zombie is pinned beside the player; it hits, we read ITS health.
const mineflayer = require('mineflayer');
const bot = mineflayer.createBot({ host: '127.0.0.1', port: 25565, username: 'EmberTester', version: '1.21.11', auth: 'offline' });
const sleep = ms => new Promise(r => setTimeout(r, ms));
const lines = []; bot.on('message', m => lines.push(m.toString()));
let fails = 0; const check = (l, ok, e = '') => { console.log((ok ? 'PASS ' : 'FAIL ') + l + (e ? '  ' + e : '')); if (!ok) fails++; };
bot.once('spawn', async () => {
  await sleep(6000);
  const c = async (x, w = 700) => { bot.chat(x); await sleep(w); };
  const ask = async (x, w = 700) => { const n = lines.length; bot.chat(x); await sleep(w); return lines.slice(n).join(' | '); };
  const startRun = async () => { await c('/expedition leave', 900); await c('/expedition', 4000); for (let i = 0; i < 30; i++) { await sleep(1500); if (/expedition/.test(await ask('/data get entity @s Dimension', 400))) break; } await sleep(1500); };
  await c('/gamemode survival'); await c('/character select juggernaut');
  await startRun();
  await c('/effect give EmberTester minecraft:regeneration 999 4 true', 300);
  const purge = setInterval(() => bot.chat('/kill @e[type=!player,type=!item,tag=!kt]'), 1500);
  await sleep(2500);
  // Parse ONLY the "entity data: <n>f" reply of the health query. Any other chat line (action bar, XP) is ignored.
  const zHealth = async () => {
    for (let k = 0; k < 3; k++) {
      const t = await ask('/data get entity @e[tag=kt,limit=1] Health', 600);
      const m = t.match(/entity data: (-?[\d.]+)f/);
      if (m) return parseFloat(m[1]);
    }
    return NaN;
  };
  // One trial: pin a zombie beside the player for ~5 s, report how much health IT lost.
  const trial = async (label) => {
    await c('/kill @e[tag=kt]', 200);
    await c('/execute at @s run summon emberfall:horde_zombie ~1 ~ ~0 {Tags:["kt"],PersistenceRequired:1b}', 700);
    await c('/attribute @e[tag=kt,limit=1] minecraft:max_health base set 400', 200);
    await c('/data merge entity @e[tag=kt,limit=1] {Health:400f}', 200);
    await c('/attribute @e[tag=kt,limit=1] minecraft:movement_speed base set 0', 200);
    const hold = setInterval(() => bot.chat('/execute at @s run tp @e[tag=kt,limit=1] ~1 ~ ~0'), 400);
    for (let w = 0; w < 10; w++) { if (/Count: [1-9]/.test(await ask('/execute if entity @e[tag=kt]', 400))) break; }
    const h0 = await zHealth();
    await sleep(5000);
    const h1 = await zHealth();
    clearInterval(hold);
    console.log(`     ${label}: zombie health ${h0} -> ${h1}  (lost ${(h0 - h1).toFixed(1)})`);
    return h0 - h1;
  };
  const R = 3;
  const base = [], thorn = [];
  for (let i = 0; i < R; i++) base.push(await trial('no relic   #' + (i + 1)));
  await c('/emberfall relic give EmberTester thorn_vest 5', 500); // 10 back per landed hit
  for (let i = 0; i < R; i++) thorn.push(await trial('thorn x5    #' + (i + 1)));
  for (let i = 0; i < 5; i++) await c('/emberfall relic take EmberTester thorn_vest', 100);
  const after = []; for (let i = 0; i < R; i++) after.push(await trial('relic gone  #' + (i + 1)));
  const mean = a => a.reduce((x, y) => x + y, 0) / a.length;
  const okAll = a => a.every(v => Number.isFinite(v));
  clearInterval(purge);
  check('every reading is a real number', okAll(base) && okAll(thorn) && okAll(after), JSON.stringify({ base, thorn, after }));
  const mb = mean(base), mt = mean(thorn), ma = mean(after);
  console.log(`     mean loss: no relic ${mb.toFixed(1)} | thorn x5 ${mt.toFixed(1)} | relic gone ${ma.toFixed(1)}`);
  check('Thorn Vest x5 makes the attacker lose clearly MORE health than without it (>= +8)', mt - mb >= 8, `+${(mt - mb).toFixed(1)}`);
  check('after removing the relic the loss falls back to the baseline (within 6 of no-relic)', Math.abs(ma - mb) <= 6, `${ma.toFixed(1)} vs ${mb.toFixed(1)}`);
  console.log(fails ? `RESULT: ${fails} FAILED` : 'RESULT: ALL PASSED');
  await c('/expedition leave', 800); bot.quit(); setTimeout(() => process.exit(fails ? 1 : 0), 500);
});
