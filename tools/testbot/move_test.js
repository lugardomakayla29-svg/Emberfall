const mineflayer = require('mineflayer');
const bot = mineflayer.createBot({ host: '127.0.0.1', port: 25565, username: 'EmberTester', version: '1.21.11', auth: 'offline' });
const sleep = ms => new Promise(r => setTimeout(r, ms));
const lines = []; bot.on('message', m => lines.push(m.toString()));
const ask = async (x, w = 500) => { const n = lines.length; bot.chat(x); await sleep(w); return lines.slice(n).join(' | '); };
const res = []; const check = (n, ok, note) => { res.push(ok); console.log((ok ? 'PASS ' : 'FAIL ') + n + ' ' + note); };
const G = '@e[type=emberfall:ember_guardian,limit=1]';
const P = async sel => { for (let k = 0; k < 4; k++) { const r = await ask(`/data get entity ${sel} Pos`, 450); const m = /\[(-?[\d.]+)d, (-?[\d.]+)d, (-?[\d.]+)d\]/.exec(r); if (m) return [+m[1], +m[2], +m[3]]; } return null; };
const d2 = (a, b) => Math.hypot(a[0] - b[0], a[2] - b[2]);
bot.once('spawn', async () => {
  await sleep(6500);
  await ask('/kill @e[type=!player]', 600); await ask('/kill @e[type=minecraft:item_display]', 600); await sleep(1200);
  await ask('/gamemode survival'); await ask('/effect give @s minecraft:resistance 999 4 true', 250); await ask('/effect give @s minecraft:regeneration 999 4 true', 250);
  await ask('/character select juggernaut', 600); await ask('/expedition', 1500); await sleep(1500);
  await ask('/emberfall boss 0', 900); await ask('/emberfall wavestop 0', 300);
  await ask('/kill @e[type=!player,type=!emberfall:ember_guardian,type=!emberfall:cinder_pylon,type=!minecraft:item_display]', 500);
  // Put the player 15 blocks east of the boss, then sample boss and player every ~1s for 14s.
  await ask(`/execute as ${G} at @s run tp EmberTester ~15 ~ ~`, 300);
  const rows = []; const t0 = Date.now();
  for (let i = 0; i < 14; i++) { const b = await P(G); const p = await P('@s'); rows.push({ t: (Date.now() - t0) / 1000, b, p }); await sleep(250); }
  const first = rows[0], last = rows[rows.length - 1];
  console.log('gap over time:', rows.map(r => d2(r.b, r.p).toFixed(1)).join(' '));
  check('V1 it closes in to about the stop distance', d2(last.b, last.p) <= 6.5 && (d2(first.b, first.p) - d2(last.b, last.p) > 5 || (d2(first.b, first.p) < 11 && d2(first.b, first.p) - d2(last.b, last.p) > 3)), `gap ${d2(first.b, first.p).toFixed(1)} -> ${d2(last.b, last.p).toFixed(1)}`);
  // speed: use the longest run of samples where the gap was still shrinking
  // Speed WHILE WALKING: only intervals where the boss moved at least 0.2 blocks (it stands still once it arrives).
  let dist = 0, time = 0; for (let k = 1; k < rows.length; k++) { const dd = d2(rows[k].b, rows[k - 1].b); if (dd >= 0.2) { dist += dd; time += rows[k].t - rows[k - 1].t; } }
  const best = time > 0 ? dist / time : 0; console.log('walking intervals: ' + dist.toFixed(1) + ' blocks in ' + time.toFixed(1) + ' s');
  check('V2 average walking pace is sane (1.0 to 2.5 blocks/s)', best >= 1.0 && best <= 2.5, `peak ${best.toFixed(2)} blocks/s`);
  // V5: parts follow the brain
  const disp = await ask(`/execute as ${G} at @s if entity @e[type=minecraft:item_display,distance=..2.5]`, 500);
  check('V5 display parts stay with the moving brain', /Test passed/.test(disp), disp.slice(0, 80));
  // V4: after the pylons break it plants its feet
  await ask('/kill @e[type=emberfall:cinder_pylon]', 900); await sleep(500);
  await ask(`/execute as ${G} at @s run tp EmberTester ~12 ~ ~`, 300);
  const a = await P(G); await sleep(3000); const b2 = await P(G);
  check('V4 exposed boss stays planted (moves < 1 block in 3s)', d2(a, b2) < 1.0, `moved ${d2(a, b2).toFixed(2)}`);
  await ask('/kill @e[type=emberfall:ember_guardian]', 800); await sleep(2500);
  console.log(res.every(Boolean) ? 'ALL PASS' : 'SOME FAIL');
  bot.quit(); setTimeout(() => process.exit(0), 300);
});
bot.on('error', e => console.log('ERR', e.message));
