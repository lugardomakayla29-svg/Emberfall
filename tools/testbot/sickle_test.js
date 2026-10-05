// Spectral Sickles growth. reaper starts with ONLY the sickles. Foes are 1024 hp frozen horde zombies, player pinned.
//   K0  the sickles are the only weapon, slot 0
//   K1  level 1: a foe 3.6 blocks away is beyond the blades (2.2 orbit + 0.9 contact = 3.1) and untouched
//   K2  level 10: the same foe is inside the wider orbit (3.6 + 1.3) and is cut
//   K3  blades: the damage one foe at 2.7 (inside both levels' contact bands) takes in 10 s is clearly higher at level 10 than at level 1
//   K4  Reaper's Rite: a full meter starts it exactly once (ults 0 -> 1) at level 1
//   K5  the meter is consumed by it
//   K6  while it runs, a foe at 3.4 (outside the normal 3.1 reach) is gathered into the pile and cut; before it, it is not
const mineflayer = require('mineflayer');
const bot = mineflayer.createBot({ host: '127.0.0.1', port: 25565, username: 'EmberTester', version: '1.21.11', auth: 'offline' });
const sleep = ms => new Promise(r => setTimeout(r, ms));
const lines = []; bot.on('message', m => lines.push(m.toString())); bot.on('error', e => console.log('ERR', e.message));
const ask = async (x, w = 500) => { const n = lines.length; bot.chat(x); await sleep(w); return lines.slice(n).join(' | '); };
let fails = 0; const R = (n, ok, extra = '') => { console.log(`${ok ? 'PASS' : 'FAIL'} ${n} ${extra}`); if (!ok) fails++; };
const BIG = 1024;
const hp = async tag => { const r = await ask(`/data get entity @e[tag=${tag},limit=1] Health`, 420); const m = /entity data: (-?[\d.]+)f/.exec(r); return m ? +m[1] : null; };
const meter = async () => { const r = await ask('/emberfall debugweapongrowth EmberTester', 700); const m = /growth slot 0 spectral_sickles kills=(\d+) level=(\d+) meter=(\d+) ults=(\d+)/.exec(r); return m ? { kills: +m[1], level: +m[2], meter: +m[3], ults: +m[4] } : null; };
const foe = async (tag, dx, dz, hpv = BIG) => {
  await ask(`/execute at @s run summon emberfall:horde_zombie ~${dx} ~ ~${dz} {Tags:["${tag}","keep"],NoAI:1b,Silent:1b,PersistenceRequired:1b}`, 300);
  await ask(`/attribute @e[tag=${tag},limit=1] minecraft:max_health base set ${hpv}`, 120);
  await ask(`/data modify entity @e[tag=${tag},limit=1] Health set value ${hpv}.0f`, 120);
};
const g = async (kills, m) => ask(`/emberfall debugweapongrowth EmberTester grant 0 ${kills} ${m}`, 400);
const clear = async () => { await ask('/kill @e[tag=keep]', 500); };
// damage one foe takes over `ms` (it is never killed: 1024 hp)
const lostOver = async (tag, ms) => { const a = await hp(tag); await sleep(ms); const b = await hp(tag); return a !== null && b !== null ? a - b : NaN; };
bot.once('spawn', async () => {
  await sleep(5000);
  await ask('/gamemode survival'); await ask('/effect clear @s');
  await ask('/character select reaper'); await ask('/expedition', 2500);
  for (let i = 0; i < 40; i++) { await sleep(2000); if (/expedition/.test(await ask('/data get entity @s Dimension', 400))) break; }
  await sleep(2000);
  await ask('/effect give @s minecraft:resistance 999 4 true', 200); await ask('/effect give @s minecraft:regeneration 999 4 true', 200);
  await ask('/emberfall wavestop 0', 300); await ask('/time set midnight', 200);
  await ask('/kill @e[type=!player,type=!minecraft:item_display,type=!minecraft:interaction]', 900);
  const lo = await ask('/emberfall debugloadout EmberTester', 600);
  R('K0 the sickles are the only weapon, slot 0', /Loadout: spectral_sickles \|/.test(lo), lo.slice(-100));
  const bx = bot.entity.position.x, by = bot.entity.position.y, bz = bot.entity.position.z;
  const pin = setInterval(() => bot.chat(`/tp @s ${bx.toFixed(2)} ${by.toFixed(2)} ${bz.toFixed(2)} 0 0`), 800);

  // ---------- LEVEL 1 first (grant only ever ADDS kills)
  await g(0, 0);
  const pre = await meter();
  R('K4z the sickles start fresh: level 1, kills 0, no ultimate fired yet', pre && pre.level === 1 && pre.kills === 0 && pre.ults === 0, JSON.stringify(pre));

  // K1: beyond the normal reach
  await foe('far', 0, 3.6);
  const k1 = await lostOver('far', 6000);
  R('K1 level 1: a foe 3.6 blocks away is beyond the blades and untouched', k1 === 0, `lost ${k1}`);
  await clear();

  // K3 baseline: one foe ON the ring (2.2), damage in 10 s
  await foe('ring', 0, 2.7);                       // 2.7 is inside BOTH orbits' contact bands (level 1: 1.3..3.1, level 10: 2.3..4.9)
  const d1 = await lostOver('ring', 10000);
  console.log('L1 one foe on the ring lost', d1.toFixed(1), 'in 10 s');
  await clear();

  // K6 before: a foe at 3.4, outside the normal reach, 5 s of nothing
  await foe('band', 0, 3.4);
  const before = await lostOver('band', 5000);
  // K4/K5: fill the meter; the Rite starts on the next landed cut, so keep a cuttable foe on the ring too
  await foe('cut', 0, 2.2);
  await g(0, 999);
  const h0 = await hp('band');
  let u = null, tries = 0; const t0 = Date.now();
  while (tries++ < 40) { u = await meter(); if (u && u.ults >= 1) break; }
  R('K4 the full meter started the Reaper Rite exactly once (ults 0 -> 1)', u && u.ults === 1, JSON.stringify(u) + ` after ${Date.now() - t0} ms`);
  R('K5 the meter was consumed by it', u && u.meter < 300, `meter ${u && u.meter}`);
  await sleep(1500);                                   // inside the Rite's gather and slice (6.5 s at level 1)
  const h1 = await hp('band');
  R('K6 while the Rite runs a foe at 3.4 (outside the ring) is gathered and cut; before it, it was not touched', before === 0 && h0 !== null && h1 !== null && h0 - h1 > 0, `before ${before}, during ${(h0 - h1).toFixed(1)}`);
  await clear();
  await sleep(7500);                                   // the Rite is 130 ticks (6.5 s) at level 1: let it end before the next measurement

  // ---------- LEVEL 10
  await g(108, 0);
  const m10s = await meter();
  R('K2a the sickles are level 10', m10s && m10s.level === 10, JSON.stringify(m10s));
  await foe('far', 0, 3.6);
  const k2 = await lostOver('far', 6000);
  R('K2 level 10: the same foe is inside the wider orbit and is cut', k2 > 0, `lost ${k2}`);
  await clear();
  await foe('ring', 0, 2.7);                       // 2.7 is inside BOTH orbits' contact bands (level 1: 1.3..3.1, level 10: 2.3..4.9)
  const d10 = await lostOver('ring', 10000);
  console.log('L10 one foe on the ring lost', d10.toFixed(1), 'in 10 s');
  R('K3 one foe on the ring takes clearly more damage at level 10 than at level 1', d1 > 0 && d10 > d1 * 1.5, `L1 ${d1.toFixed(1)} L10 ${d10.toFixed(1)} ratio ${(d10 / d1).toFixed(2)}`);

  clearInterval(pin);
  await clear(); await ask('/expedition leave', 800);
  console.log(fails === 0 ? 'ALL PASS' : 'SOME FAIL ' + fails);
  bot.quit(); setTimeout(() => process.exit(0), 400);
});
