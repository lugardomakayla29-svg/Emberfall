const mineflayer = require('mineflayer');
const bot = mineflayer.createBot({ host: '127.0.0.1', port: 25565, username: 'EmberTester', version: '1.21.11', auth: 'offline' });
const sleep = ms => new Promise(r => setTimeout(r, ms));
const lines = []; bot.on('message', m => lines.push(m.toString()));
const ask = async (x, w = 900) => { const n = lines.length; bot.chat(x); await sleep(w); return lines.slice(n).join(' | '); };
const res = [];
const check = (id, ok, note) => { res.push(ok); console.log(`${ok ? 'PASS' : 'FAIL'} ${id} ${note}`); };
const dump = async () => {
  const r = await ask('/emberfall tikidump EmberTester', 1000);
  const m = /mobs=(\d+) cubes=(\d+) oldsegments=(\d+) heads=(\d+) roofs=(\d+)/.exec(r);
  const ys = [...r.matchAll(/\[Head y=([\d.]+)\]/g)].map(x => parseFloat(x[1])).sort((a, b) => a - b);
  return m ? { mobs: +m[1], cubes: +m[2], old: +m[3], heads: +m[4], roofs: +m[5], ys } : null;
};
bot.once('spawn', async () => {
  await sleep(6500);
  await ask('/gamemode creative');
  for (const [label, cmd, want] of [
    ['FODDER', '/emberfall spawnveteran tiki_magma', { mobs: 1, cubes: 1, heads: 1, roofs: 1 }],
    ['ELITE', null, { mobs: 1, cubes: 0, heads: 3, roofs: 1 }],
    ['CORRUPTED', '/emberfall spawnelite tiki_magma_corrupted', { mobs: 1, cubes: 0, heads: 4, roofs: 1 }]]) {
    await ask('/kill @e[type=!player]', 700); await sleep(2500);
    let spawned = cmd;
    if (label === 'ELITE') { // tiki_magma elite rolls corrupted 20% of the time: retry until the tier is plain elite
      for (let i = 0; i < 12; i++) {
        const r = await ask('/emberfall spawnelite tiki_magma', 1000);
        // The reply says 'Elite-tier' even for a Corrupted roll (measured: 10 of 10 identical), so judge by the real stack: elite has 3 heads.
        await sleep(2200); const probe = await dump();
        if (probe && probe.heads === 3) { spawned = 'ok'; break; }
        await ask('/kill @e[type=!player]', 600); await sleep(2200);
      }
    } else await ask(cmd, 1200);
    await sleep(2500);
    const d = await dump();
    if (!d) { check(`${label}-parse`, false, 'no dump'); continue; }
    check(`${label}-count`, d.mobs === want.mobs && d.cubes === want.cubes && d.heads === want.heads && d.roofs === want.roofs && d.old === 0,
      `mobs=${d.mobs} cubes=${d.cubes} heads=${d.heads} roofs=${d.roofs} old=${d.old} (want ${JSON.stringify(want)})`);
    if (d.ys.length > 1) {
      const gaps = d.ys.slice(1).map((y, i) => +(y - d.ys[i]).toFixed(3));
      console.log(`  ${label} head y gaps`, gaps.join(','), ' heads y', d.ys.join(','));
    }
  }
  // T4: kill leaves nothing behind
  await ask('/kill @e[type=!player]', 700); await sleep(2500);
  await ask('/emberfall spawnveteran tiki_magma', 1200); await sleep(2000);
  const before = await dump();
  await ask('/kill @e[type=emberfall:tiki_magma]', 800); await sleep(3500);
  const after = await dump();
  check('KILL-cleanup', before && before.heads === 1 && after && after.mobs === 0 && after.cubes === 0 && after.heads === 0 && after.roofs === 0,
    `before ${JSON.stringify(before && { h: before.heads, c: before.cubes })} after ${JSON.stringify(after && { m: after.mobs, c: after.cubes, h: after.heads, r: after.roofs })}`);
  console.log(res.every(Boolean) ? 'ALL PASS' : 'SOME FAIL');
  bot.quit(); setTimeout(() => process.exit(0), 300);
});
bot.on('error', e => console.log('ERR', e.message));
