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
  // pinned pink slimes right on top of the party, several at once, so pools overlap them and kills cluster
  for (let k = 0; k < 4; k++) { await say('/execute at @a[name=PA] run emberfall spawnelite pink_slime', 500); }
  await say('/execute as @e[type=emberfall:pink_slime] run data merge entity @s {NoAI:1b}', 500);
  // MEASUREMENT TAIL (V13): the probe's own setup above is unchanged. Poll for 60 s, write what the bots and slimes look like.
  const t0 = Date.now(); const rows = [];
  while (Date.now() - t0 < 60000) {
    await sleep(5000);
    const hp = {}; for (const n of names) { const s = await say('/emberfall bot state ' + n, 450); const m = /hp=([\d.]+)/.exec(s); hp[n] = /not online/.test(s) ? 'off' : (m ? +m[1] : '?'); }
    const slimes = await say('/execute if entity @e[type=emberfall:pink_slime]', 400);
    rows.push({ t: Math.round((Date.now() - t0) / 1000), hp, slimesExist: /passed/.test(slimes) }); console.log('ROW', JSON.stringify(rows[rows.length - 1]));
  }
  console.log('DONE'); process.exit(0);
})();
