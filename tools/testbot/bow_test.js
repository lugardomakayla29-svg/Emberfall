// Hunting Bow growth. ranger starts with ONLY the bow. Foes are 1024 hp frozen horde zombies, player pinned.
//   B0  the bow is the only weapon, slot 0
//   B1  level 1: a foe 11.5 blocks away is outside the 10 range (untouched)
//   B2  level 10: the same foe is inside the 13 range and is hurt
//   B3  multishot: ring damage relative to the primary is ~0 at level 1 and clearly higher at level 10
//   B4  Storm of Arrows: a full meter fires it exactly once (ults 0 -> 1) at level 1
//   B5  the meter is consumed by it
const mineflayer = require('mineflayer');
const bot = mineflayer.createBot({ host: '127.0.0.1', port: 25565, username: 'EmberTester', version: '1.21.11', auth: 'offline' });
const sleep = ms => new Promise(r => setTimeout(r, ms));
const lines = []; bot.on('message', m => lines.push(m.toString())); bot.on('error', e => console.log('ERR', e.message));
const ask = async (x, w = 500) => { const n = lines.length; bot.chat(x); await sleep(w); return lines.slice(n).join(' | '); };
let fails = 0; const R = (n, ok, extra = '') => { console.log(`${ok ? 'PASS' : 'FAIL'} ${n} ${extra}`); if (!ok) fails++; };
const BIG = 1024;
const hp = async tag => { const r = await ask(`/data get entity @e[tag=${tag},limit=1] Health`, 420); const m = /entity data: (-?[\d.]+)f/.exec(r); return m ? +m[1] : null; };
const meter = async () => { const r = await ask('/emberfall debugweapongrowth EmberTester', 700); const m = /growth slot 0 hunting_bow kills=(\d+) level=(\d+) meter=(\d+) ults=(\d+)/.exec(r); return m ? { kills: +m[1], level: +m[2], meter: +m[3], ults: +m[4] } : null; };
const foe = async (tag, dx, dz, hpv = BIG) => {
  await ask(`/execute at @s run summon emberfall:horde_zombie ~${dx} ~ ~${dz} {Tags:["${tag}","keep"],NoAI:1b,Silent:1b,PersistenceRequired:1b}`, 300);
  await ask(`/attribute @e[tag=${tag},limit=1] minecraft:max_health base set ${hpv}`, 120);
  await ask(`/data modify entity @e[tag=${tag},limit=1] Health set value ${hpv}.0f`, 120);
};
const g = async (kills, m) => ask(`/emberfall debugweapongrowth EmberTester grant 0 ${kills} ${m}`, 400);
const clear = async () => { await ask('/kill @e[tag=keep]', 500); };
bot.once('spawn', async () => {
  await sleep(5000);
  await ask('/gamemode survival'); await ask('/effect clear @s');
  await ask('/character select ranger'); await ask('/expedition', 2500);
  for (let i = 0; i < 40; i++) { await sleep(2000); if (/expedition/.test(await ask('/data get entity @s Dimension', 400))) break; }
  await sleep(2000);
  await ask('/effect give @s minecraft:resistance 999 4 true', 200); await ask('/effect give @s minecraft:regeneration 999 4 true', 200);
  await ask('/emberfall wavestop 0', 300); await ask('/time set midnight', 200);
  await ask('/kill @e[type=!player,type=!minecraft:item_display,type=!minecraft:interaction]', 900);
  const lo = await ask('/emberfall debugloadout EmberTester', 600);
  R('B0 the bow is the only weapon, slot 0', /Loadout: hunting_bow \|/.test(lo), lo.slice(-100));
  const bx = bot.entity.position.x, by = bot.entity.position.y, bz = bot.entity.position.z;
  const pin = setInterval(() => bot.chat(`/tp @s ${bx.toFixed(2)} ${by.toFixed(2)} ${bz.toFixed(2)} 0 0`), 800);

  // ---------- Storm of Arrows at LEVEL 1, first (grant only ever ADDS kills). The fire counter is the proof, not a foe count.
  await g(0, 0);
  const pre = await meter();
  R('B4z the bow starts fresh: level 1, kills 0, no ultimate fired', pre && pre.level === 1 && pre.kills === 0 && pre.ults === 0, JSON.stringify(pre));
  await foe('near', 0, 4.0, 400);
  await g(0, 999);
  let u = null, tries = 0; const t0 = Date.now();
  while (tries++ < 40) { u = await meter(); if (u && u.ults >= 1) break; }
  R('B4 the full meter fired the Storm of Arrows exactly once (ults 0 -> 1)', u && u.ults === 1, JSON.stringify(u) + ` after ${Date.now() - t0} ms`);
  R('B5 the meter was consumed by it', u && u.meter < 300, `meter ${u && u.meter}`);
  await clear();

  // multishot: a primary at 3 (nearest) and a ring 6 away; the primary takes the shots, the ring takes the extras
  async function multishot(label) {
    await foe('prim', 0, 3.0);
    for (let i = 0; i < 4; i++) { const a = Math.PI * 2 * i / 4; await foe('r' + i, +(Math.cos(a) * 1.2).toFixed(2), +(7.0 + Math.sin(a) * 1.2).toFixed(2)); }
    const p0 = await hp('prim'); const r0 = []; for (let i = 0; i < 4; i++) r0.push(await hp('r' + i));
    await sleep(9000);
    const p1 = await hp('prim'); let lost = 0; for (let i = 0; i < 4; i++) { const v = await hp('r' + i); if (v !== null && r0[i] !== null) lost += r0[i] - v; }
    const primLost = p0 - p1; const ratio = primLost > 0 ? lost / primLost : NaN;
    console.log(label, `primary lost ${primLost.toFixed(1)}, ring lost ${lost.toFixed(1)} => ring/primary ${ratio.toFixed(2)}`);
    await clear(); return ratio;
  }
  // ---------- LEVEL 1
  await foe('reach', 0, 11.5);
  const a0 = await hp('reach'); await sleep(6000); const a1 = await hp('reach');
  R('B1 level 1: a foe 11.5 blocks away is outside the 10 range (untouched)', a0 === BIG && a1 === BIG, `${a0} -> ${a1}`);
  await clear();
  const m1 = []; for (let k = 0; k < 2; k++) m1.push(await multishot('L1'));
  // ---------- LEVEL 10
  await g(108, 0);
  const m10s = await meter();
  R('B2a the bow is level 10', m10s && m10s.level === 10, JSON.stringify(m10s));
  await foe('reach', 0, 11.5); await foe('near', 0, 3.0);
  await sleep(7000); const b1 = await hp('reach');
  R('B2 level 10: the same foe is inside the 13 range and is hurt', b1 !== null && b1 < BIG, `far ${b1}`);
  await clear();
  const m10 = []; for (let k = 0; k < 2; k++) m10.push(await multishot('L10'));
  const avg = a => a.reduce((x, y) => x + y, 0) / a.length;
  R('B3 multishot sends far more damage to the other foes at level 10 than at level 1', avg(m10) > avg(m1) * 2.5 && avg(m10) >= 0.4, `L1 ${avg(m1).toFixed(2)} (${m1.map(x => x.toFixed(2))}) L10 ${avg(m10).toFixed(2)} (${m10.map(x => x.toFixed(2))})`);

  clearInterval(pin);
  await clear(); await ask('/expedition leave', 800);
  console.log(fails === 0 ? 'ALL PASS' : 'SOME FAIL ' + fails);
  bot.quit(); setTimeout(() => process.exit(0), 400);
});
