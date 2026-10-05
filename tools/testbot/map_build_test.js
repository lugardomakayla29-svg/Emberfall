// Builds slot 0 of the static map in the real game, then compares the WORLD against the data file block by block
// (sampled). Expected blocks are recomputed here from expedition_map.json, independent of the Java builder.
const mineflayer = require('mineflayer'); const fs = require('fs');
const D = JSON.parse(fs.readFileSync('../mod/src/main/resources/data/emberfall/map/expedition_map.json', 'utf8'));
const sleep = ms => new Promise(r => setTimeout(r, ms));
const bot = mineflayer.createBot({ host: '127.0.0.1', port: 25565, username: 'EmberTester', version: '1.21.11', auth: 'offline' });
const chat = []; bot.on('message', m => chat.push(m.toString())); bot.on('error', e => console.log('ERR', e.message)); bot.on('kicked', r => console.log('KICKED', JSON.stringify(r).slice(0, 200))); bot.on('end', r => console.log('BOT_END', r));
const ask = async (c, w = 600) => { chat.length = 0; bot.chat(c); await sleep(w); return chat.join(' | '); };
let fails = 0; const check = (l, ok, x = '') => { console.log((ok ? 'PASS ' : 'FAIL ') + l + (x ? '  ' + x : '')); if (!ok) fails++; };
const OX = 0, OY = 64, OZ = 0; // slot 0 origin
const N = 201, R = 100;
const H = (x, z) => D.heights[(x + R) * N + (z + R)];
// normalise 'minecraft:oak_stairs[facing=south,half=bottom]' for /execute if block (same syntax, keep as is)
const atOnce = async (x, y, z, id, w) => /Test passed/i.test(await ask(`/execute in emberfall:expedition if block ${OX + x} ${OY + y} ${OZ + z} ${id}`, w));
// a reply can land after the wait under load; a block that is truly absent fails BOTH tries, so a retry cannot hide a real miss
let slowHits = 0;
const at = async (x, y, z, id) => { if (await atOnce(x, y, z, id, 180)) return true; const second = await atOnce(x, y, z, id, 700); if (second) { slowHits++; return true; }
  const third = await atOnce(x, y, z, id, 2500); if (third) slowHits++; return third; };
// "this cell is NOT air": ask it positively with `unless block ... air` so a correct answer is a 'Test passed' and the late-reply retry only
// costs time on a real miss (asking 'is it air' and expecting no made every CORRECT answer burn the whole 3.4 s retry chain)
const notAirOnce = async (x, y, z, w) => /Test passed/i.test(await ask(`/execute in emberfall:expedition unless block ${OX + x} ${OY + y} ${OZ + z} minecraft:air`, w));
const notAir = async (x, y, z) => { if (await notAirOnce(x, y, z, 180)) return true; if (await notAirOnce(x, y, z, 700)) { slowHits++; return true; } const t = await notAirOnce(x, y, z, 2500); if (t) slowHits++; return t; };
let seed = 12345; const rnd = () => (seed = (seed * 1103515245 + 12345) & 0x7fffffff) / 0x7fffffff;
bot.once('spawn', async () => {
  await sleep(5000);
  await ask('/gamemode creative'); await ask('/op EmberTester');
  const t0 = Date.now();
  let r = await ask('/emberfall mapbuild 0', 1500); console.log('start:', r.slice(0, 120));
  let done = null;
  for (let i = 0; i < 120 && !done; i++) { await sleep(2000); const m = chat.find(l => /MAPDONE/.test(l)); if (m) done = m; }
  check('M0 build finished', !!done, done ? done.slice(0, 200) : 'timeout');
  console.log('wall seconds', ((Date.now() - t0) / 1000).toFixed(1));
  if (!done) { bot.quit(); return setTimeout(() => process.exit(1), 300); }
  await sleep(1500);
  // chunks are released after the build; load them again for /execute to see blocks
  await ask('/execute in emberfall:expedition run forceload add -100 -100 100 100', 3000);
  // 1. terrain: 120 random floor columns, check grass at height h, dirt just under, and air above the surface
  let ok1 = 0, n1 = 0, bad1 = [];
  for (let i = 0; i < 120; i++) {
    const a = rnd() * Math.PI * 2, d = Math.sqrt(rnd()) * 94; const x = Math.round(Math.cos(a) * d), z = Math.round(Math.sin(a) * d);
    const h = H(x, z); n1++;
    const g = await at(x, h, z, 'minecraft:grass_block'), b = await at(x, h - 1, z, 'minecraft:dirt');
    if (g && b) ok1++; else bad1.push([x, z, h, g, b]);
  }
  check('M1 terrain columns match (grass top at height, dirt under)', ok1 === n1, `${ok1}/${n1} ${JSON.stringify(bad1.slice(0, 3))}`);
  // 2. base: stone at y -6 and -2 in 30 columns, including near the rim
  let ok2 = 0; for (let i = 0; i < 30; i++) { const a = rnd() * Math.PI * 2, d = Math.sqrt(rnd()) * 99; const x = Math.round(Math.cos(a) * d), z = Math.round(Math.sin(a) * d); if (await at(x, -6, z, 'minecraft:stone') && await at(x, -2, z, 'minecraft:stone')) ok2++; }
  check('M2 base is solid stone 6 deep across the disc', ok2 === 30, `${ok2}/30`);
  // 3. outside the disc is empty
  check('M3 nothing outside the circle', await at(101, -3, 0, 'minecraft:air') && await at(72, -3, 72, 'minecraft:air'));
  // 4. wall: 40 columns at radius 97.5; filled up to top-1, air at top
  let ok4 = 0, bad4 = []; const arc = D.wallTops.length;
  for (let i = 0; i < 40; i++) {
    const ang = (i / 40) * Math.PI * 2 + 0.013, x = Math.round(Math.cos(ang) * 97.5), z = Math.round(Math.sin(ang) * 97.5);
    let a2 = Math.atan2(z, x); if (a2 < 0) a2 += Math.PI * 2; const top = D.wallTops[Math.floor(a2 / (2 * Math.PI) * arc) % arc];
    const solid = await notAir(x, top - 1, z), empty = await at(x, top, z, 'minecraft:air'), low = await notAir(x, 0, z);
    if (solid && empty && low) ok4++; else bad4.push([x, z, top, solid, empty, low]);
  }
  check('M4 wall filled to its top and open above', ok4 === 40, `${ok4}/40 ${JSON.stringify(bad4.slice(0, 2))}`);
  // 5. inner wall face is vertical: at every sampled bearing the cell 1 inside the floor edge is free air up to 15
  let ok5 = 0; for (let i = 0; i < 24; i++) { const ang = (i / 24) * Math.PI * 2, x = Math.round(Math.cos(ang) * 93), z = Math.round(Math.sin(ang) * 93); const g5 = H(x, z); if (await at(x, 10, z, 'minecraft:air') && await at(x, g5 + 1, z, 'minecraft:air') && await at(x, g5, z, 'minecraft:grass_block')) ok5++; }
  check('M5 open air just inside the wall', ok5 === 24, `${ok5}/24`);
  // 6. shrines: every schematic block is in the world (sample all of them, they are small)
  for (const s of D.shrines) {
    const w = s.size[0], l = s.size[2], x0 = s.x - Math.floor(w / 2), z0 = s.z - Math.floor(l / 2); let ok = 0;
    for (const b of s.blocks) if (await at(x0 + b[0], 1 + b[1], z0 + b[2], b[3])) ok++;
    check(`M6 shrine ${s.type}: all ${s.blocks.length} blocks placed exactly`, ok === s.blocks.length, `${ok}/${s.blocks.length}`);
  }
  // 7. houses: EVERY block of each real vanilla building, exact state (houseBlocks is keyed by house name)
  for (const h of D.houses) {
    const B = D.houseBlocks[h[0]]; let ok = 0, n = 0; const bad = [];
    for (const b of B) { n++; if (await at(h[1] + b[0], 1 + b[1], h[2] + b[2], b[3])) ok++; else bad.push(b.join(' ')); }
    check(`M7 ${h[0]}: all ${n} blocks placed exactly`, ok === n, `${ok}/${n} ${JSON.stringify(bad.slice(0, 3))}`);
  }
  // 8. trees and boulders: trunk base + a leaf, rock centre
  let okT = 0; for (let i = 0; i < D.trees.length; i++) { const [tx, tz] = D.trees[i]; if (await at(tx, 1, tz, 'minecraft:oak_log[axis=y]') && await at(tx, 1 + (i % 2 ? 7 : 5) - 1, tz, 'minecraft:oak_log[axis=y]')) okT++; }
  check('M8 every tree trunk stands', okT === D.trees.length, `${okT}/${D.trees.length}`);
  let okB = 0; for (const [bx, bz, sz] of D.boulders) { const B = D.boulderBlocks[String(sz)]; const c = B.find(b => b[0] === 0 && b[1] === 0 && b[2] === 0); if (await at(bx, 1, bz, c[3])) okB++; }
  check('M9 every boulder centre stands', okB === D.boulders.length, `${okB}/${D.boulders.length}`);
  console.log('probes that needed the retry (late reply):', slowHits);
  console.log(fails === 0 ? 'ALL PASS' : 'SOME FAIL ' + fails);
  bot.quit(); setTimeout(() => process.exit(0), 400);
});
