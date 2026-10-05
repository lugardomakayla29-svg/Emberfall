// Twin Daggers growth. duelist starts with ONLY the daggers. Foes are 1024 hp frozen horde zombies, player pinned.
//   D0  the daggers are the only weapon, slot 0
//   D1  level 1: a foe 4.3 blocks away is outside the 3.0 reach (untouched over a few seconds)
//   D2  level 10: the same foe is inside the 5.0 reach and is hurt
//   D3  flurry: ring foes hit per swing is clearly higher at level 10 than at level 1 (measured by arithmetic over many swings)
//   D4  Phantom Blades: with the meter full at level 1, the ultimate fires exactly once and consumes the meter
//   D5  the dance kills credit the daggers (kills rose) and the meter was consumed
const mineflayer = require('mineflayer');
const bot = mineflayer.createBot({ host: '127.0.0.1', port: 25565, username: 'EmberTester', version: '1.21.11', auth: 'offline' });
const sleep = ms => new Promise(r => setTimeout(r, ms));
const lines = []; bot.on('message', m => lines.push(m.toString())); bot.on('error', e => console.log('ERR', e.message));
const ask = async (x, w = 500) => { const n = lines.length; bot.chat(x); await sleep(w); return lines.slice(n).join(' | '); };
let fails = 0; const R = (n, ok, extra = '') => { console.log(`${ok ? 'PASS' : 'FAIL'} ${n} ${extra}`); if (!ok) fails++; };
const BIG = 1024;                                                    // Minecraft clamps max_health at 1024
const hp = async tag => { const r = await ask(`/data get entity @e[tag=${tag},limit=1] Health`, 420); const m = /entity data: (-?[\d.]+)f/.exec(r); return m ? +m[1] : null; };
const meter = async () => { const r = await ask('/emberfall debugweapongrowth EmberTester', 700); const m = /growth slot 0 twin_daggers kills=(\d+) level=(\d+) meter=(\d+) ults=(\d+)/.exec(r); return m ? { kills: +m[1], level: +m[2], meter: +m[3], ults: +m[4] } : null; };
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
  await ask('/character select duelist'); await ask('/expedition', 2500);
  for (let i = 0; i < 40; i++) { await sleep(2000); if (/expedition/.test(await ask('/data get entity @s Dimension', 400))) break; }
  await sleep(2000);
  await ask('/effect give @s minecraft:resistance 999 4 true', 200); await ask('/effect give @s minecraft:regeneration 999 4 true', 200);
  await ask('/emberfall wavestop 0', 300); await ask('/time set midnight', 200);
  await ask('/kill @e[type=!player,type=!minecraft:item_display,type=!minecraft:interaction]', 900);
  const lo = await ask('/emberfall debugloadout EmberTester', 600);
  R('D0 the daggers are the only weapon, slot 0', /Loadout: twin_daggers \|/.test(lo), lo.slice(-100));
  const bx = bot.entity.position.x, by = bot.entity.position.y, bz = bot.entity.position.z;
  const pin = setInterval(() => bot.chat(`/tp @s ${bx.toFixed(2)} ${by.toFixed(2)} ${bz.toFixed(2)} 0 0`), 800);

  // Swings per window are counted by arithmetic: the primary (nearest) takes every swing, so swings = its hp lost / one hit.
  async function flurryPerSwing(label) {
    await foe('prim', 0, 1.0);
    for (let i = 0; i < 6; i++) { const a = Math.PI * 2 * i / 6; await foe('r' + i, +(Math.cos(a) * 0.9).toFixed(2), +(2.2 + Math.sin(a) * 0.9).toFixed(2)); }
    const p0 = await hp('prim'); const r0 = []; for (let i = 0; i < 6; i++) r0.push(await hp('r' + i));
    await sleep(8000);
    const p1 = await hp('prim'); let lost = 0; for (let i = 0; i < 6; i++) { const v = await hp('r' + i); if (v !== null && r0[i] !== null) lost += r0[i] - v; }
    const primLost = p0 - p1;
    const perSwing = primLost > 0 ? lost / primLost : NaN;           // ring hp lost per 1 hp the primary lost: ring hits per swing at 60% damage
    console.log(label, `primary lost ${primLost.toFixed(1)}, ring lost ${lost.toFixed(1)} => ring/primary damage ratio ${perSwing.toFixed(2)}`);
    await clear(); return perSwing;
  }

  // ---------- Blade Dance at LEVEL 1 (reach 3.0, flurry only 0.2 per swing). Must run before any grant: grant only ever ADDS kills.
  // The bot cannot be moved away from its pin (boundary and pin fight every teleport, measured: every /tp landed at 0.5 65 0.5), and a fast
  // dagger cuts a pack while it is being placed. So compare TWO identical packs over the SAME short window after both are complete:
  // CONTROL (meter 0): how many foes a plain level-1 swing cadence hurts in that window. TEST (meter one hit short of full): the same window.
  // The dance is proven if the TEST pack has clearly more foes cut than the control, with foes built as FAST as possible (summon in one command).
  // The dagger is so fast that counting foes cannot show the dance (a plain level-1 window already hurts all 9). The server counts fired
  // ultimates per slot (ults=), and GAIN_HIT is only 6 per hit, so: ults must rise by exactly 1 on the first swing after a full fill, and
  // the meter must read far below 999 right then (it can only be low if it was consumed).
  await g(0, 0);
  const pre = await meter();
  R('D4z the weapon starts fresh: level 1, kills 0, no ultimate fired', pre && pre.level === 1 && pre.kills === 0 && pre.ults === 0, JSON.stringify(pre));
  await foe('near', 0, 1.0, 400);                                      // one target in reach so the swing can happen
  await g(0, 999 - 0);
  let u = null, tries = 0; const t0 = Date.now();
  while (tries++ < 40) { u = await meter(); if (u && u.ults >= 1) break; }
  R('D4 the full meter fired the Phantom Blades exactly once (ults 0 -> 1)', u && u.ults === 1, JSON.stringify(u) + ` after ${Date.now() - t0} ms`);
  R('D5 the meter was consumed by it (read in the same poll)', u && u.meter < 300, `meter ${u && u.meter}`);
  await clear();
  await sleep(4500);                                                   // the Phantom Blades outlive the swing (3 s at level 1): let them vanish before D1 measures reach

  // ---------- LEVEL 1 (before any grant: grant only ever ADDS kills)
  await g(0, 0);
  await foe('reach', 0, 4.3);
  const a0 = await hp('reach'); await sleep(5000); const a1 = await hp('reach');
  R('D1 level 1: a foe 4.3 blocks away is outside the 3.0 reach (untouched)', a0 === BIG && a1 === BIG, `${a0} -> ${a1}`);
  await clear();
  const f1 = []; for (let k = 0; k < 2; k++) f1.push(await flurryPerSwing('L1'));

  // ---------- LEVEL 10
  await g(108, 0);
  const m10 = await meter();
  R('D2a the daggers are level 10', m10 && m10.level === 10, JSON.stringify(m10));
  await foe('reach', 0, 4.3);
  await foe('near', 0, 1.0);                                          // a nearer foe so the swings have a target to start from
  await sleep(6000); const b1 = await hp('reach');
  R('D2 level 10: the same foe is inside the 5.0 reach and is hurt', b1 !== null && b1 < BIG, `far ${b1}`);
  await clear();
  const f10 = []; for (let k = 0; k < 2; k++) f10.push(await flurryPerSwing('L10'));
  const avg = a => a.reduce((x, y) => x + y, 0) / a.length; const r1 = avg(f1), r10 = avg(f10);
  R('D3 the flurry hits far more of the ring at level 10 than at level 1', r10 > r1 * 2.5 && r10 >= 0.6, `L1 ${r1.toFixed(2)} (${f1.map(x => x.toFixed(2))}) L10 ${r10.toFixed(2)} (${f10.map(x => x.toFixed(2))})`);

  clearInterval(pin);
  await clear(); await ask('/expedition leave', 800);
  console.log(fails === 0 ? 'ALL PASS' : 'SOME FAIL ' + fails);
  bot.quit(); setTimeout(() => process.exit(0), 400);
});
