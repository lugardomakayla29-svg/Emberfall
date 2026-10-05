// Golden Nugget / Clockwork Charm through the REAL kill path: same kills with and without the relics.
const mineflayer = require('mineflayer');
const bot = mineflayer.createBot({ host: '127.0.0.1', port: 25565, username: 'EmberTester', version: '1.21.11', auth: 'offline' });
const sleep = ms => new Promise(r => setTimeout(r, ms));
const lines = []; bot.on('message', m => lines.push(m.toString()));
let fails = 0; const check = (l, ok, e = '') => { console.log((ok ? 'PASS ' : 'FAIL ') + l + (e ? '  ' + e : '')); if (!ok) fails++; };
bot.once('spawn', async () => {
  await sleep(6000);
  const c = async (x, w = 700) => { bot.chat(x); await sleep(w); };
  const ask = async (x, w = 700) => { const n = lines.length; bot.chat(x); await sleep(w); return lines.slice(n).join(' | '); };
  const gold = async () => Number((await ask('/emberfall debuggold EmberTester')).match(/Gold: (\d+)/)?.[1] ?? NaN);
  const xpOf = async () => Number((await ask('/data get entity @s XpTotal', 500)).match(/(-?\d+)\D*$/)?.[1] ?? NaN);
  await c('/gamemode survival'); await c('/effect give EmberTester minecraft:resistance 999 4 true'); await c('/effect give EmberTester minecraft:regeneration 999 4 true');
  await c('/character select juggernaut'); await c('/expedition leave', 800); await c('/expedition', 4000); await sleep(1500);
  const purge = setInterval(() => bot.chat('/kill @e[type=!player,type=!item,tag=!kt]'), 1500);
  await sleep(3000);
  const killOne = async () => {
    await c('/execute at @s run summon emberfall:horde_zombie ~2 ~ ~0 {Tags:["kt"],PersistenceRequired:1b}', 600);
    await c('/attribute @e[tag=kt,limit=1] minecraft:movement_speed base set 0', 300);
    await c('/data merge entity @e[tag=kt,limit=1] {Health:1f}', 300);
    const hold = setInterval(() => bot.chat('/execute at @s run tp @e[tag=kt,limit=1] ~2 ~ ~0'), 600);
    let dead = false;
    for (let i = 0; i < 14 && !dead; i++) { await sleep(700); dead = /Test failed/.test(await ask('/execute if entity @e[tag=kt]', 500)); }
    clearInterval(hold); await sleep(1500); await c('/kill @e[tag=kt]', 300);
    return dead;
  };
  // collect the per-kill "+N XP" / "+N Gold" action-bar lines
  const xpTotal = async () => { const t = await ask('/data get entity @s XpTotal', 500); const m = t.match(/data: (-?\d+)/) || t.match(/(-?\d+)\s*$/); return m ? +m[1] : NaN; };
  const payouts = async (kills) => {
    const xp = [], gd = []; let died = 0;
    for (let k = 0; k < kills; k++) {
      const g0 = await gold(), x0 = await xpTotal();
      const dead = await killOne(); if (dead) died++;
      const g1 = await gold(), x1 = await xpTotal();
      if (dead) { gd.push(g1 - g0); xp.push(x1 - x0); }
    }
    return { xp, gd, died };
  };
  const sum = a => a.reduce((x, y) => x + y, 0);
  const K = 10;
  const base = await payouts(K);
  console.log(`BASE  : ${base.died}/${K} killed, xp ${JSON.stringify(base.xp)} gold ${JSON.stringify(base.gd)}`);
  await c('/emberfall relic give EmberTester gold_nugget 10', 500);
  await c('/emberfall relic give EmberTester clockwork_charm 10', 500);
  const st = await ask('/emberfall relic state EmberTester', 700);
  check('relics are held (gold x2.5, xp x2.0)', /gold=2\.50/.test(st) && /xp=2\.00/.test(st), st.match(/gold=\S+ xp=\S+/)?.[0]);
  const boost = await payouts(K);
  console.log(`RELICS: ${boost.died}/${K} killed, xp ${JSON.stringify(boost.xp)} gold ${JSON.stringify(boost.gd)}`);
  clearInterval(purge);
  check('enough kills landed in both batches to compare', base.died >= 8 && boost.died >= 8, `${base.died} / ${boost.died}`);
  const bx = sum(base.xp) / Math.max(1, base.xp.length), rx = sum(boost.xp) / Math.max(1, boost.xp.length);
  check('XP per kill is ~2x with Clockwork Charm x10', rx / bx > 1.7 && rx / bx < 2.3, `base ${bx.toFixed(2)} -> ${rx.toFixed(2)} (x${(rx / bx).toFixed(2)})`);
  const bg = sum(base.gd), rg = sum(boost.gd);
  check('Gold total rises with Golden Nugget x10 (expected ~2.5x of a 60% coin flip)', rg > bg, `base ${bg} -> ${rg}`);
  check('no payout went negative or NaN', boost.gd.every(v => v >= 0) && boost.xp.every(v => v >= 0), '')
  console.log(fails ? `RESULT: ${fails} FAILED` : 'RESULT: ALL PASSED');
  bot.quit(); setTimeout(() => process.exit(fails ? 1 : 0), 500);
});
