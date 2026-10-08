// Kraken stage 2b: tentacles stay out of the ground, including over raised terrain built next to the boss.
const mineflayer = require('mineflayer');
const bot = mineflayer.createBot({ host: '127.0.0.1', port: 25565, username: 'EmberTester', version: '1.21.11', auth: 'offline' });
const sleep = ms => new Promise(r => setTimeout(r, ms));
const lines = []; bot.on('message', m => lines.push(m.toString()));
const ask = async (x, w = 500) => { const n = lines.length; bot.chat(x); await sleep(w); return lines.slice(n).join(' | '); };
const res = []; const check = (n, ok, note) => { res.push(ok); console.log((ok ? 'PASS ' : 'FAIL ') + n + ' ' + note); };
const G = '@e[type=emberfall:ember_guardian,limit=1]';
const vec = /\[(-?[\d.]+)d, (-?[\d.]+)d, (-?[\d.]+)d\]/;
const posOf = async sel => { for (let k = 0; k < 4; k++) { const r = await ask(`/data get entity ${sel} Pos`, 300); const m = vec.exec(r); if (m) return [+m[1], +m[2], +m[3]]; } return null; };
const LINKS = 8, TENT = 4;
// true when the block at the joint's position (block containing it) is NOT solid; asked positively

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
  const head = await posOf(G); check('F0 boss found', !!head, JSON.stringify(head));
  const by = Math.floor(head[1]);
  // a 3-high stone ridge 4 blocks east of the boss, 9 long, where idle tips reach (idle radius 5)
  await ask(`/fill ${Math.floor(head[0]) + 3} ${by} ${Math.floor(head[2]) - 5} ${Math.floor(head[0]) + 6} ${by + 2} ${Math.floor(head[2]) + 5} minecraft:stone`, 900);
  await sleep(2500);   // several idle sway cycles over the ridge
  await ask('/tick freeze', 400);
  let inside = 0, total = 0, lowestAboveFloor = 9;
  for (let t = 0; t < TENT; t++) for (let i = 1; i < LINKS; i++) {
    const p = await posOf(`@e[type=minecraft:item_display,tag=emberfall_tentacle_${t}_${i},limit=1]`);
    if (!p) continue; total++;
    const solid = await ask(`/execute if block ${Math.floor(p[0])} ${Math.floor(p[1])} ${Math.floor(p[2])} minecraft:stone run say INSIDE`, 220);
    if (/INSIDE/.test(solid)) inside++;
  }
  check('F1 every joint was read', total === TENT * (LINKS - 1), `read=${total}`);
  check('F2 no joint centre sits inside the stone ridge', inside === 0, `inside=${inside} of ${total}`);
  await ask('/tick unfreeze', 300);
  console.log(res.every(Boolean) ? 'ALL PASS' : 'SOME FAIL');
  bot.quit(); setTimeout(() => process.exit(0), 300);
});
bot.on('error', e => console.log('ERR', e.message));
