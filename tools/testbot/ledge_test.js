// A 3x3 stone platform 2 blocks high; the player stands on it, the boss starts 10 blocks away on the ground.
// Step height 1.1 cannot climb 2 blocks, so the boss can only reach the player through the hop. Proof comes from the boss position and the HOPDBG log.
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
  const b0 = await P(G); const gy = Math.floor(b0[1]);
  // Platform 10 blocks east of the boss: a 5x5 flat-topped block 2 high, with the ground below flattened by it.
  const px = Math.floor(b0[0]) + 10, pz = Math.floor(b0[2]);
  await ask(`/fill ${px - 2} ${gy} ${pz - 2} ${px + 2} ${gy + 1} ${pz + 2} minecraft:stone`, 800);
  await ask(`/fill ${px - 2} ${gy + 2} ${pz - 2} ${px + 2} ${gy + 6} ${pz + 2} minecraft:air`, 800);
  await ask(`/tp @s ${px}.5 ${gy + 2} ${pz}.5`, 400);
  const start = d2(b0, [px, 0, pz]);
  // Keep the player on top of the platform for 25 seconds, sample the boss gap.
  const gaps = []; const t0 = Date.now();
  while (Date.now() - t0 < 25000) { await ask(`/tp @s ${px}.5 ${gy + 2} ${pz}.5`, 100); const b = await P(G); if (b) gaps.push(d2(b, [px + 0.5, 0, pz + 0.5])); await sleep(500); }
  console.log('gap to the platform centre over time:', gaps.map(g => g.toFixed(1)).join(' '));
  console.log('start gap', start.toFixed(1), 'closest', Math.min(...gaps).toFixed(1));
  check('L1 the boss reaches the foot of a 2 block platform', Math.min(...gaps) <= 5.5, `closest ${Math.min(...gaps).toFixed(1)} (platform edge is 2.5 from centre)`);
  await ask('/kill @e[type=emberfall:ember_guardian]', 800); await sleep(2000);
  console.log(res.every(Boolean) ? 'ALL PASS' : 'SOME FAIL');
  bot.quit(); setTimeout(() => process.exit(0), 300);
});
bot.on('error', e => console.log('ERR', e.message));
