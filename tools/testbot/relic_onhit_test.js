// After-hit relics on the REAL weapon path: Blood Chalice (heal), Spiked Censer (blast neighbours), Frostbound Ring (slow), Big Bonk (x20).
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
  await c('/gamemode survival'); await c('/character select vanguard');
  await startRun();
  await c('/effect give EmberTester minecraft:resistance 999 4 true', 300);
  const purge = setInterval(() => bot.chat('/kill @e[type=!player,type=!item,tag=!keep]'), 1500);
  await sleep(2500);
  const foe = async (tag, dx, dz) => {
    await c(`/execute at @s run summon emberfall:horde_zombie ~${dx} ~ ~${dz} {Tags:["${tag}","keep"],NoAI:1b,Silent:1b,PersistenceRequired:1b}`, 450);
    await c(`/attribute @e[tag=${tag},limit=1] minecraft:max_health base set ${BIG}`, 180);
    await c(`/data modify entity @e[tag=${tag},limit=1] Health set value ${BIG}.0f`, 180);
  };
  const refill = async tags => { for (const t of tags) await c(`/data modify entity @e[tag=${t},limit=1] Health set value ${BIG}.0f`, 220); };
  const give = async (id, n = 1) => c(`/emberfall relic give EmberTester ${id} ${n}`, 400);
  const take = async (id, n) => { for (let i = 0; i < n; i++) await c(`/emberfall relic take EmberTester ${id}`, 150); };
  const med = a => [...a].sort((x, y) => x - y)[Math.floor(a.length / 2)];

  // ---- primary foe f1 at 2 blocks, neighbour f2 right beside it ----
  await foe('f1', 2.0, 0); await foe('f2', 2.0, 1.6);
  const neighbourLoss = async () => { await refill(['f1', 'f2']); const a = await hp('f2'); await sleep(WINDOW); const b = await hp('f2'); return (a === null || b === null) ? NaN : a - b; };
  const trio = async fn => { const v = []; for (let i = 0; i < 3; i++) v.push(await fn()); return v; };

  // CENSER: the neighbour f2 must lose more while f1 is being hit
  const cBefore = await trio(neighbourLoss);
  await give('spiked_censer', 3); const cWith = await trio(neighbourLoss); await take('spiked_censer', 3);
  const cAfter = await trio(neighbourLoss);
  console.log(`     censer neighbour loss: before ${cBefore.map(v => v.toFixed(0))} | with x3 ${cWith.map(v => v.toFixed(0))} | after ${cAfter.map(v => v.toFixed(0))}`);
  const cRef = (med(cBefore) + med(cAfter)) / 2;
  check('Spiked Censer x3 (30% procs) makes the NEIGHBOUR lose clearly more', Number.isFinite(cRef) && med(cWith) > cRef * 1.25, `${med(cWith).toFixed(1)} vs baseline ${cRef.toFixed(1)}`);
  check('Censer removed: neighbour loss returns to baseline', Math.abs(med(cAfter) / med(cBefore) - 1) < 0.3, `${med(cBefore).toFixed(1)} -> ${med(cAfter).toFixed(1)}`);

  // BLOOD CHALICE: heal from damage dealt. The player is wounded for real (NO resistance, NO regeneration: a probe showed
  // 10 damage takes 23.0 to 13.5 and nothing recovers on its own), then the weapon hits the pinned foe for a window.
  await c('/effect clear EmberTester', 300);
  const healOnce = async () => {
    await refill(['f1']);
    const h0 = Number((await ask('/data get entity @s Health', 600)).match(/entity data: (-?[\d.]+)f/)?.[1]);
    await sleep(WINDOW);
    const h1 = Number((await ask('/data get entity @s Health', 600)).match(/entity data: (-?[\d.]+)f/)?.[1]);
    return { h0, h1, gain: h1 - h0 };
  };
  await c('/damage @s 10 minecraft:generic', 700);
  const hBefore = await healOnce();
  await c('/damage @s 10 minecraft:generic', 700);
  await give('blood_chalice', 3);
  const hWith = await healOnce(); await take('blood_chalice', 3);
  console.log(`     chalice: without ${hBefore.h0} -> ${hBefore.h1} (gain ${hBefore.gain.toFixed(1)}) | with x3 ${hWith.h0} -> ${hWith.h1} (gain ${hWith.gain.toFixed(1)})`);
  check('no relic: health does not recover on its own (control is honest)', Math.abs(hBefore.gain) < 0.5, `gain ${hBefore.gain.toFixed(1)}`);
  check('Blood Chalice x3 (9% of damage dealt) heals the player', hWith.gain > 2.0, `gain ${hWith.gain.toFixed(1)}`);

  // FROST: f1 is NoAI so slowness is read as an effect, not movement.
  const slowSeen = async () => { await c('/effect clear @e[tag=f1,limit=1]', 200); await sleep(WINDOW); const t = await ask('/data get entity @e[tag=f1,limit=1] active_effects', 600); return /slowness/.test(t); };
  const fNone = await slowSeen();
  await give('frostbound_ring', 3); const fWith = await slowSeen(); await take('frostbound_ring', 3);
  check('without the ring the foe is never slowed', !fNone, '');
  check('Frostbound Ring x3 (30% procs) slows the foe within the window', fWith, '');

  console.log(fails ? `RESULT: ${fails} FAILED` : 'RESULT: ALL PASSED');
  clearInterval(purge); await c('/kill @e[tag=keep]', 300); await c('/expedition leave', 800); bot.quit(); setTimeout(() => process.exit(fails ? 1 : 0), 500);
});
