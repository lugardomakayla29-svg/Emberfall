// EmberTester step 4: a bot in a run WALKS toward a hostile through the game's own pathfinder, and the path scout is
// invisible to the game's enemy rules. Controls: an idle bot outside any run does not move; a run with NO foe.
const mineflayer = require('mineflayer');
const sleep = ms => new Promise(r => setTimeout(r, ms));
const mk = name => new Promise(res => { const b = mineflayer.createBot({ host: '127.0.0.1', port: 25565, username: name, version: '1.21.11', auth: 'offline' }); b.chat_ = []; b.on('message', m => b.chat_.push(m.toString())); b.once('spawn', () => res(b)); });
let fails = 0; const check = (n, ok, note = '') => { console.log((ok ? 'PASS ' : 'FAIL ') + n + ' ' + note); if (!ok) fails++; };
(async () => {
  const op = await mk('EmberTester'); await sleep(4000);
  const say = async (cmd, w = 900) => { op.chat_.length = 0; op.chat(cmd); await sleep(w); return op.chat_.join(' | '); };
  const state = async n => (await say(`/emberfall bot state ${n}`, 700)).replace(/.*BOT state /, '');
  const posOf = s => { const m = /pos=(-?[\d.]+),(-?[\d.]+)/.exec(s); return m ? { x: +m[1], z: +m[2] } : null; };
  const num = (s, k) => { const m = new RegExp(k + '=(\\d+)').exec(s); return m ? +m[1] : NaN; };
  await say('/gamemode creative', 300);
  await say('/emberfall bot spawn WalkBot', 2000);
  await say('/emberfall bot spawn IdleBot', 2000);
  const idle0 = posOf(await state('IdleBot')); await sleep(6000); const idle1 = posOf(await state('IdleBot'));
  check('W5 control: a bot outside any run does not move', idle0 && idle1 && Math.hypot(idle1.x - idle0.x, idle1.z - idle0.z) < 0.01, JSON.stringify([idle0, idle1]));
  await say('/effect give @a[name=WalkBot] minecraft:resistance 999 4 true', 300);
  await say('/effect give @a[name=WalkBot] minecraft:regeneration 999 4 true', 300);
  const run = await say('/emberfall bot run WalkBot ranger', 1500);
  check('W0 the bot starts a run', /BOT run WalkBot ok/.test(run), run.slice(0, 100));
  let s = '';
  for (let i = 0; i < 70; i++) { await sleep(2000); s = await state('WalkBot'); if (/run=\d/.test(s) && /weapons=\w/.test(s)) break; }
  check('W0b in a run with a weapon', /run=\d/.test(s) && /weapons=\w/.test(s), s.slice(0, 100));
  // stop the wave director from adding its own mobs, so the only foe is the one placed here
  await say('/emberfall wavestop 0', 500);
  await sleep(8000);
  const quiet = await state('WalkBot');
  const q0 = posOf(quiet);
  check('W6 control: with no foe in sight the bot still has a scout and sees 0 foes', num(quiet, 'scouts') <= 1 && num(quiet, 'foes') === 0, quiet.slice(0, 200));
  // place one zombie 30 blocks east of the bot IN THE BOT'S OWN DIMENSION (as WalkBot at @s), held still with zero speed
  await say('/execute as WalkBot at @s run summon minecraft:zombie ~30 ~ ~ {Tags:["walk_target"],attributes:[{id:"minecraft:movement_speed",base:0.0}],PersistenceRequired:1b}', 800);
  await sleep(1500);
  const d = async () => { const st = await state('WalkBot'); const p = posOf(st); const z = await say('/execute as WalkBot at @s run data get entity @e[tag=walk_target,limit=1,distance=..200] Pos', 600); const m = /\[(-?[\d.]+)d, (-?[\d.]+)d, (-?[\d.]+)d\]/.exec(z); return { st, p, foe: m ? { x: +m[1], z: +m[3] } : null }; };
  const a = await d();
  check('W1a a foe is in sight and counted (the scout is not)', num(a.st, 'foes') === 1 && num(a.st, 'scouts') === 1, a.st.slice(0, 220));
  const startDist = a.foe ? Math.hypot(a.foe.x - a.p.x, a.foe.z - a.p.z) : NaN;
  await sleep(9000);
  const b = await d();
  if (!b.foe) { console.log('FAIL the target zombie vanished before the walk ' + b.st.slice(0, 160)); process.exit(1); }
  const midDist = Math.hypot(b.foe.x - b.p.x, b.foe.z - b.p.z);
  check('W1 the bot walked toward the foe (distance shrank by more than 10 blocks)', startDist - midDist > 10, `start ${startDist.toFixed(1)} now ${midDist.toFixed(1)}`);
  await sleep(14000);
  const c = await d();
  const endDist = c.foe ? Math.hypot(c.foe.x - c.p.x, c.foe.z - c.p.z) : 0;
  check('W2 it stopped near the foe: within 6 blocks and not on top of it (>= 1.0)', c.foe ? endDist <= 6.0 && endDist >= 1.0 : /foes=0/.test(c.st), `end ${endDist.toFixed(1)} ${c.st.slice(0, 160)}`);
  const w = await state('WalkBot');
  check('W3 exactly one scout exists however long the bot walked', num(w, 'scouts') === 1, w.slice(0, 200));
  await say('/emberfall bot remove WalkBot', 1500); await say('/emberfall bot remove IdleBot', 1500);
  console.log(fails === 0 ? 'ALL PASS' : 'SOME FAIL ' + fails);
  op.quit(); setTimeout(() => process.exit(0), 400);
})();
async function yOf(say, n) { const r = await say(`/data get entity ${n} Pos`, 600); const m = /\[(-?[\d.]+)d, (-?[\d.]+)d, (-?[\d.]+)d\]/.exec(r); return m ? m[2] : '70'; }
