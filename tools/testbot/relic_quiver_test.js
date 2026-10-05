// Quiver of Plenty: +1 strike per stack on volley weapons. The Ranger's Hunting Bow fires extra arrows at OTHER foes, so with a group of
// 9 pinned foes the number of foes damaged per window and the total damage both rise. Each phase starts from the same full-health foes.
const mineflayer = require('mineflayer');
const bot = mineflayer.createBot({ host: '127.0.0.1', port: 25565, username: 'EmberTester', version: '1.21.11', auth: 'offline' });
const sleep = ms => new Promise(r => setTimeout(r, ms));
const lines = []; bot.on('message', m => lines.push(m.toString()));
let fails = 0; const check = (l, ok, e = '') => { console.log((ok ? 'PASS ' : 'FAIL ') + l + (e ? '  ' + e : '')); if (!ok) fails++; };
const BIG = 1000000, WINDOW = 12000;
bot.once('spawn', async () => {
  await sleep(6000);
  const c = async (x, w = 600) => { bot.chat(x); await sleep(w); };
  const ask = async (x, w = 600) => { const n = lines.length; bot.chat(x); await sleep(w); return lines.slice(n).join(' | '); };
  const hp = async tag => { for (let k = 0; k < 4; k++) { const t = await ask(`/data get entity @e[tag=${tag},limit=1] Health`, 350 + k * 200); const m = t.match(/entity data: (-?[\d.]+)f/); if (m) return parseFloat(m[1]); } return null; };
  await c('/gamemode survival'); await c('/effect clear @s', 200); const CHAR = process.env.Q_CHAR || 'ranger'; await c(`/character select ${CHAR}`, 500);
  await c('/expedition leave', 900); await c('/expedition', 4000); for (let i = 0; i < 30; i++) { await sleep(1500); if (/expedition/.test(await ask('/data get entity @s Dimension', 400))) break; } await sleep(1500);
  await c('/effect give @s minecraft:resistance 999 4 true', 200); await c('/effect give @s minecraft:regeneration 999 4 true', 200);
  await c('/emberfall wavestop 0', 300); await c('/time set midnight', 200);
  await c('/kill @e[type=!player,type=!minecraft:item_display,type=!minecraft:interaction]', 900);
  const bx = bot.entity.position.x, by = bot.entity.position.y, bz = bot.entity.position.z;
  const pin = setInterval(() => bot.chat(`/tp @s ${bx.toFixed(2)} ${by.toFixed(2)} ${bz.toFixed(2)} 0 0`), 700);
  console.log('LOADOUT', (await ask('/emberfall debugloadout EmberTester', 700)).slice(0, 140));
  const nine = [[-2.2, 2.4], [-1.1, 2.2], [0, 2.0], [1.1, 2.3], [2.2, 2.5], [-1.6, 3.5], [-0.5, 3.4], [0.7, 3.6], [1.8, 3.3]];
  const tags = nine.map((_, i) => 'n' + i);
  for (let i = 0; i < nine.length; i++) {
    await c(`/execute at @s run summon emberfall:horde_zombie ~${nine[i][0]} ~ ~${nine[i][1]} {Tags:["${tags[i]}","keep"],NoAI:1b,Silent:1b,PersistenceRequired:1b}`, 300);
    await c(`/attribute @e[tag=${tags[i]},limit=1] minecraft:max_health base set ${BIG}`, 120);
  }
  const refill = async () => { for (const t of tags) await c(`/data modify entity @e[tag=${t},limit=1] Health set value ${BIG}.0f`, 110); };
  // One window: total damage and how many of the 9 foes were hurt at all.
  const window = async () => {
    await refill(); const a = []; for (const t of tags) a.push(await hp(t));
    await sleep(WINDOW);
    let total = 0, hurt = 0, bad = 0; for (let i = 0; i < tags.length; i++) { const b = await hp(tags[i]); if (a[i] === null || b === null) { bad++; continue; } const d = a[i] - b; total += d; if (d > 0.5) hurt++; }
    return { total, hurt, bad };
  };
  const give = n => c(`/emberfall relic give EmberTester quiver_of_plenty ${n}`, 500);
  const take = async n => { for (let i = 0; i < n; i++) await c('/emberfall relic take EmberTester quiver_of_plenty', 150); };
  const run = async label => { const r = []; for (let i = 0; i < 2; i++) r.push(await window()); const m = k => r.reduce((x, y) => x + y[k], 0) / r.length; console.log(`     ${label}: total ${r.map(x => x.total.toFixed(0))}  hurt ${r.map(x => x.hurt)}  unreadable ${r.map(x => x.bad)}`); return { total: m('total'), hurt: m('hurt') }; };
  if (process.env.Q_CONTROL) { for (let k = 0; k < 4; k++) await run('CONTROL win ' + (k + 1)); console.log('CONTROL done'); clearInterval(pin); await c('/kill @e[tag=keep]', 300); await c('/expedition leave', 800); bot.quit(); setTimeout(() => process.exit(0), 500); return; }
  const before = await run('no quiver   ');
  await give(3); const during = await run('quiver x3    '); await take(3);
  const after = await run('quiver gone  ');
  const ref = (before.total + after.total) / 2;
  check('Quiver of Plenty x3 (+3 strikes) raises total damage clearly (>= 1.25x) [' + CHAR + ']', during.total >= ref * 1.25, `${during.total.toFixed(0)} vs baseline ${ref.toFixed(0)} = x${(during.total / ref).toFixed(2)}`);
  check('Quiver x3 hurts at least as many foes as baseline [' + CHAR + ']', during.hurt >= Math.max(before.hurt, after.hurt), `${during.hurt} vs ${before.hurt}/${after.hurt}`);
  check('removed: back to baseline (within 30%)', Math.abs(after.total / before.total - 1) < 0.3, `${before.total.toFixed(0)} -> ${after.total.toFixed(0)}`);
  console.log(fails ? `RESULT: ${fails} FAILED` : 'RESULT: ALL PASSED');
  clearInterval(pin); await c('/kill @e[tag=keep]', 300); await c('/expedition leave', 800); bot.quit(); setTimeout(() => process.exit(fails ? 1 : 0), 500);
});
