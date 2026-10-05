// End to end: /expedition from the overworld builds the static map, teleports the player in, starts waves; leaving sends them home.
const mineflayer = require('mineflayer');
const bot = mineflayer.createBot({ host: '127.0.0.1', port: 25565, username: 'EmberTester', version: '1.21.11', auth: 'offline' });
const sleep = ms => new Promise(r => setTimeout(r, ms));
const lines = []; bot.on('message', m => lines.push(m.toString())); bot.on('error', e => console.log('ERR', e.message));
const ask = async (x, w = 500) => { const n = lines.length; bot.chat(x); await sleep(w); return lines.slice(n).join(' | '); };
let fails = 0; const check = (n, ok, note = '') => { console.log((ok ? 'PASS ' : 'FAIL ') + n + ' ' + note); if (!ok) fails++; };
const dim = async () => { const r = await ask('/data get entity @s Dimension', 500); const m = /"?([a-z_]+:[a-z_]+)"?\s*$/.exec(r.trim()) || /Dimension: "([^"]+)"/.exec(r); return m ? m[1] : r.slice(-60); };
const pos = async () => { const r = await ask('/data get entity @s Pos', 500); const m = /\[(-?[\d.]+)d, (-?[\d.]+)d, (-?[\d.]+)d\]/.exec(r); return m ? m.slice(1, 4).map(Number) : null; };
const count = async sel => {
  await ask('/scoreboard objectives add mrt dummy', 200); await ask('/scoreboard players set #n mrt -1', 200);
  await ask(`/execute store result score #n mrt if entity ${sel}`, 300);
  const r = await ask('/scoreboard players get #n mrt', 500); const m = /has (-?\d+) \[mrt\]/.exec(r); return m ? +m[1] : NaN; };
bot.once('spawn', async () => {
  await sleep(6500);
  await ask('/gamemode survival'); await ask('/effect give @s minecraft:resistance 999 4 true', 300); await ask('/effect give @s minecraft:regeneration 999 4 true', 300);
  await ask('/tp @s 123 80 -57', 800); await sleep(3500);
  const start = await pos(); const d0 = await dim(); console.log('start', start, d0);
  check('R0 starts in the overworld', /overworld/.test(d0), d0);
  await ask('/character select juggernaut', 600);
  const t0 = Date.now(); let r = await ask('/expedition', 1500); console.log('reply:', r.slice(0, 160));
  check('R1 told to wait while the map builds', /Preparing the expedition grounds/.test(r), r.slice(0, 80));
  let d = ''; for (let i = 0; i < 60; i++) { await sleep(2000); d = await dim(); if (/expedition/.test(d)) break; }
  console.log('seconds until inside', ((Date.now() - t0) / 1000).toFixed(1));
  check('R2 player is now in the expedition dimension', /expedition/.test(d), d);
  await sleep(1500);
  const p = await pos(); console.log('inside pos', p);
  check('R3 player stands at the map entry (0, ?, 0), on the surface', p && Math.abs(p[0] - 0.5) < 2 && Math.abs(p[2] - 0.5) < 2 && p[1] >= 64.9 && p[1] <= 66.1, JSON.stringify(p));
  await sleep(9000);
  const mobs = await count('@e[type=!player,type=!minecraft:item_display,type=!minecraft:interaction]');
  check('R4 the wave director is spawning mobs on the map', mobs > 0, `mobs=${mobs}`);
  const far = await ask('/execute as @e[type=!player,type=!minecraft:item_display,type=!minecraft:interaction,limit=1,sort=random] run data get entity @s Pos', 600);
  console.log('a mob at', far.slice(0, 120));
  const slotInfo = await ask('/emberfall mapreport', 500); console.log(slotInfo.slice(0, 200));
  check('R5 a repeat /expedition is refused', /already on an expedition/.test(await ask('/expedition', 800)));
  await ask('/expedition leave', 1500); await sleep(1500);
  const d2 = await dim(); const p2 = await pos(); console.log('after leave', d2, p2);
  check('R6 leaving returns to the overworld', /overworld/.test(d2), d2);
  check('R7 returned to the exact spot they left from', p2 && Math.abs(p2[0] - start[0]) < 1.5 && Math.abs(p2[2] - start[2]) < 1.5 && Math.abs(p2[1] - start[1]) < 1.5, JSON.stringify([start, p2]));
  // second run: the map is already built, so it must start at once
  const t1 = Date.now(); r = await ask('/expedition', 1500);
  check('R8 second run does NOT rebuild (no wait message)', !/Preparing the expedition grounds/.test(r), r.slice(0, 80));
  let d3 = ''; for (let i = 0; i < 10; i++) { await sleep(1000); d3 = await dim(); if (/expedition/.test(d3)) break; }
  check('R9 second run is inside within 8 seconds', /expedition/.test(d3) && (Date.now() - t1) < 9000, ((Date.now() - t1) / 1000).toFixed(1) + 's');
  // the map must be intact after run one ended: sample blocks
  const okG = /Test passed/.test(await ask('/execute in emberfall:expedition if block 30 65 -44 minecraft:grass_block', 600)) || /Test passed/.test(await ask('/execute in emberfall:expedition if block 30 64 -44 minecraft:grass_block', 600));
  check('R10 map survived the first run (grass still there)', okG);
  await ask('/expedition leave', 1200);
  console.log(fails === 0 ? 'ALL PASS' : 'SOME FAIL ' + fails); bot.quit(); setTimeout(() => process.exit(0), 400);
});
