// Hourglass: below half health, foes move at ~half speed. A real chasing zombie (AI on) starts 14 blocks away; we measure how far it
// closes in 3 s in four conditions, holding health fixed each time with /attribute + /data so the only difference is the relic and the health.
const mineflayer = require('mineflayer');
const bot = mineflayer.createBot({ host: '127.0.0.1', port: 25565, username: 'EmberTester', version: '1.21.11', auth: 'offline' });
const sleep = ms => new Promise(r => setTimeout(r, ms));
const lines = []; bot.on('message', m => lines.push(m.toString()));
let fails = 0; const check = (l, ok, e = '') => { console.log((ok ? 'PASS ' : 'FAIL ') + l + (e ? '  ' + e : '')); if (!ok) fails++; };
bot.once('spawn', async () => {
  await sleep(6000);
  const c = async (x, w = 600) => { bot.chat(x); await sleep(w); };
  const ask = async (x, w = 600) => { const n = lines.length; bot.chat(x); await sleep(w); return lines.slice(n).join(' | '); };
  const pos = async () => { for (let k = 0; k < 4; k++) { const t = await ask('/data get entity @e[tag=hz,limit=1] Pos', 350 + k * 150); const m = t.match(/\[(-?[\d.]+)d, (-?[\d.]+)d, (-?[\d.]+)d\]/); if (m) return [+m[1], +m[2], +m[3]]; } return null; };
  await c('/gamemode survival'); await c('/character select vanguard', 500);
  await c('/expedition leave', 900); await c('/expedition', 4000); for (let i = 0; i < 30; i++) { await sleep(1500); if (/expedition/.test(await ask('/data get entity @s Dimension', 400))) break; } await sleep(2000);
  await c('/emberfall wavestop 0', 300); const bx = bot.entity.position.x, by = bot.entity.position.y, bz = bot.entity.position.z;
  const pin = setInterval(() => bot.chat(`/tp @s ${bx.toFixed(2)} ${by.toFixed(2)} ${bz.toFixed(2)} 0 0`), 500);
  // Vanilla ignores /data writes to a player's Health (probed: six writes left 23.5), so health is set with /damage from a known full state.
  // Resistance is cleared, and the player is topped up with /effect instant_health before each run. Max health is read live, not assumed.
  await c('/effect clear @s', 200);
  const maxHp = parseFloat((await ask('/attribute @s minecraft:max_health get', 500)).match(/is ([\d.]+)/)?.[1]);
  console.log('     max health', maxHp);
  const setHp = async frac => {
    await c('/effect give @s minecraft:instant_health 1 10 true', 250); await c('/effect give @s minecraft:instant_health 1 10 true', 250);
    const dmg = maxHp * (1 - frac);
    if (dmg > 0.01) await c(`/damage @s ${dmg.toFixed(2)} minecraft:generic`, 250);
  };
  const runOnce = async (label, frac) => {
    await c('/kill @e[tag=hz]', 300);
    await setHp(frac);
    await c(`/execute at @s run summon emberfall:horde_zombie ~0 ~ ~14 {Tags:["hz"],PersistenceRequired:1b}`, 400);
    await c('/attribute @e[tag=hz,limit=1] minecraft:attack_damage base set 0', 100);
    await c('/effect give @e[tag=hz,limit=1] minecraft:resistance 999 9 true', 100);
    await setHp(frac); await sleep(1200);                       // sweep sees the hourglass state and the zombie starts walking
    const p0 = await pos(); await setHp(frac); await sleep(3000); const p1 = await pos();
    if (!p0 || !p1) { console.log('     ' + label + ' unreadable'); return NaN; }
    const d = Math.hypot(p1[0] - p0[0], p1[2] - p0[2]);
    console.log(`     ${label}: health ${(frac * 100).toFixed(0)}% -> zombie moved ${d.toFixed(2)} blocks in 3 s`);
    return d;
  };
  const full = async () => (await runOnce('no relic, full health ', 1.0) + await runOnce('no relic, full health ', 1.0)) / 2;
  const base = await full();
  await c('/emberfall relic give EmberTester hourglass 1', 500);
  const relicFull = await runOnce('hourglass, FULL health', 1.0);
  const relicLow = await runOnce('hourglass, 30% health', 0.30);
  const relicEdge = await runOnce('hourglass, 55% health ', 0.55);
  await c('/emberfall relic take EmberTester hourglass', 400);
  const noRelicLow = await runOnce('no relic, 30% health  ', 0.30);
  console.log(`     ratio hourglass low/base ${(relicLow / base).toFixed(2)} | full ${(relicFull / base).toFixed(2)} | edge ${(relicEdge / base).toFixed(2)} | no relic low ${(noRelicLow / base).toFixed(2)}`);
  check('baseline zombie actually walks (>= 2 blocks in 3 s)', base >= 2.0, base.toFixed(2));
  check('Hourglass at 30% health slows to about 0.55 (band 0.4-0.7)', relicLow / base >= 0.4 && relicLow / base <= 0.7, (relicLow / base).toFixed(2));
  check('Hourglass at FULL health does nothing (>= 0.85)', relicFull / base >= 0.85, (relicFull / base).toFixed(2));
  check('above half (55%) does nothing (>= 0.85)', relicEdge / base >= 0.85, (relicEdge / base).toFixed(2));
  check('without the relic low health does nothing (>= 0.85)', noRelicLow / base >= 0.85, (noRelicLow / base).toFixed(2));
  console.log(fails ? `RESULT: ${fails} FAILED` : 'RESULT: ALL PASSED');
  clearInterval(pin); await c('/kill @e[tag=hz]', 300); await c('/expedition leave', 800); bot.quit(); setTimeout(() => process.exit(fails ? 1 : 0), 500);
});
