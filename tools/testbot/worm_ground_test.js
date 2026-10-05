const mineflayer = require('mineflayer');
const bot = mineflayer.createBot({ host: '127.0.0.1', port: 25565, username: 'EmberTester', version: '1.21.11', auth: 'offline' });
const sleep = ms => new Promise(r => setTimeout(r, ms));
const lines = []; bot.on('message', m => lines.push(m.toString()));
const ask = async (x, w = 700) => { const n = lines.length; bot.chat(x); await sleep(w); return lines.slice(n).join(' | '); };
const res = []; const check = (id, ok, note) => { res.push(ok); console.log(`${ok ? 'PASS' : 'FAIL'} ${id} ${note}`); };
const dump = async () => {
  const r = await ask('/emberfall devdump EmberTester', 700);
  const parts = [...r.matchAll(/\[(\w+)(?:#\d+)? ([\d.-]+),([\d.-]+),([\d.-]+)( invis)?\]/g)].map(x => ({ k: x[1], x: +x[2], y: +x[3], z: +x[4] }));
  return parts;
};
bot.once('spawn', async () => {
  await sleep(6500);
  await ask('/kill @e[type=!player]', 500); await ask('/kill @e[type=minecraft:item_display]', 500); await sleep(1500);
  await ask('/gamemode survival'); await ask('/effect give @s minecraft:resistance 999 4 true', 300);
  await ask('/effect give @s minecraft:regeneration 999 4 true', 300);
  await ask('/character select juggernaut', 600); await ask('/expedition', 1500); await sleep(1500);
  await ask('/emberfall bossdevourer 0', 1500);
  const rows = [];
  const t0 = Date.now();
  while (Date.now() - t0 < 40000) {
    const p = await dump();
    const brain = p.find(a => a.k === 'DevourerBrain');
    const disp = p.filter(a => a.k === 'ItemDisplay');
    if (brain && disp.length) rows.push({ by: brain.y, hy: disp[0].y, bodyY: disp.slice(1).map(d => d.y), bodyX: disp.slice(1).map(d => [d.x, d.z]) });
  }
  const counts = {}; rows.forEach(r => { const k = Math.round(r.by); counts[k] = (counts[k] || 0) + 1; });
  const floor = +Object.entries(counts).sort((a, b) => b[1] - a[1])[0][0];
  const up = rows.filter(r => Math.abs(r.by - floor) < 0.5);   // brain standing on the floor
  console.log(`  rows=${rows.length} floor=${floor} surfaced samples=${up.length}`);
  check('G0', up.length >= 20, `control: ${up.length} surfaced samples (need 20+)`);
  const minHead = Math.min(...up.map(r => r.hy - r.by));
  check('G1', minHead >= 0.9, `lowest head-centre height above the brain's feet while surfaced = ${minHead.toFixed(2)} (want 0.9+, cube half-size is 1.05)`);
  const minBody = Math.min(...up.flatMap(r => r.bodyY.map(y => y - r.by)));
  check('G2', minBody >= -0.05, `lowest body-segment height relative to the brain's feet while surfaced = ${minBody.toFixed(2)} (want 0 or above)`);
  // body must be a line, not a pile: last segment at least 3 blocks from the head in the horizontal plane on some sample
  const spread = Math.max(...up.map(r => { const h = r.bodyX[0]; const t = r.bodyX[r.bodyX.length - 1]; return Math.hypot(h[0] - t[0], h[1] - t[1]); }));
  check('G3', spread >= 3.0, `largest first-to-last body segment spread = ${spread.toFixed(1)} blocks (a pile would be about 0)`);
  console.log(res.every(Boolean) ? 'ALL PASS' : 'SOME FAIL');
  bot.quit(); setTimeout(() => process.exit(0), 300);
});
bot.on('error', e => console.log('ERR', e.message));
