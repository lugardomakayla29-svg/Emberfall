// Direct cover proof: the real placePillar + the real line-of-sight check, against a flat patch, at several distances.
const mineflayer = require('mineflayer');
const bot = mineflayer.createBot({ host: '127.0.0.1', port: 25565, username: 'EmberTester', version: '1.21.11', auth: 'offline' });
const sleep = ms => new Promise(r => setTimeout(r, ms));
const lines = []; bot.on('message', m => lines.push(m.toString()));
const ask = async (x, w = 500) => { const n = lines.length; bot.chat(x); await sleep(w); return lines.slice(n).join(' | '); };
const G = '@e[type=emberfall:ember_guardian,limit=1]';
const num = async (sel, path) => { for (let k = 0; k < 4; k++) { const r = await ask(`/data get entity ${sel} ${path}`, 400); const m = /(-?[\d.]+)[fdb]?$/.exec(r.trim()); if (m) return +m[1]; } return NaN; };
const res = []; const check = (n, ok, note) => { res.push(ok); console.log((ok ? 'PASS ' : 'FAIL ') + n + ' ' + note); };
bot.once('spawn', async () => {
  await sleep(6500);
  await ask('/kill @e[type=!player]', 600); await ask('/kill @e[type=minecraft:item_display]', 600); await sleep(1200);
  await ask('/gamemode survival'); await ask('/effect give @s minecraft:resistance 999 4 true', 250); await ask('/effect give @s minecraft:regeneration 999 4 true', 250);
  await ask('/character select juggernaut', 600); await ask('/expedition', 1500); await sleep(1500);
  await ask('/emberfall boss 0', 900); await ask('/emberfall wavestop 0', 300);
  await ask(`/data modify entity ${G} NoAI set value 1b`, 400);
  const by = Math.floor(await num(G, 'Pos[1]'));
  await ask(`/execute as ${G} at @s run fill ~-3 ${by - 1} ~-3 ~24 ${by - 1} ~8 minecraft:stone`, 900);
  await ask(`/execute as ${G} at @s run fill ~-3 ${by} ~-3 ~24 ${by + 6} ~8 minecraft:air`, 900);
  for (const d of [6, 9, 12]) {
    await ask(`/execute as ${G} at @s run tp EmberTester ~${d} ${by} ~`, 400);
    const r = await ask('/emberfall coverprobe EmberTester', 700);
    const m = /COVER before=(\w+) placed=(\d+) after=(\w+) sideStep=(\w+) sideBefore=(\w+) removed=(\w+)/.exec(r);
    console.log(`d=${d}:`, m ? m[0] : r.slice(0, 120));
    if (!m) { check(`C${d} probe replied`, false, r.slice(0, 80)); continue; }
    check(`C${d}a open ground: not blocked before the pillar`, m[1] === 'false', `before=${m[1]}`);
    check(`C${d}b the pillar is placed (6 blocks)`, m[2] === '6', `placed=${m[2]}`);
    check(`C${d}c the pillar BLOCKS the line to the player`, m[3] === 'true', `after=${m[3]}`);
    check(`C${d}d the pillar does not change the sideways line (not a wide shield)`, m[4] === m[5], `sideBefore=${m[5]} sideAfter=${m[4]}`);
    check(`C${d}e the pillar is removed afterwards`, m[6] === 'true', `removed=${m[6]}`);
  }
  // Hill case: the boss stands 3 blocks ABOVE the player's floor, on a slope that steps down with no wall between them.
  // Rows of stone at descending heights: x+0..2 at by-1 (boss), x+3 at by-2, x+4 at by-3, x+5 onward at by-4 (player).
  // Everything above each column is cleared, so the only thing that can block the line is the pillar.
  await ask(`/execute as ${G} at @s run fill ~-3 ${by - 8} ~-3 ~26 ${by - 5} ~8 minecraft:stone`, 900);
  await ask(`/execute as ${G} at @s run fill ~-3 ${by - 4} ~-3 ~26 ${by + 6} ~8 minecraft:air`, 900);
  await ask(`/execute as ${G} at @s run fill ~-3 ${by - 4} ~-3 ~2 ${by - 1} ~8 minecraft:stone`, 900);
  await ask(`/execute as ${G} at @s run fill ~3 ${by - 4} ~-3 ~3 ${by - 2} ~8 minecraft:stone`, 900);
  await ask(`/execute as ${G} at @s run fill ~4 ${by - 4} ~-3 ~4 ${by - 3} ~8 minecraft:stone`, 900);
  await ask(`/execute as ${G} at @s run tp EmberTester ~10 ${by - 3} ~`, 500);
  const h = await ask('/emberfall coverprobe EmberTester', 700);
  const hm = /COVER before=(\w+) placed=(\d+) after=(\w+) sideStep=(\w+) sideBefore=(\w+) removed=(\w+)/.exec(h);
  console.log('hill d=10, player 3 below:', hm ? hm[0] : h.slice(0, 120));
  check('H0 hill: the line is open before the pillar', !!hm && hm[1] === 'false', hm ? `before=${hm[1]}` : 'no reply');
  check('H1 hill: the pillar is placed on the lower floor', !!hm && hm[2] === '6', hm ? `placed=${hm[2]}` : 'no reply');
  check('H2 hill: the pillar BLOCKS the line', !!hm && hm[3] === 'true', hm ? `after=${hm[3]}` : 'no reply');
  check('H3 hill: removed afterwards (line open again)', !!hm && hm[6] === 'true', hm ? `removed=${hm[6]}` : 'no reply');
  console.log(res.every(Boolean) ? 'ALL PASS' : 'SOME FAIL');
  bot.quit(); setTimeout(() => process.exit(0), 300);
});
bot.on('error', e => console.log('ERR', e.message));
