// Impact splash for the two projectile weapons. Frozen 1024 hp horde zombies, the player pinned.
// A primary foe stands 3 blocks ahead (the nearest, so it takes the aimed shot). Neighbours stand at a chosen distance FROM the primary,
// on the side AWAY from the player, so they are never the nearest and only get hurt by splash (or by an extra bolt, which is why the
// neighbour is compared against a control foe far outside any radius).
//   level 1 staff  radius 2.75: a neighbour 2.0 from the primary is hurt, one 4.0 away is not (beyond splash; only extra bolts could touch it)
//   level 10 staff radius 3.50: a neighbour 3.2 from the primary is hurt at level 10 and NOT at level 1
//   level 1 bow    radius 1.75: a neighbour 1.2 away is hurt, one 2.6 away is not
//   level 10 bow   radius 2.75: a neighbour 2.5 away is hurt at level 10 and NOT at level 1
const mineflayer = require('mineflayer');
const CHAR = process.env.SPLASH_CHAR || 'battlemage';           // battlemage = arcane staff, ranger = hunting bow
const WEAPON = CHAR === 'battlemage' ? 'arcane_staff' : 'hunting_bow';
const bot = mineflayer.createBot({ host: '127.0.0.1', port: 25565, username: 'EmberTester', version: '1.21.11', auth: 'offline' });
const sleep = ms => new Promise(r => setTimeout(r, ms));
const lines = []; bot.on('message', m => lines.push(m.toString())); bot.on('error', e => console.log('ERR', e.message));
const ask = async (x, w = 500) => { const n = lines.length; bot.chat(x); await sleep(w); return lines.slice(n).join(' | '); };
let fails = 0; const R = (n, ok, extra = '') => { console.log(`${ok ? 'PASS' : 'FAIL'} ${n} ${extra}`); if (!ok) fails++; };
const fs = require('fs');
const BIG = 1024;
const LOG = (process.env.EMBERFALL_HOME || '.') + '/run/server_run.log';
// SPLASH_TEST burst lines written since `from` (a line count), as {radius, reached}.
const bursts = from => fs.readFileSync(LOG, 'utf8').split('\n').slice(from).filter(l => l.includes('SPLASH_TEST burst')).map(l => { const m = /radius=([\d.]+) reached=(\d+)/.exec(l); return m ? { radius: +m[1], reached: +m[2] } : null; }).filter(Boolean);
const logLen = () => fs.readFileSync(LOG, 'utf8').split('\n').length;
const hp = async tag => { for (let i = 0; i < 4; i++) { const r = await ask(`/data get entity @e[tag=${tag},limit=1] Health`, 420 + i * 250); const m = /entity data: (-?[\d.]+)f/.exec(r); if (m) return +m[1]; } return null; };   // retry: a swallowed reply must not become a NaN result
const foe = async (tag, dx, dz) => {
  await ask(`/execute at @s run summon emberfall:horde_zombie ~${dx} ~ ~${dz} {Tags:["${tag}","keep"],NoAI:1b,Silent:1b,PersistenceRequired:1b}`, 300);
  await ask(`/attribute @e[tag=${tag},limit=1] minecraft:max_health base set ${BIG}`, 120);
  await ask(`/data modify entity @e[tag=${tag},limit=1] Health set value ${BIG}.0f`, 120);
};
const clear = async () => { await ask('/kill @e[tag=keep]', 500); };
const grant = async (kills) => ask(`/emberfall debugweapongrowth EmberTester grant 0 ${kills} 0`, 400);
// One trial: primary at z+3; a neighbour `gap` blocks beyond it (z+3+gap); a control foe 9 blocks beyond it. Returns hp lost by each.
async function trial(gap) {
  await foe('prim', 0, 3.0); await foe('nb', 0, +(3.0 + gap).toFixed(2)); await foe('ctl', 0, 3.0 + 7.0 + gap > 11 ? 9.0 : 3.0 + 7.0 + gap);
  const h0 = { p: await hp('prim'), n: await hp('nb'), c: await hp('ctl') };
  const mark = logLen();
  await sleep(5500);
  const h1 = { p: await hp('prim'), n: await hp('nb'), c: await hp('ctl') };
  await clear();
  if ([h0.p, h0.n, h0.c, h1.p, h1.n, h1.c].some(v => v === null)) console.log('   WARNING: an hp read failed after retries', JSON.stringify({ h0, h1 }));
  return { prim: h0.p - h1.p, nb: h0.n - h1.n, ctl: h0.c - h1.c, bursts: bursts(mark) };
}
const fmt = t => `prim ${t.prim.toFixed(1)} nb ${t.nb.toFixed(1)} ctl ${t.ctl.toFixed(1)}`;
bot.once('spawn', async () => {
  await sleep(5000);
  await ask('/gamemode survival'); await ask('/effect clear @s');
  await ask(`/character select ${CHAR}`); await ask('/expedition', 2500);
  for (let i = 0; i < 40; i++) { await sleep(2000); if (/expedition/.test(await ask('/data get entity @s Dimension', 400))) break; }
  await sleep(2000);
  await ask('/effect give @s minecraft:resistance 999 4 true', 200); await ask('/effect give @s minecraft:regeneration 999 4 true', 200);
  await ask('/emberfall wavestop 0', 300); await ask('/time set midnight', 200);
  await ask('/kill @e[type=!player,type=!minecraft:item_display,type=!minecraft:interaction]', 900);
  const lo = await ask('/emberfall debugloadout EmberTester', 600);
  R(`X0 ${CHAR} holds only the ${WEAPON}`, new RegExp('Loadout: ' + WEAPON + ' \\|').test(lo), lo.slice(-90));
  const bx = bot.entity.position.x, by = bot.entity.position.y, bz = bot.entity.position.z;
  const pin = setInterval(() => bot.chat(`/tp @s ${bx.toFixed(2)} ${by.toFixed(2)} ${bz.toFixed(2)} 0 0`), 800);
  const RAD = CHAR === 'battlemage' ? { l1: 2.75, l10: 3.5 } : { l1: 1.75, l10: 2.75 };
  const r1 = CHAR === 'battlemage' ? { in: 2.0, out: 4.0, grow: 3.2 } : { in: 1.2, out: 2.6, grow: 2.5 };
  const avg = (a, f) => a.reduce((s, x) => s + f(x), 0) / a.length;
  // ---- level 1
  await grant(0);
  const inside = [], outside = [];
  for (let k = 0; k < 2; k++) { inside.push(await trial(r1.in)); outside.push(await trial(r1.out)); }
  console.log('L1 inside ', inside.map(fmt).join(' || ')); console.log('L1 outside', outside.map(fmt).join(' || '));
  const b1 = inside.flatMap(t => t.bursts), bo = outside.flatMap(t => t.bursts);
  console.log(`   level 1 bursts: ${b1.length} inside-trials, radii ${[...new Set(b1.map(b => b.radius))]}, reached ${b1.map(b => b.reached).join(',')}`);
  R('X1 level 1: every burst has the level 1 radius ' + RAD.l1, b1.length >= 3 && b1.every(b => Math.abs(b.radius - RAD.l1) < 0.01), `${b1.length} bursts, radii ${[...new Set(b1.map(b => b.radius))]}`);
  R('X2 level 1: a neighbour inside the radius is reached by the burst and loses hp', b1.some(b => b.reached >= 1) && avg(inside, t => t.nb) > avg(inside, t => t.ctl) + 1.0, `reached max ${Math.max(0, ...b1.map(b => b.reached))}, nb ${avg(inside, t => t.nb).toFixed(1)} ctl ${avg(inside, t => t.ctl).toFixed(1)}`);
  R('X3 level 1: a neighbour beyond the radius is never reached by a burst', bo.length >= 3 && bo.every(b => b.reached === 0), `${bo.length} bursts, reached ${bo.map(b => b.reached).join(',')}`);
  // ---- level 10
  await grant(108);
  const lv = await ask('/emberfall debugweapongrowth EmberTester', 700);
  R('X4 the weapon is level 10', /level=10/.test(lv), lv.slice(-90));
  const grow = []; for (let k = 0; k < 2; k++) grow.push(await trial(r1.grow));
  const b10 = grow.flatMap(t => t.bursts);
  console.log('L10 grow  ', grow.map(fmt).join(' || '));
  console.log(`   level 10 bursts: ${b10.length}, radii ${[...new Set(b10.map(b => b.radius))]}, reached ${b10.map(b => b.reached).join(',')}`);
  R('X5 level 10: every burst has the grown radius ' + RAD.l10, b10.length >= 3 && b10.every(b => Math.abs(b.radius - RAD.l10) < 0.01), `${b10.length} bursts, radii ${[...new Set(b10.map(b => b.radius))]}`);
  R('X6 level 10: the neighbour that was beyond the level 1 burst is now inside it', b10.some(b => b.reached >= 1), `reached max ${Math.max(0, ...b10.map(b => b.reached))}`);
  clearInterval(pin);
  await clear(); await ask('/expedition leave', 800);
  console.log(fails === 0 ? 'ALL PASS' : 'SOME FAIL ' + fails);
  bot.quit(); setTimeout(() => process.exit(0), 400);
});
