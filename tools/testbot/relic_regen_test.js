// Campfire Core (heals only while standing still) and Dragon's Heart (heals always), measured on a wounded player with NO other healing.
const mineflayer = require('mineflayer');
const bot = mineflayer.createBot({ host: '127.0.0.1', port: 25565, username: 'EmberTester', version: '1.21.11', auth: 'offline' });
const sleep = ms => new Promise(r => setTimeout(r, ms));
const lines = []; bot.on('message', m => lines.push(m.toString()));
let fails = 0; const check = (l, ok, e = '') => { console.log((ok ? 'PASS ' : 'FAIL ') + l + (e ? '  ' + e : '')); if (!ok) fails++; };
bot.once('spawn', async () => {
  await sleep(6000);
  const c = async (x, w = 600) => { bot.chat(x); await sleep(w); };
  const ask = async (x, w = 600) => { const n = lines.length; bot.chat(x); await sleep(w); return lines.slice(n).join(' | '); };
  const H = async () => Number((await ask('/data get entity @s Health', 500)).match(/entity data: (-?[\d.]+)f/)?.[1]);
  const startRun = async () => { await c('/expedition leave', 900); await c('/expedition', 4000); for (let i = 0; i < 30; i++) { await sleep(1500); if (/expedition/.test(await ask('/data get entity @s Dimension', 400))) break; } await sleep(1500); };
  await c('/gamemode survival'); await c('/character select vanguard');
  await startRun();
  const purge = setInterval(() => bot.chat('/kill @e[type=!player,type=!item]'), 1500);
  await c('/effect clear @s', 300);
  const give = async (id, n = 1) => c(`/emberfall relic give EmberTester ${id} ${n}`, 400);
  const take = async (id, n) => { for (let i = 0; i < n; i++) await c(`/emberfall relic take EmberTester ${id}`, 150); };
  const wound = async () => { await c('/effect give EmberTester minecraft:instant_health 1 10 true', 500); await c('/damage @s 12 minecraft:generic', 700); };
  const SECS = 8;
  // A: no relic, standing still
  await wound(); const a0 = await H(); await sleep(SECS * 1000); const a1 = await H();
  // B: campfire x2 (1.0/s once still 1.5 s), standing still
  await give('campfire_core', 2); await wound(); const b0 = await H(); await sleep(SECS * 1000); const b1 = await H();
  // C: campfire x2 while WALKING the whole window
  await wound(); const c0 = await H();
  bot.setControlState('forward', true); bot.setControlState('sprint', true);
  const walk = setInterval(() => { bot.look(bot.entity.yaw + 0.9, 0, true); }, 400);
  await sleep(SECS * 1000);
  clearInterval(walk); bot.clearControlStates(); await sleep(300); const c1 = await H();
  await take('campfire_core', 2);
  // D: dragon's heart (0.5/s, also +40 max hp), walking. Same pattern the standalone probe proved: settle, wound, read, walk, read.
  await c('/effect clear @s', 300);
  await give('dragons_heart', 1); await sleep(2500);
  await wound(); await sleep(600);
  const mx = async () => (await ask('/attribute @s minecraft:max_health get', 600)).match(/value[^\d-]*(-?[\d.]+)/)?.[1];
  console.log(`     heart state before walk: hp ${await H()} max ${await mx()}  | relics: ${(await ask('/emberfall relic state EmberTester', 700)).slice(0, 90)}`);
  const d0 = await H();
  bot.setControlState('forward', true); const walk2 = setInterval(() => { bot.look(bot.entity.yaw + 0.9, 0, true); }, 400);
  await sleep(6000); clearInterval(walk2); bot.clearControlStates(); await sleep(400); const d1 = await H();
  console.log(`     heart raw: ${d0} -> ${d1}`);
  await take('dragons_heart', 1); await sleep(2500);
  // E: control again AFTER removing everything: the same still window with no relic
  await wound(); const e0 = await H(); await sleep(SECS * 1000); const e1 = await H();
  console.log(`     ${SECS}s gain: none ${(a1 - a0).toFixed(1)} | campfire still ${(b1 - b0).toFixed(1)} | campfire walking ${(c1 - c0).toFixed(1)} | heart walking ${(d1 - d0).toFixed(1)} | none again ${(e1 - e0).toFixed(1)}`);
  check('no relic (start AND end of the test): recovery stays under 3 (the relics give 6.5+)', Math.abs(a1 - a0) < 3 && Math.abs(e1 - e0) < 3, `${(a1 - a0).toFixed(1)} / ${(e1 - e0).toFixed(1)}`);
  check('Campfire Core x2 heals while standing still (expect ~6.5 = 1.0/s after 1.5 s)', b1 - b0 > 4.0, `${(b1 - b0).toFixed(1)}`);
  check('Campfire Core heals NOTHING while walking', Math.abs(c1 - c0) < 1.0, `${(c1 - c0).toFixed(1)}`);
  check("Dragon's Heart heals while walking (expect ~3 in 6 s = 0.5/s)", d1 - d0 > 1.5, `${(d1 - d0).toFixed(1)}`);
  console.log(fails ? `RESULT: ${fails} FAILED` : 'RESULT: ALL PASSED');
  clearInterval(purge); await c('/expedition leave', 800); bot.quit(); setTimeout(() => process.exit(fails ? 1 : 0), 500);
});
