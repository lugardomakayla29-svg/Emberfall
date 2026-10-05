// Quiver of Plenty on the Spectral Sickles. The orbit is measured by its blade count, which is deterministic: every blade draws a head and
// a tail every tick, so world_particles packets per tick rise with blades. (A damage reading against fixed foes swings 123..610 with NO relic at
// all, measured by the control, because orbiting blades drift in and out of reach, so it cannot judge a relic.) Nobody to fight, no ultimate.
const mineflayer = require('mineflayer');
const bot = mineflayer.createBot({ host: '127.0.0.1', port: 25565, username: 'EmberTester', version: '1.21.11', auth: 'offline' });
const sleep = ms => new Promise(r => setTimeout(r, ms));
const lines = []; bot.on('message', m => lines.push(m.toString()));
let fails = 0; const check = (l, ok, e = '') => { console.log((ok ? 'PASS ' : 'FAIL ') + l + (e ? '  ' + e : '')); if (!ok) fails++; };
let count = 0; bot._client.on('world_particles', () => { count++; });
bot.once('spawn', async () => {
  await sleep(6000);
  const c = async (x, w = 600) => { bot.chat(x); await sleep(w); };
  const ask = async (x, w = 600) => { const n = lines.length; bot.chat(x); await sleep(w); return lines.slice(n).join(' | '); };
  await c('/gamemode survival'); await c('/effect clear @s', 200); await c('/character select reaper', 500);
  await c('/expedition leave', 900); await c('/expedition', 4000); for (let i = 0; i < 30; i++) { await sleep(1500); if (/expedition/.test(await ask('/data get entity @s Dimension', 400))) break; } await sleep(1500);
  await c('/effect give @s minecraft:resistance 999 4 true', 200); await c('/emberfall wavestop 0', 300);
  await c('/kill @e[type=!player,type=!minecraft:item_display,type=!minecraft:interaction]', 900);
  const bx = bot.entity.position.x, by = bot.entity.position.y, bz = bot.entity.position.z;
  const pin = setInterval(() => bot.chat(`/tp @s ${bx.toFixed(2)} ${by.toFixed(2)} ${bz.toFixed(2)} 0 0`), 700);
  const purge = setInterval(() => bot.chat('/kill @e[type=!player,type=!minecraft:item_display,type=!minecraft:interaction]'), 1500);
  // Particles per server tick over 6 s (120 ticks): the mean is stable because the ring is drawn every tick.
  const rate = async () => { await sleep(1500); count = 0; const t0 = Date.now(); await sleep(6000); return count / ((Date.now() - t0) / 50); };
  const med = async () => { const v = []; for (let i = 0; i < 3; i++) v.push(await rate()); v.sort((a, b) => a - b); console.log('     samples', v.map(x => x.toFixed(1)).join(' ')); return v[1]; };
  const none = await med();
  await c('/emberfall relic give EmberTester quiver_of_plenty 3', 500);
  const three = await med();
  for (let i = 0; i < 3; i++) await c('/emberfall relic take EmberTester quiver_of_plenty', 150);
  const gone = await med();
  console.log(`     particles/tick: none ${none.toFixed(1)} | quiver x3 ${three.toFixed(1)} | gone ${gone.toFixed(1)}`);
  check('Quiver x3 (+3 blades) raises the ring clearly (>= 1.3x)', three >= none * 1.3, `x${(three / none).toFixed(2)}`);
  check('removed: ring back to baseline (within 15%)', Math.abs(gone / none - 1) < 0.15, `${none.toFixed(1)} -> ${gone.toFixed(1)}`);
  check('stays inside the 40 per tick budget (SickleSystem.RING_PARTICLE_BUDGET) with the Quiver', three <= 40, `${three.toFixed(1)}`);
  console.log(fails ? `RESULT: ${fails} FAILED` : 'RESULT: ALL PASSED');
  clearInterval(pin); clearInterval(purge); await c('/expedition leave', 800); bot.quit(); setTimeout(() => process.exit(fails ? 1 : 0), 500);
});
