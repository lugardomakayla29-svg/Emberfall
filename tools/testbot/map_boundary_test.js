// Boundary on the STATIC map: PLAY_RADIUS 93. Positions are read from the SERVER. Judge: inside stays, just past is pulled back, far past is returned.
const mineflayer = require('mineflayer'); const sleep = ms => new Promise(r => setTimeout(r, ms));
const bot = mineflayer.createBot({ host: '127.0.0.1', port: 25565, username: 'EmberTester', version: '1.21.11', auth: 'offline' });
const lines = []; bot.on('message', m => lines.push(m.toString())); bot.on('error', e => console.log('ERR', e.message));
const ask = async (x, w = 500) => { const n = lines.length; bot.chat(x); await sleep(w); return lines.slice(n).join(' | '); };
let fails = 0; const check = (n, ok, note = '') => { console.log((ok ? 'PASS ' : 'FAIL ') + n + ' ' + note); if (!ok) fails++; };
const pos = async () => { for (let k = 0; k < 4; k++) { const r = await ask('/data get entity @s Pos', 500); const m = /\[(-?[\d.]+)d, (-?[\d.]+)d, (-?[\d.]+)d\]/.exec(r); if (m) return [+m[1], +m[2], +m[3]]; } return null; };
const radius = p => p ? Math.hypot(p[0], p[2]) : NaN;
const at = async (r) => { // stand at distance r along +x, 1 above the surface, then wait two seconds for the guard
  await ask(`/tp @s ${r} 80 0`, 1200); await sleep(2200); return await pos(); };
bot.once('spawn', async () => {
  await sleep(6500);
  await ask('/gamemode survival'); await ask('/effect give @s minecraft:resistance 999 4 true', 300); await ask('/effect give @s minecraft:regeneration 999 4 true', 300);
  await ask('/character select juggernaut', 600); await ask('/expedition', 1500);
  for (let i = 0; i < 60; i++) { await sleep(2000); if (/expedition/.test(await ask('/data get entity @s Dimension', 400))) break; }
  await sleep(1500); await ask('/emberfall wavestop 0', 500);
  const p90 = await at(90); check('B1 at radius 90 the player is left alone', Math.abs(radius(p90) - 90) < 1.5, 'r=' + radius(p90).toFixed(2));
  const p92 = await at(92.5); check('B2 at radius 92.5 (inside 93) the player is left alone', Math.abs(radius(p92) - 92.5) < 1.5, 'r=' + radius(p92).toFixed(2));
  // B3: a real player WALKING outward into the soft band. The server pushes with velocity (a bot standing still keeps sending its own
  // position, so a static bot cannot show it). Hold forward toward +x from 91 for 4 s: the player must never get past the hard limit.
  await ask('/tp @s 91 80 0 -90 0', 1500); await sleep(1500);
  bot.setControlState('forward', true); let maxR = 0; for (let k = 0; k < 16; k++) { await sleep(250); const q = bot.entity.position; maxR = Math.max(maxR, Math.hypot(q.x, q.z)); }
  bot.setControlState('forward', false); await sleep(800); const l95 = await pos();
  check('B3 walking outward into the soft band never passes the hard limit (96)', maxR <= 96.2 && radius(l95) <= 96, 'max r=' + maxR.toFixed(2) + ' final r=' + radius(l95).toFixed(2));
  const p99 = await at(99); check('B4 at radius 99 (past the hard limit) the player is put back at or under 93.5', radius(p99) <= 93.5, 'r=' + radius(p99).toFixed(2));
  const p110 = await at(110); check('B5 at radius 110 (on the wall) the player is put back inside', radius(p110) <= 93.5 && p110[1] < 70, 'r=' + radius(p110).toFixed(2) + ' y=' + (p110 ? p110[1].toFixed(1) : '?'));
  // ground under the edge: the floor must exist at 93 so the player is not left over a void
  const g = await ask('/execute in emberfall:expedition if block 92 1 0 minecraft:air', 500);
  const h = await ask('/execute in emberfall:expedition if block 92 65 0 minecraft:air', 500);
  const col = []; for (const y of [60, 62, 64, 65, 66, 70]) col.push(y + ':' + (/passed/.test(await ask(`/execute in emberfall:expedition if block 92 ${y} 0 minecraft:air`, 300)) ? 'air' : 'solid'));
  console.log('column at x=92, z=0:', col.join(' '));
  check('B6 solid ground exists at the play edge (some block under y 66 at x=92 is not air)', col.slice(0, 4).some(c => c.endsWith('solid')), col.join(' '));
  await ask('/expedition leave', 1200);
  console.log(fails === 0 ? 'ALL PASS' : 'SOME FAIL ' + fails); bot.quit(); setTimeout(() => process.exit(0), 400);
});
