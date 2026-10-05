// Mirror Shard window, deterministic: /damage with known timing. A hit opens a 1 s window (20 ticks) then a 10 s cooldown (200 ticks).
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
  const purge = setInterval(() => bot.chat('/kill @e[type=!player,type=!item,tag=!kt]'), 1500);
  await c('/effect give EmberTester minecraft:regeneration 999 4 true', 300);
  await c('/emberfall relic give EmberTester mirror_shard', 500);
  // One /damage call returns "Applied N damage" when it landed and a failure text when the event vetoed it.
  const hit = async (wait = 750) => { const t = await ask('/damage @s 1 minecraft:mob_attack', wait); return /Applied/.test(t); };
  // Spacing is 0.75 s: BEYOND vanilla's 0.5 s hurt immunity (so vanilla alone would let it land) but INSIDE Mirror's 1.0 s window.
  // After the window the 10 s cooldown is running, but cooldown only stops a NEW window: hits still land normally.
  const t0 = await hit(); const t1 = await hit();
  check('first hit lands (it opens the window)', t0, '');
  check('a hit 0.75 s later (vanilla would allow it) is IGNORED by the window', !t1, `landed=${t1}`);
  await sleep(1300);
  const t2 = await hit();
  check('after the window has closed, hits land again', t2, `landed=${t2}`);
  const t3 = await hit();
  check('during the 10 s cooldown no NEW window opens: a hit 0.75 s later also lands', t3, `landed=${t3}`);
  await sleep(10500);
  const t4 = await hit(); const t5 = await hit();
  check('after the cooldown the window opens again: a hit lands then the quick one is ignored', t4 && !t5, `t4=${t4} t5=${t5}`);
  // Without the relic nothing is ignored.
  await c('/emberfall relic take EmberTester mirror_shard', 300);
  await sleep(1500);
  const u0 = await hit(); const u1 = await hit();
  check('relic removed: two hits 0.75 s apart both land (nothing but the relic was ignoring them)', u0 && u1, `${u0} ${u1}`);
  clearInterval(purge);
  console.log(fails ? `RESULT: ${fails} FAILED` : 'RESULT: ALL PASSED');
  await c('/expedition leave', 800); bot.quit(); setTimeout(() => process.exit(fails ? 1 : 0), 500);
});
