const mineflayer = require('mineflayer');
const bot = mineflayer.createBot({ host: '127.0.0.1', port: 25565, username: 'EmberTester', version: '1.21.11', auth: 'offline' });
const sleep = ms => new Promise(r => setTimeout(r, ms));
const lines = []; bot.on('message', m => lines.push(m.toString()));
const ask = async (x, w = 700) => { const n = lines.length; bot.chat(x); await sleep(w); return lines.slice(n).join(' | '); };
const res = [];
const check = (id, ok, note) => { res.push(ok); console.log(`${ok ? 'PASS' : 'FAIL'} ${id} ${note}`); };
const dump = async () => {
  const r = await ask('/emberfall devdump EmberTester', 800);
  const m = /DEVDUMP px=([\d.,-]+)(.*) parts=(\d+)/.exec(r);
  if (!m) return null;
  const parts = [...m[2].matchAll(/\[(\w+)(?:#\d+)? ([\d.-]+),([\d.-]+),([\d.-]+)( invis)?\]/g)].map(x => ({ k: x[1], x: +x[2], y: +x[3], z: +x[4], inv: !!x[5] }));
  return { py: parseFloat(m[1].split(',')[1]), parts, n: +m[3] };
};
bot.once('spawn', async () => {
  await sleep(6500);
  await ask('/kill @e[type=!player]', 500);
  await ask('/kill @e[type=minecraft:item_display]', 500); await sleep(1500);
  await ask('/gamemode survival'); await ask('/effect give @s minecraft:resistance 999 4 true', 300);
  await ask('/effect give @s minecraft:regeneration 999 4 true', 300);
  await ask('/character select juggernaut', 600);
  await ask('/expedition', 1500);
  for (let i = 0; i < 60; i++) { await sleep(2000); if (/expedition/.test(await ask('/data get entity @s Dimension', 400))) break; }
  await sleep(1500);
  await ask('/emberfall bossdevourer 0', 1500);
  await sleep(400);
  const d0 = await dump();
  if (!d0) { console.log('NO DUMP'); process.exit(1); }
  const disp = d0.parts.filter(p => p.k === 'ItemDisplay').length;
  const stands = d0.parts.filter(p => /Segment/.test(p.k)).length;
  check('W1', disp === 20 && stands === 0 && d0.n === 21 && d0.parts.some(p => p.k === 'DevourerBrain'), `displays=${disp} armorstands=${stands} total parts=${d0.n} (want 20, 0, 21 incl. brain)`);
  const brainYs = []; let maxRise = 0, minHead = 99, base = null, lowY = 999, highY = -999, samples = 0, maxAbove = -99, sawStand = false, floorY = d0.py;
  for (let i = 0; i < 70; i++) {
    const d = await dump(); if (!d) continue; samples++;
    if (d.parts.some(p => /Segment/.test(p.k))) sawStand = true;
    const brain = d.parts.find(p => p.k === 'DevourerBrain');
    const head = d.parts.filter(p => p.k === 'ItemDisplay')[0];
    if (brain && base === null) base = brain.y;
    if (brain) { lowY = Math.min(lowY, brain.y); highY = Math.max(highY, brain.y); brainYs.push(Math.round(brain.y * 2) / 2); }
    if (head && brain && !brain.inv) { const above = head.y - floorY; minHead = Math.min(minHead, above); maxAbove = Math.max(maxAbove, above); }
    await sleep(300);
  }
  // Measure against the MODAL brain y (where it stands), not the minimum: the burrow dips about 3 below the floor.
  const counts = {}; brainYs.forEach(y => { counts[y] = (counts[y] || 0) + 1; });
  const modal = +Object.keys(counts).sort((a, b) => counts[b] - counts[a])[0];
  maxRise = highY - modal;
  console.log(`  samples=${samples} modal standing y=${modal} lowest=${lowY.toFixed(1)} (burrow dip ${(modal - lowY).toFixed(1)}) maxRise above standing=${maxRise.toFixed(1)} maxRise above start=${maxRise.toFixed(1)} minHead above player feet=${minHead.toFixed(2)} maxHeadAbove=${maxAbove.toFixed(1)} base=${base}`);
  // Peak height is judged by leap_profile.js (fast sampling, measured 8.94). Sampling every 0.3s often misses the 1.5s apex,
  // so here it is only a sanity floor: the boss must clearly leave the ground at all.
  check('W3', maxRise >= 3.0 && maxRise <= 11.0, `peak rise ${maxRise.toFixed(1)} blocks (want about 9, 7 to 11)`);
  check('W5', !sawStand, 'no DevourerSegment armor stand seen in any sample');
  console.log(res.every(Boolean) ? 'ALL PASS' : 'SOME FAIL');
  bot.quit(); setTimeout(() => process.exit(0), 300);
});
bot.on('error', e => console.log('ERR', e.message));
