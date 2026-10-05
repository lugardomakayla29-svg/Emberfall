// Mirror Shard: (1) reflect returns half the damage taken to a melee attacker, (2) the invulnerability window ignores hits.
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
  await sleep(2500);
  const num = async (q, re) => { for (let k = 0; k < 3; k++) { const t = await ask(q, 600); const m = t.match(re); if (m) return parseFloat(m[1]); } return NaN; };
  const zHealth = () => num('/data get entity @e[tag=kt,limit=1] Health', /entity data: (-?[\d.]+)f/);
  const pHealth = () => num('/data get entity @s Health', /entity data: (-?[\d.]+)f/);
  // Pin a durable zombie beside the player for `ms`. Counts the "hit" events by reading the player's health steps every 250 ms.
  const trial = async (label, ms) => {
    await c('/kill @e[tag=kt]', 200);
    await c('/effect clear @s', 200); await c('/effect give EmberTester minecraft:instant_health 1 5 true', 300);
    await c('/execute at @s run summon emberfall:horde_zombie ~1 ~ ~0 {Tags:["kt"],PersistenceRequired:1b}', 700);
    await c('/attribute @e[tag=kt,limit=1] minecraft:max_health base set 400', 200);
    await c('/data merge entity @e[tag=kt,limit=1] {Health:400f}', 200);
    await c('/attribute @e[tag=kt,limit=1] minecraft:movement_speed base set 0', 200);
    const hold = setInterval(() => bot.chat('/execute at @s run tp @e[tag=kt,limit=1] ~1 ~ ~0'), 400);
    await c('/effect give EmberTester minecraft:instant_health 1 5 true', 300);
    const z0 = await zHealth(), p0 = await pHealth();
    let hits = 0, last = p0, ignoredWindows = 0;
    const end = Date.now() + ms;
    while (Date.now() < end) { await sleep(120); const h = bot.health; if (h < last - 0.01) hits++; last = h; }
    const z1 = await zHealth(), p1 = await pHealth();
    clearInterval(hold);
    const r = { zLoss: z0 - z1, pLoss: p0 - p1, hits };
    console.log(`     ${label}: zombie lost ${r.zLoss.toFixed(1)}, player lost ${r.pLoss.toFixed(1)}, damage events seen ${r.hits}`);
    return r;
  };
  const base = await trial('no relic   ', 6000);
  await c('/emberfall relic give EmberTester mirror_shard', 500);
  const st = await ask('/emberfall relic state EmberTester', 700);
  check('Mirror Shard is held', /mirror_shard/.test(st), st.slice(0, 100));
  const mir = await trial('mirror_shard', 6000);
  clearInterval(purge);
  check('readings are real numbers', [base.zLoss, mir.zLoss, base.pLoss, mir.pLoss].every(Number.isFinite), '');
  // Reflect: the zombie must lose MORE than the baseline by roughly half of what the player lost.
  const extra = mir.zLoss - base.zLoss;
  check('reflect: the attacker loses extra health with Mirror Shard', extra > 0.5 * mir.pLoss * 0.4, `extra ${extra.toFixed(1)} (player lost ${mir.pLoss.toFixed(1)}; half of it is ${(mir.pLoss / 2).toFixed(1)})`);
  // Invulnerability: fewer damage events reach the player than without the relic (the window ignores hits, then a 10 s cooldown).
  check('invulnerability window: the player took fewer damage events than the baseline', mir.hits < base.hits, `${mir.hits} vs ${base.hits}`);
  console.log(fails ? `RESULT: ${fails} FAILED` : 'RESULT: ALL PASSED');
  await c('/expedition leave', 800); bot.quit(); setTimeout(() => process.exit(fails ? 1 : 0), 500);
});
