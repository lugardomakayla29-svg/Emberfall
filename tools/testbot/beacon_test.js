// Ashen Beacon growth. emberwarden starts with ONLY the beacon. Foes are 1024 hp frozen horde zombies, player pinned.
// The beacon is planted ON the target (the nearest foe) and is a no-op while one is live, so every phase waits for it to burn out.
//   A0  the beacon is the only weapon, slot 0
//   A1  level 1: a foe 4.0 from the beacon is outside the pulse (radius 3.0) and untouched
//   A2  level 10: a foe 4.0 from the beacon is inside the pulse (radius 5.0) and is hurt
//   A3  satellites: a foe 6.5 from the beacon (outside radius 5.0, inside the satellite reach) is hurt at level 10, untouched at level 1
//   A4  Pyre Nova: a full meter fires it exactly once (ults 0 -> 1) at level 1
//   A5  the meter is consumed by it
//   A6  a foe caught by the pulse is set alight
const mineflayer = require('mineflayer');
const bot = mineflayer.createBot({ host: '127.0.0.1', port: 25565, username: 'EmberTester', version: '1.21.11', auth: 'offline' });
const sleep = ms => new Promise(r => setTimeout(r, ms));
const lines = []; bot.on('message', m => lines.push(m.toString())); bot.on('error', e => console.log('ERR', e.message));
const ask = async (x, w = 500) => { const n = lines.length; bot.chat(x); await sleep(w); return lines.slice(n).join(' | '); };
let fails = 0; const R = (n, ok, extra = '') => { console.log(`${ok ? 'PASS' : 'FAIL'} ${n} ${extra}`); if (!ok) fails++; };
const BIG = 1024;
const hp = async tag => { const r = await ask(`/data get entity @e[tag=${tag},limit=1] Health`, 420); const m = /entity data: (-?[\d.]+)f/.exec(r); return m ? +m[1] : null; };
const fire = async tag => { const r = await ask(`/data get entity @e[tag=${tag},limit=1] Fire`, 420); const m = /entity data: (-?\d+)s/.exec(r); return m ? +m[1] : null; };
const meter = async () => { const r = await ask('/emberfall debugweapongrowth EmberTester', 700); const m = /growth slot 0 ashen_beacon kills=(\d+) level=(\d+) meter=(\d+) ults=(\d+)/.exec(r); return m ? { kills: +m[1], level: +m[2], meter: +m[3], ults: +m[4] } : null; };
const foe = async (tag, dx, dz, hpv = BIG) => {
  await ask(`/execute at @s run summon emberfall:horde_zombie ~${dx} ~ ~${dz} {Tags:["${tag}","keep"],NoAI:1b,Silent:1b,PersistenceRequired:1b}`, 300);
  await ask(`/attribute @e[tag=${tag},limit=1] minecraft:max_health base set ${hpv}`, 120);
  await ask(`/data modify entity @e[tag=${tag},limit=1] Health set value ${hpv}.0f`, 120);
};
const g = async (kills, m) => ask(`/emberfall debugweapongrowth EmberTester grant 0 ${kills} ${m}`, 400);
const clear = async () => { await ask('/kill @e[tag=keep]', 500); };
const lostOver = async (tag, ms) => { const a = await hp(tag); await sleep(ms); const b = await hp(tag); return a !== null && b !== null ? a - b : NaN; };
// the beacon lives 10 s (level 1) to 14 s (level 10) and a Pyre Nova adds 24 ticks; wait it all out before the next phase
const burnOut = async () => { await clear(); await sleep(16000); };
bot.once('spawn', async () => {
  await sleep(5000);
  await ask('/gamemode survival'); await ask('/effect clear @s');
  await ask('/character select emberwarden'); await ask('/expedition', 2500);
  for (let i = 0; i < 40; i++) { await sleep(2000); if (/expedition/.test(await ask('/data get entity @s Dimension', 400))) break; }
  await sleep(2000);
  await ask('/effect give @s minecraft:resistance 999 4 true', 200); await ask('/effect give @s minecraft:regeneration 999 4 true', 200);
  await ask('/emberfall wavestop 0', 300); await ask('/time set midnight', 200);
  await ask('/kill @e[type=!player,type=!minecraft:item_display,type=!minecraft:interaction]', 900);
  const lo = await ask('/emberfall debugloadout EmberTester', 600);
  R('A0 the beacon is the only weapon, slot 0', /Loadout: ashen_beacon \|/.test(lo), lo.slice(-100));
  const bx = bot.entity.position.x, by = bot.entity.position.y, bz = bot.entity.position.z;
  const pin = setInterval(() => bot.chat(`/tp @s ${bx.toFixed(2)} ${by.toFixed(2)} ${bz.toFixed(2)} 0 0`), 800);

  // ---------- LEVEL 1 (grant only ever ADDS kills)
  await g(0, 0);
  const pre = await meter();
  R('A4z the beacon starts fresh: level 1, kills 0, no Pyre Nova yet', pre && pre.level === 1 && pre.kills === 0 && pre.ults === 0, JSON.stringify(pre));

  // A1 + A3 + A6 (REWRITTEN for the Living Flames): primary at 3.0 (the beacon lands on it), near foe 4.0 beyond it, far foe 6.5 beyond it.
  // The old design left both untouched at level 1; now a flame hunts them, so BOTH are hurt, and the pulse itself (radius 3.0) still is not what reaches them.
  await foe('prim', 0, 3.0); await foe('near', 0, 7.0); await foe('farx', 0, 9.5);
  await sleep(1000);
  const n0 = await hp('near'), f0 = await hp('farx');
  await sleep(7000);
  const n1 = await hp('near'), f1 = await hp('farx');
  const fl = await fire('prim');
  R('A1 level 1: a flame reaches a foe 4.0 from the beacon (outside the pulse)', n0 !== null && n1 !== null && n0 - n1 > 0, `${n0} -> ${n1}`);
  R('A3a level 1: a foe 6.5 from the beacon is within the flame hunt reach (7.0) and hurt', f0 !== null && f1 !== null && f0 - f1 > 0, `${f0} -> ${f1}`);
  R('A6 a foe caught by the pulse is set alight', fl !== null && fl > 0, `Fire ${fl}`);
  await burnOut();

  // A4/A5: fill the meter; the Nova starts on the next pulse that lands damage, so a foe must be in the pulse
  await foe('prim', 0, 3.0);
  await g(0, 999);
  let u = null, tries = 0; const t0 = Date.now();
  while (tries++ < 40) { u = await meter(); if (u && u.ults >= 1) break; }
  R('A4 the full meter fired the Pyre Nova exactly once (ults 0 -> 1)', u && u.ults === 1, JSON.stringify(u) + ` after ${Date.now() - t0} ms`);
  R('A5 the meter was consumed by it', u && u.meter < 300, `meter ${u && u.meter}`);
  await burnOut();

  // ---------- LEVEL 10
  await g(108, 0);
  const m10 = await meter();
  R('A2a the beacon is level 10', m10 && m10.level === 10, JSON.stringify(m10));
  await foe('prim', 0, 3.0); await foe('near', 0, 7.0); await foe('farx', 0, 9.5);
  await sleep(1000);
  const nn0 = await hp('near'), ff0 = await hp('farx');
  await sleep(9000);
  const nn1 = await hp('near'), ff1 = await hp('farx');
  R('A2 level 10: a foe 4.0 from the beacon is inside the pulse and is hurt', nn0 !== null && nn1 !== null && nn0 - nn1 > 0, `${nn0} -> ${nn1}`);
  R('A3 level 10: a foe 6.5 from the beacon (outside the pulse) is reached by the satellites', ff0 !== null && ff1 !== null && ff0 - ff1 > 0, `${ff0} -> ${ff1}`);

  clearInterval(pin);
  await clear(); await ask('/expedition leave', 800);
  console.log(fails === 0 ? 'ALL PASS' : 'SOME FAIL ' + fails);
  bot.quit(); setTimeout(() => process.exit(0), 400);
});
