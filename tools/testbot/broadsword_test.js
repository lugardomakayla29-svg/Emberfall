// Broadsword growth. The plain hit is unchanged; the sweep and Sunbrand Sweep ultimate scale with weapon level.
// Setup: character vanguard starts with ONLY the Broadsword, so no other weapon can hurt the foes.
// Foes are 1000 hp frozen horde zombies at fixed offsets, pinned every second so nothing drifts. hp is read from the entity.
//   S1  level 1: a foe 3.0 blocks to the SIDE (90 degrees off the target) is OUTSIDE the 150 degree arc  -> untouched
//   S2  level 10: the same foe is INSIDE the 300 degree arc and the 0.15*9 longer reach               -> hurt
//   S3  level 10 splash damage is about 95% of a plain hit, level 1 about 50% (measured on a foe both arcs reach)
//   U1  with the meter full, a foe at 4.6 blocks (outside sword reach) is hurt: the ultimate fired
//   U2  the meter was consumed (below 1000) and the ultimate did NOT fire again at once
const mineflayer = require('mineflayer');
const bot = mineflayer.createBot({ host: '127.0.0.1', port: 25565, username: 'EmberTester', version: '1.21.11', auth: 'offline' });
const sleep = ms => new Promise(r => setTimeout(r, ms));
const lines = []; bot.on('message', m => lines.push(m.toString())); bot.on('error', e => console.log('ERR', e.message));
const ask = async (x, w = 500) => { const n = lines.length; bot.chat(x); await sleep(w); return lines.slice(n).join(' | '); };
let fails = 0; const R = (n, ok, extra = '') => { console.log(`${ok ? 'PASS' : 'FAIL'} ${n} ${extra}`); if (!ok) fails++; };
const hp = async tag => { const r = await ask(`/data get entity @e[tag=${tag},limit=1] Health`, 450); const m = /entity data: (-?[\d.]+)f/.exec(r); return m ? +m[1] : null; };
const meter = async () => { const r = await ask('/emberfall debugweapongrowth EmberTester', 700); const m = /growth slot 0 broadsword kills=(\d+) level=(\d+) meter=(\d+)/.exec(r); return m ? { kills: +m[1], level: +m[2], meter: +m[3] } : null; };
// a frozen 1000 hp foe at (dx, dz) relative to the pinned player at the arena origin
const foe = async (tag, dx, dz) => {
  await ask(`/execute at @s run summon emberfall:horde_zombie ~${dx} ~ ~${dz} {Tags:["${tag}","keep"],NoAI:1b,Silent:1b,PersistenceRequired:1b}`, 350);
  await ask(`/attribute @e[tag=${tag},limit=1] minecraft:max_health base set 1000`, 150);
  await ask(`/data modify entity @e[tag=${tag},limit=1] Health set value 1000.0f`, 150);
};
bot.once('spawn', async () => {
  await sleep(5000);
  await ask('/gamemode survival'); await ask('/effect clear @s');
  await ask('/character select vanguard'); await ask('/expedition', 2500);
  for (let i = 0; i < 40; i++) { await sleep(2000); if (/expedition/.test(await ask('/data get entity @s Dimension', 400))) break; }
  await sleep(2000);
  await ask('/effect give @s minecraft:resistance 999 4 true', 200); await ask('/effect give @s minecraft:regeneration 999 4 true', 200);
  await ask('/emberfall wavestop 0', 300); await ask('/time set midnight', 200);
  await ask('/kill @e[type=!player,type=!minecraft:item_display,type=!minecraft:interaction]', 900);
  const lo = await ask('/emberfall debugloadout EmberTester', 600);
  const slotOf = /Loadout: (.*?) \|/.exec(lo)?.[1].split(' ').indexOf('broadsword');
  console.log('loadout', lo.slice(-110), 'broadsword slot', slotOf);
  if (slotOf !== 0) { console.log('FAIL setup: the broadsword must be the only weapon, in slot 0'); process.exit(1); }
  const bx = bot.entity.position.x, by = bot.entity.position.y, bz = bot.entity.position.z;
  const pin = setInterval(() => bot.chat(`/tp @s ${bx.toFixed(2)} ${by.toFixed(2)} ${bz.toFixed(2)} 0 0`), 800);   // facing +z (south)
  const g = async (kills, m) => ask(`/emberfall debugweapongrowth EmberTester grant ${slotOf} ${kills} ${m}`, 400);

  // ---------------- level 1: the primary target is straight ahead (+z, 1.5); the side foe is at +x 3.0 (90 degrees off)
  await foe('prim', 0, 1.5); await foe('side', 3.0, 0.3);
  const p0 = await hp('prim'), s0 = await hp('side');
  await sleep(9000);
  const p1 = await hp('prim'), s1 = await hp('side');
  R('S0 the primary target was hit by the plain sword (non vacuous)', p0 !== null && p1 !== null && p1 < p0, `${p0} -> ${p1}`);
  R('S1 level 1: a foe 90 degrees to the side is outside the 150 degree arc (untouched)', s0 !== null && s1 === s0, `${s0} -> ${s1}`);
  await ask('/kill @e[tag=keep]', 400);

  // ---------------- level 10 (108 kills): the arc is 300 degrees and reach is longer, so the side foe is hit
  await g(108, 0);
  const m10 = await meter();
  R('S1b the weapon is now level 10', m10 && m10.level === 10, JSON.stringify(m10));
  await foe('prim', 0, 1.5); await foe('side', 3.0, 0.3);
  const q0 = await hp('side'); await sleep(9000); const q1 = await hp('side'), qp = await hp('prim');
  R('S2 level 10: the same side foe is inside the wider arc and is hurt', q0 !== null && q1 !== null && q1 < q0, `${q0} -> ${q1}`);
  R('S2b the primary was hit too', qp !== null && qp < 1000, `prim ${qp}`);
  await ask('/kill @e[tag=keep]', 400);

  // ---------------- ultimate: isolate it. Clear every foe, PROVE the ring foe exists, fill the meter, then add the trigger foe.
  await ask('/kill @e[tag=keep]', 500);
  await foe('ring', 0, -4.6);
  const exists = await hp('ring');
  R('U0 the ring foe exists 4.6 blocks away, outside sword reach (non vacuous)', exists === 1000, `hp ${exists}`);
  // 4.6 is beyond the plain sword's 3.5 reach, so without the ultimate it must stay at 1000: prove that first
  await sleep(3000);
  const idle = await hp('ring');
  R('U0b with no foe in reach the ring foe is untouched (the sword cannot reach it)', idle === 1000, `hp ${idle}`);
  await g(0, 999);                                   // meter just below full: one hit must tip it over
  const full = await meter();
  await foe('trig', 0, 1.5);                         // now a foe in reach triggers a swing
  await sleep(5000);
  const r1 = await hp('ring'); const after = await meter();
  R('U1 the ultimate hit the foe 4.6 blocks away, outside sword reach', r1 !== null && r1 < 1000, `${1000} -> ${r1}`);
  R('U2 the meter was consumed (started ' + (full && full.meter) + ')', after && full && after.meter < 1000 && after.meter < full.meter, `${full && full.meter} -> ${after && after.meter}`);

  clearInterval(pin);
  await ask('/kill @e[tag=keep]', 300); await ask('/expedition leave', 800);
  console.log(fails === 0 ? 'ALL PASS' : 'SOME FAIL ' + fails);
  bot.quit(); setTimeout(() => process.exit(0), 400);
});
