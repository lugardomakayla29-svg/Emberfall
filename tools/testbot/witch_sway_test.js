// Measures the witch movement bands against a pinned, resistant player: beyond 14 she closes in, inside 9 she backs off,
// in 9..14 she sways sideways (movement perpendicular to the line to the player) and flips direction, keeping her range.
const mineflayer = require('mineflayer');
const sleep = ms => new Promise(r => setTimeout(r, ms));
const bot = mineflayer.createBot({ host: '127.0.0.1', port: 25565, username: 'EmberTester', version: '1.21.11', auth: 'offline' });
const chat = []; bot.on('message', m => chat.push(m.toString())); bot.on('error', e => console.log('ERR', e.message));
const ask = async (c, w = 500) => { chat.length = 0; bot.chat(c); await sleep(w); return chat.join(' | '); };
let fails = 0; const check = (n, ok, d = '') => { console.log((ok ? 'PASS ' : 'FAIL ') + n + (d ? '  ' + d : '')); if (!ok) fails++; };
const posOf = async sel => { const r = await ask(`/data get entity ${sel} Pos`, 450); const m = [...r.matchAll(/(-?\d+\.\d+)d/g)].map(x => parseFloat(x[1])); return m.length >= 3 ? m.slice(0, 3) : null; };
const me = () => { const p = bot.entity.position; return [p.x, p.y, p.z]; };
async function place(dist) {       // witch dist blocks east of the player, at the player's height
  await ask('/kill @e[type=emberfall:horde_witch]', 400);
  await ask('/execute at @s run emberfall spawnveteran horde_witch', 900);
  await ask('/tag @e[type=emberfall:horde_witch,limit=1] add mine', 250);
  await ask(`/execute at @s run tp @e[tag=mine,limit=1] ~${dist} ~ ~`, 500);
}
async function track(secs) {       // sample witch position every ~400ms
  const out = []; const t0 = Date.now();
  while (Date.now() - t0 < secs * 1000) { const w = await posOf('@e[tag=mine,limit=1]'); if (w) out.push({ t: Date.now() - t0, w, p: me() }); await sleep(100); }
  return out;
}
const dist = (a, b) => Math.hypot(a[0] - b[0], a[2] - b[2]);
bot.once('spawn', async () => {
  await sleep(4000); await ask('/op EmberTester');
  await ask('/gamemode survival'); await ask('/effect clear @s');
  await ask('/character select battlemage'); await ask('/expedition leave', 800); await ask('/expedition', 3500);
  for (let i = 0; i < 40; i++) { await sleep(1500); if (/expedition/.test(await ask('/data get entity @s Dimension', 400))) break; }
  await sleep(2000);
  await ask('/effect give @s minecraft:resistance 999 4 true'); await ask('/effect give @s minecraft:regeneration 999 4 true');
  await ask('/emberfall wavestop 0', 300); await ask('/kill @e[type=!player,distance=..90]', 900);
  const pin = setInterval(() => { const p = bot.entity.position; bot.chat(`/tp @s ${p.x.toFixed(2)} ${p.y.toFixed(2)} ${p.z.toFixed(2)}`); }, 1500);

  // FAR: starts 20 away, must come closer
  await place(20); let s = await track(6);
  const d0 = s.length ? dist(s[0].w, s[0].p) : NaN, d1 = s.length ? dist(s[s.length - 1].w, s[s.length - 1].p) : NaN;
  check('W1 far witch closes in', s.length >= 6 && d1 < d0 - 2, `start ${d0.toFixed(1)} end ${d1.toFixed(1)} samples ${s.length}`);

  // NEAR: starts 5 away, must back off
  await place(5); s = await track(5);
  const n0 = s.length ? dist(s[0].w, s[0].p) : NaN, n1 = s.length ? dist(s[s.length - 1].w, s[s.length - 1].p) : NaN;
  check('W2 close witch backs away', s.length >= 5 && n1 > n0 + 1.5, `start ${n0.toFixed(1)} end ${n1.toFixed(1)} samples ${s.length}`);

  // BAND: starts 11.5 away, must sway sideways and keep roughly her range
  await place(11.5); s = await track(10);
  let lat = 0, radial = 0, flips = 0, lastSign = 0;
  for (let i = 1; i < s.length; i++) {
    const a = s[i - 1], b = s[i];
    const ux = b.p[0] - b.w[0], uz = b.p[2] - b.w[2]; const L = Math.hypot(ux, uz) || 1;      // unit vector witch to player
    const mx = b.w[0] - a.w[0], mz = b.w[2] - a.w[2];
    const rad = (mx * ux + mz * uz) / L, side = (-mx * uz + mz * ux) / L;
    radial += Math.abs(rad); lat += Math.abs(side);
    if (Math.abs(side) > 0.05) { const sg = Math.sign(side); if (lastSign && sg !== lastSign) flips++; lastSign = sg; }
  }
  const ds = s.map(x => dist(x.w, x.p)); const dmin = Math.min(...ds), dmax = Math.max(...ds);
  // Radial travel includes the deliberate range corrections, so 'lateral beats radial' punished the fix that keeps her in band.
  // Judge sway by substantial sideways travel (idle = under 1 block; the first broken version measured 0.7).
  check('W3 sways: at least 8 blocks of sideways travel in 10 s', s.length >= 8 && lat >= 8.0, `lateral ${lat.toFixed(1)} radial ${radial.toFixed(1)} samples ${s.length}`);
  check('W4 stays in her range band while swaying', dmin > 7.5 && dmax < 15.5, `range ${dmin.toFixed(1)}..${dmax.toFixed(1)}`);
  check('W5 changes sway direction', flips >= 1, `${flips} flips in 10 s`);
  clearInterval(pin);
  console.log(fails === 0 ? 'ALL PASS' : 'SOME FAIL ' + fails);
  bot.quit(); setTimeout(() => process.exit(0), 300);
});
