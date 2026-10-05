// Reaper's Rite at level 10 (Reaper, Spectral Sickles): 8 tough foes spread 6 to 13 blocks out, outside the ordinary 3.6 ring, inside the 14 block
// gather range. Foes have AI but are rooted by Slowness (NoAI foes ignore pulls), so they can only go where the gather drags them. Meter filled LAST.
// Prints each foe's distance from the player before and after, and hp lost; the server trace RITE_TEST reports cuts, pulled, foes, maxInDisc, shatterHit.
// Run twice: normally, and with -Demberfall.noRite=true as the control (same scene, no ultimate).
const mineflayer = require('mineflayer');
const bot = mineflayer.createBot({ host: '127.0.0.1', port: 25565, username: 'EmberTester', version: '1.21.11', auth: 'offline' });
const sleep = ms => new Promise(r => setTimeout(r, ms));
const lines = []; bot.on('message', m => lines.push(m.toString())); bot.on('error', e => console.log('ERR', e.message));
const ask = async (x, w = 450) => { const n = lines.length; bot.chat(x); await sleep(w); return lines.slice(n).join(' | '); };
const BIG = 1024;
const hp = async tag => { const r = await ask(`/data get entity @e[tag=${tag},limit=1] Health`, 380); const m = /entity data: (-?[\d.]+)f/.exec(r); return m ? +m[1] : null; };
const pos = async tag => { const r = await ask(`/data get entity @e[tag=${tag},limit=1] Pos`, 380); const m = /\[(-?[\d.]+)d, (-?[\d.]+)d, (-?[\d.]+)d\]/.exec(r); return m ? [+m[1], +m[2], +m[3]] : null; };
const foe = async (tag, dx, dz) => {
  await ask(`/execute at @s run summon emberfall:horde_zombie ~${dx} ~ ~${dz} {Tags:["${tag}","keep"],Silent:1b,PersistenceRequired:1b}`, 280);
  await ask(`/attribute @e[tag=${tag},limit=1] minecraft:max_health base set ${BIG}`, 100);
  await ask(`/data modify entity @e[tag=${tag},limit=1] Health set value ${BIG}.0f`, 100);
  await ask(`/effect give @e[tag=${tag},limit=1] minecraft:slowness 60 6 true`, 100);     // rooted: cannot walk, can still be dragged
};
const g = async (kills, m) => ask(`/emberfall debugweapongrowth EmberTester grant 0 ${kills} ${m}`, 400);
bot.once('spawn', async () => {
  await sleep(5000);
  await ask('/gamemode survival'); await ask('/effect clear @s');
  await ask('/character select reaper'); await ask('/expedition', 2500);
  for (let i = 0; i < 40; i++) { await sleep(2000); if (/expedition/.test(await ask('/data get entity @s Dimension', 400))) break; }
  await sleep(2000);
  await ask('/effect give @s minecraft:resistance 999 4 true', 200); await ask('/effect give @s minecraft:regeneration 999 4 true', 200);
  await ask('/emberfall wavestop 0', 300); await ask('/time set midnight', 200);
  await ask('/kill @e[type=!player,type=!minecraft:item_display,type=!minecraft:interaction]', 900);
  const bx = bot.entity.position.x, by = bot.entity.position.y, bz = bot.entity.position.z;
  const pin = setInterval(() => bot.chat(`/tp @s ${bx.toFixed(2)} ${by.toFixed(2)} ${bz.toFixed(2)} 0 0`), 700);
  await ask('/emberfall debugloadout EmberTester', 600);
  await g(108, 0);                                                     // level 10, meter EMPTY while the scene is built
  const spots = { a: [6, 0], b: [8, 4], c: [9, -4], d: [-7, 3], e: [10, 1], f: [0, 8], g: [-6, -9], h: [12, 5] };
  for (const [t, [dx, dz]] of Object.entries(spots)) await foe(t, dx, dz);
  const tags = Object.keys(spots);
  const dist = p => p ? Math.hypot(p[0] - bx, p[2] - bz) : NaN;
  const before = {}; for (const t of tags) before[t] = dist(await pos(t));
  const hp0 = {}; for (const t of tags) hp0[t] = await hp(t);
  console.log('BEFORE_DIST', JSON.stringify(Object.fromEntries(tags.map(t => [t, +before[t].toFixed(1)]))));
  await foe('trig', 1.2, 0);                                           // the foe the sickles cut to start the ultimate
  await g(0, 1000);                                                    // meter FULL as the last step: the next landed cut starts the Rite
  await sleep(1800);                                                   // gather is 1.5 s
  const mid = {}; for (const t of tags) mid[t] = await pos(t);
  // spread of the pile: mean distance between each foe and the group's centre
  const pts = tags.map(t => mid[t]).filter(Boolean);
  const cx = pts.reduce((a, p) => a + p[0], 0) / pts.length, cz = pts.reduce((a, p) => a + p[2], 0) / pts.length;
  const spread = pts.reduce((a, p) => a + Math.hypot(p[0] - cx, p[2] - cz), 0) / pts.length;
  const spread0 = (() => { const b = tags.map(t => spots[t]); const mx = b.reduce((a, p) => a + p[0], 0) / b.length, mz = b.reduce((a, p) => a + p[1], 0) / b.length; return b.reduce((a, p) => a + Math.hypot(p[0] - mx, p[1] - mz), 0) / b.length; })();
  console.log('SPREAD before', spread0.toFixed(1), '-> after gather', spread.toFixed(1), '| foes seen', pts.length);
  await sleep(14000);                                                  // slice (10 s at level 10) plus the break
  const lost = {}; for (const t of tags) { const h = await hp(t); lost[t] = h === null ? BIG : +(hp0[t] - h).toFixed(1); }
  const vals = Object.values(lost);
  console.log('LOST', JSON.stringify(lost), 'total', vals.reduce((a, b) => a + b, 0).toFixed(1), 'foes hit', vals.filter(x => x > 0.5).length);
  clearInterval(pin); await ask('/expedition leave', 800);
  console.log('RITE_DONE'); bot.quit(); setTimeout(() => process.exit(0), 300);
});
