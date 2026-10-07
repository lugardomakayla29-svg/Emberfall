// STATUS 2026-10-07: the crash was seen ONCE in 6 party runs (4 weak bots, no resistance), at PinkPools.tickAll line 160 which is the PLAYERS loop
// (`for (Player player : level.players())`), not the pool list. This probe has NOT yet reproduced it on demand; a clean run proves nothing.
// A verdict needs many runs (pinkcrash_driver.sh) on a build WITHOUT the snapshot and the same count WITH it.
// REPRODUCTION for the server crash seen in party runs: ConcurrentModificationException at PinkPools.tickAll (the `for (Pool pool : ACTIVE)` inside
// the per-player damage loop). Mechanism under test: a pool kills the LAST player, RunEndHandler tears the arena down and calls
// PinkPools.clearLevel (ACTIVE.removeIf) while tickAll is still iterating ACTIVE for the next player in its snapshot.
// Four weak bots stand on a pinned pink slime so several die from pool damage in the same second. The verdict comes from the SERVER LOG
// (a crash report / CME trace), read by pink_party_crash_grade.sh, not from this script.
const mineflayer = require('mineflayer');
const sleep = ms => new Promise(r => setTimeout(r, ms));
const mk = name => new Promise(res => { const b = mineflayer.createBot({ host: '127.0.0.1', port: 25565, username: name, version: '1.21.11', auth: 'offline' }); b.chat_ = []; b.on('message', m => b.chat_.push(m.toString())); b.once('spawn', () => res(b)); });
(async () => {
  const op = await mk('EmberTester'); await sleep(3500);
  const say = async (cmd, w = 700) => { op.chat_.length = 0; op.chat(cmd); await sleep(w); return op.chat_.join(' | '); };
  const names = ['PA', 'PB', 'PC', 'PD'];
  await say('/gamemode creative', 300);
  for (const n of names) await say('/emberfall bot spawn ' + n, 1800);
  console.log('RUN', (await say('/emberfall bot run PA ranger', 1500)).slice(0, 60));
  for (let i = 0; i < 70; i++) { await sleep(2000); const s = await say('/emberfall bot state PA', 700); if (/run=\d/.test(s) && /weapons=\w/.test(s)) break; }
  for (const n of ['PB', 'PC', 'PD']) { await say('/character select ranger', 400); console.log('JOIN', n, (await say('/emberfall join 0 ' + n, 1500)).slice(0, 60)); }
  // V13 DESIGN TEST: stop the wave director so the ONLY hostile things are the pink slimes, then spawn them UNPINNED (they have AI)
  await say('/emberfall wavestop 0', 500);
  for (let k = 0; k < 4; k++) { await say('/execute at @a[name=PA] run emberfall spawnelite pink_slime', 500); }
  const t0 = Date.now(); const rows = [];
  while (Date.now() - t0 < 70000) {
    await sleep(5000);
    const st = {}; for (const n of names) { const s = await say('/emberfall bot state ' + n, 450); const h = /hp=(\d+)/.exec(s); st[n] = /not online/.test(s) ? 'off' : (/run=0/.test(s) ? 'inrun hp=' + (h ? h[1] : '?') : 'outofrun hp=' + (h ? h[1] : '?')); }
    console.log('ROW t=' + Math.round((Date.now() - t0) / 1000), JSON.stringify(st));
    if (Object.values(st).every(v => !/^inrun/.test(v))) break;
  }
  console.log('DONE'); process.exit(0);
})();
