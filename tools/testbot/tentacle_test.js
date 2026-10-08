// Kraken stage 2: the tentacles are connected arms rooted on the head. Every part is read by its own tag off the live server.
const mineflayer = require('mineflayer');
const bot = mineflayer.createBot({ host: '127.0.0.1', port: 25565, username: 'EmberTester', version: '1.21.11', auth: 'offline' });
const sleep = ms => new Promise(r => setTimeout(r, ms));
const lines = []; bot.on('message', m => lines.push(m.toString()));
const ask = async (x, w = 500) => { const n = lines.length; bot.chat(x); await sleep(w); return lines.slice(n).join(' | '); };
const res = []; const check = (n, ok, note) => { res.push(ok); console.log((ok ? 'PASS ' : 'FAIL ') + n + ' ' + note); };
const G = '@e[type=emberfall:ember_guardian,limit=1]';
const vec = /\[(-?[\d.]+)d, (-?[\d.]+)d, (-?[\d.]+)d\]/;
const posOf = async sel => { for (let k = 0; k < 4; k++) { const r = await ask(`/data get entity ${sel} Pos`, 300); const m = vec.exec(r); if (m) return [+m[1], +m[2], +m[3]]; } return null; };
const dist = (a, b) => Math.hypot(a[0] - b[0], a[1] - b[1], a[2] - b[2]);
const LINKS = 8, TENT = 4, SPACING = 0.95, ROOT_MAX = 2.6;
const readAll = async () => {
  const grid = [];
  for (let t = 0; t < TENT; t++) { grid.push([]); for (let i = 0; i < LINKS; i++) grid[t].push(await posOf(`@e[type=minecraft:item_display,tag=emberfall_tentacle_${t}_${i},limit=1]`)); }
  return grid;
};
bot.once('spawn', async () => {
  await sleep(6500);
  await ask('/kill @e[type=!player]', 600); await ask('/kill @e[type=minecraft:item_display]', 600); await sleep(1500);
  await ask('/gamemode survival'); await ask('/effect give @s minecraft:resistance 999 4 true', 300); await ask('/effect give @s minecraft:regeneration 999 4 true', 300);
  await ask('/character select juggernaut', 600); await ask('/expedition', 1500);
  // The map builds async (about 32 s). Poll the dimension so the checks below run in the RUN, not in the hub, and assert it (R0).
  let inRun = false; for (let i = 0; i < 80 && !inRun; i++) { await sleep(1500); inRun = /expedition/.test(await ask('/data get entity @s Dimension', 400)); }
  await sleep(1500);
  check('R0 the bot is inside a run (nothing below means anything in the hub)', inRun, `inRun=${inRun}`);
  console.log('boss:', (await ask('/emberfall boss 0', 1200)).slice(0, 80)); await sleep(2500);
  await ask('/emberfall wavestop 0', 400);
  await ask('/kill @e[type=!player,type=!emberfall:ember_guardian,type=!minecraft:item_display]', 600);
  await ask('/tick freeze', 400);   // a moving chain read part by part smears: freeze, read, unfreeze
  const head = await posOf(G);
  check('K0 the boss has a position', !!head, JSON.stringify(head));
  const g1 = await readAll();
  const missing = g1.flat().filter(p => !p).length;
  check('K1 all 32 tentacle parts are found by their own tag', missing === 0, `missing=${missing}`);
  let worst = 0, rootWorst = 0, reachOver = 0;
  for (let t = 0; t < TENT; t++) {
    for (let i = 1; i < LINKS; i++) if (g1[t][i] && g1[t][i - 1]) worst = Math.max(worst, Math.abs(dist(g1[t][i], g1[t][i - 1]) - SPACING));
    if (g1[t][0]) rootWorst = Math.max(rootWorst, dist(g1[t][0], [head[0], head[1] + 1.3, head[2]]));
    if (g1[t][0] && g1[t][LINKS - 1]) reachOver = Math.max(reachOver, dist(g1[t][0], g1[t][LINKS - 1]) - SPACING * (LINKS - 1));
  }
  check('K2 every link is one spacing (0.95) from its neighbour', worst < 0.06, `worst error=${worst.toFixed(3)}`);
  check('K3 every tentacle root sits on the head', rootWorst < ROOT_MAX, `farthest root=${rootWorst.toFixed(2)}`);
  check('K4 no arm is longer than its links allow', reachOver < 0.06, `over=${reachOver.toFixed(3)}`);
  await ask('/tick unfreeze', 300); await sleep(4000);
  await ask('/tick freeze', 400);
  const g2 = await readAll();
  let moved = 0; for (let t = 0; t < TENT; t++) if (g1[t][LINKS - 1] && g2[t][LINKS - 1]) moved = Math.max(moved, dist(g1[t][LINKS - 1], g2[t][LINKS - 1]));
  check('K5 the tips sway on their own (idle motion)', moved > 0.3, `largest tip move in 4 s=${moved.toFixed(2)}`);
  let tipFloor = 1e9; for (let t = 0; t < TENT; t++) if (g2[t][LINKS - 1]) tipFloor = Math.min(tipFloor, g2[t][LINKS - 1][1] - head[1]);
  check('K6 no tip hangs far below the boss (nothing underground)', tipFloor > -1.5, `lowest tip relative to the boss feet=${tipFloor.toFixed(2)}`);
  await ask('/tick unfreeze', 300);
  console.log(res.every(Boolean) ? 'ALL PASS' : 'SOME FAIL');
  bot.quit(); setTimeout(() => process.exit(0), 300);
});
bot.on('error', e => console.log('ERR', e.message));
