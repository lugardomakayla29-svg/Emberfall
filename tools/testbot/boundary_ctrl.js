const mineflayer = require('mineflayer');
const bot = mineflayer.createBot({ host: '127.0.0.1', port: 25565, username: 'EmberTester', version: '1.21.11', auth: 'offline' });
const sleep = ms => new Promise(r => setTimeout(r, ms));
const lines = []; bot.on('message', m => lines.push(m.toString()));
const ask = async (x, w = 900) => { const n = lines.length; bot.chat(x); await sleep(w); return lines.slice(n).join(' | '); };
const R = (n, ok, extra = '') => console.log(`${ok ? 'PASS' : 'FAIL'} ${n} ${extra}`);
let dust = 0, dustNear = [];
bot._client.on('packet', (d, meta) => { if (meta.name === 'world_particles') { dust++; dustNear.push({ x: d.x, z: d.z }); } });
const box = async () => { const r = await ask('/emberfall arenabox EmberTester', 700); const m = /ARENABOX ([-\d.]+),([-\d.]+),([-\d.]+),([-\d.]+)\|([-\d.]+)\|(\w+)/.exec(r); return m ? { x0: +m[1], z0: +m[2], x1: +m[3], z1: +m[4], over: +m[5], inPlace: m[6] === 'true' } : null; };
const pos = async () => { const r = await ask('/data get entity EmberTester Pos', 700); const m = /\[([-\d.]+)d, ([-\d.]+)d, ([-\d.]+)d\]/.exec(r); return m ? { x: +m[1], y: +m[2], z: +m[3] } : null; };
const tp = (x, y, z) => ask(`/tp EmberTester ${x} ${y} ${z}`, 500);
bot.once('spawn', async () => {
  await sleep(4000);
  await ask('/gamemode survival'); await ask('/effect give @s minecraft:resistance 999 4 true'); await ask('/effect give @s minecraft:regeneration 999 4 true');
  await ask('/tp @s 7.5 70 -0.5', 1500);   // known dry surface: a previous run's B10 leaves the bot underground, and the start is refused there
  await ask('/character select juggernaut'); await ask('/expedition', 3000);
  const b = await box();
  R('B0 in-place arena box read', b && b.inPlace && b.x1 - b.x0 === 57, b ? `(x ${b.x0}..${b.x1}, z ${b.z0}..${b.z1}, ${b.x1 - b.x0} wide)` : 'none');
  if (!b) { bot.quit(); return process.exit(0); }
  const cx = (b.x0 + b.x1) / 2, cz = (b.z0 + b.z1) / 2;
  const p0 = await pos();
  const y = p0.y;
  // middle: no curtain
  await tp(cx, y + 1, cz); await sleep(1500); dust = 0; dustNear = []; await sleep(2000);
  const midDust = dustNear.filter(p => Math.abs(p.x - b.x0) < 2 || Math.abs(p.x - b.x1) < 2 || Math.abs(p.z - b.z0) < 2 || Math.abs(p.z - b.z1) < 2).length;
  R('B1 no wall dust in the middle of the arena', midDust === 0, `(${midDust} wall particles)`);
  // near the east edge, inside: curtain appears exactly on that edge
  await tp(b.x1 - 2.5, y + 1, cz); await sleep(1500); dust = 0; dustNear = []; await sleep(2000);
  const onEast = dustNear.filter(p => Math.abs(p.x - b.x1) < 0.01).length;
  R('B2 curtain drawn on the east edge when 2.5 blocks from it', onEast > 20, `(${onEast} particles on x=${b.x1})`);
  const offWall = dustNear.filter(p => Math.abs(p.x - b.x1) > 0.01 && Math.abs(p.x - b.x0) > 0.01 && Math.abs(p.z - b.z0) > 0.01 && Math.abs(p.z - b.z1) > 0.01).length;
  R('B3 curtain particles all sit on an edge plane', offWall === 0, `(${offWall} stray)`);
  // just past the edge: pushed back in
  await tp(b.x1 + 1.5, y + 1, cz); await sleep(2500);
  const p1 = await pos(); const b1 = await box();
  R('B4 a player 1.5 past the edge is pushed back inside', b1.over <= 0.3, `(overshoot now ${b1.over}, x ${p1.x.toFixed(1)} vs edge ${b.x1})`);
  // far outside: clamped back
  await tp(b.x1 + 30, y + 1, cz + 5); await sleep(2500);
  const b2 = await box();
  R('B5 a player 30 blocks outside is brought back inside', b2.over <= 0.3, `(overshoot now ${b2.over})`);
  await tp(b.x0 - 25, y + 1, b.z0 - 25); await sleep(2500);
  const b3 = await box();
  R('B6 a corner escape (both axes) is brought back inside', b3.over <= 0.3, `(overshoot now ${b3.over})`);
  const pf = await pos();
  R('B7 clamped player is on solid ground, not in the air', pf.y > -60 && pf.y < 320, `(y ${pf.y.toFixed(1)}, start y ${y.toFixed(1)})`);
  // mobs
  await tp(cx, y + 1, cz); await sleep(1500);
  await ask(`/summon emberfall:horde_zombie ${b.x1 + 12} ${y + 1} ${cz} {Tags:["strayA"],Attributes:[{id:"minecraft:movement_speed",base:0.0}]}`, 600);
  await ask(`/summon emberfall:tiki_magma ${cx + 3} ${y + 1} ${cz + 3} {Tags:["insideB"],Attributes:[{id:"minecraft:movement_speed",base:0.0}]}`, 600);
  await sleep(3000);
  const inQ = async tag => { const r = await ask(`/data get entity @e[tag=${tag},limit=1] Pos`, 700); const m = /\[([-\d.]+)d, ([-\d.]+)d, ([-\d.]+)d\]/.exec(r); return m ? { x: +m[1], z: +m[3] } : null; };
  const a = await inQ('strayA'), c = await inQ('insideB');
  R('B8 a stray mob 12 blocks outside was pulled back inside', a && a.x <= b.x1 && a.x >= b.x0, a ? `(x ${a.x.toFixed(1)}, edge ${b.x1})` : '(mob gone)');
  R('B9 a mob already inside is not moved', c && Math.abs(c.x - (cx + 3)) < 1.5 && Math.abs(c.z - (cz + 3)) < 1.5, c ? `(${c.x.toFixed(1)},${c.z.toFixed(1)} vs ${cx + 3},${cz + 3})` : '(gone)');
  await ask('/kill @e[tag=strayA]', 300); await ask('/kill @e[tag=insideB]', 300);
  // leave: nothing enforced any more
  await ask('/expedition leave', 1500);
  await tp(b.x1 + 40, y + 1, cz); await sleep(2500);
  const pl = await pos();
  R('B10 after leaving, the player is NOT pulled back', pl.x > b.x1 + 30, `(x ${pl.x.toFixed(1)}, old edge ${b.x1})`);
  bot.quit(); setTimeout(() => process.exit(0), 400);
});
bot.on('error', e => console.log('ERR', e.message));
