// Issue #13, step 3a: RunManager.partySize(slot), read through `/emberfall wavestatus <slot>` (partySize=<n>).
// Five EmberBots share slot 0 (same scaffold as cap_party5_test): Anchor starts the run, Mate1..Mate4 are put in with `/emberfall join`.
// We read the count at 1, 2 and 5, then lower it two ways: a DISCONNECT (`/emberfall bot remove`, which calls connection.disconnect)
// and the op command `/emberfall leave <name>`. Needs a FRESH server. Exit 0 only if all EXPECTED checks ran and none failed.
const mineflayer = require('mineflayer');
const sleep = ms => new Promise(r => setTimeout(r, ms));
const EXPECTED = 9; let ran = 0, fails = 0;
const check = (n, ok, note = '') => { ran++; console.log((ok ? 'PASS ' : 'FAIL ') + n + ' ' + note); if (!ok) fails++; };
function finish() {
  const short_ = ran < EXPECTED;
  if (fails === 0 && !short_) { console.log('RESULT: ALL PASSED (' + ran + ' checks)'); return 0; }
  console.log('RESULT: ' + (fails || 1) + ' FAILED' + (short_ ? ' (only ' + ran + ' of ' + EXPECTED + ' checks ran)' : ''));
  return 1;
}
const mk = name => new Promise(res => {
  const b = mineflayer.createBot({ host: '127.0.0.1', port: 25565, username: name, version: '1.21.11', auth: 'offline' });
  b.chat_ = []; b.on('message', m => b.chat_.push(m.toString()));
  b.once('spawn', () => res(b));
  b.on('error', e => { console.log('FAIL connection error: ' + e.message); fails++; process.exit(finish()); });
  b.on('kicked', r => { console.log('FAIL kicked: ' + JSON.stringify(r).slice(0, 160)); fails++; process.exit(finish()); });
});
(async () => {
  const op = await mk('EmberTester'); await sleep(4000);
  const say = async (cmd, w = 900) => { op.chat_.length = 0; op.chat(cmd); await sleep(w); return op.chat_.join(' | '); };
  const size = async slot => { const s = await say('/emberfall wavestatus ' + slot, 900); const m = /partySize=(\d+)/.exec(s); console.log('WAVESTATUS :: ' + s.slice(0, 200)); return m ? parseInt(m[1], 10) : null; };
  await say('/gamemode creative', 300);
  await say('/emberfall bot spawn Anchor', 1500);
  await say('/emberfall bot run Anchor ranger', 3000);
  let slot = null;
  for (let i = 0; i < 70; i++) { await sleep(2000); const s = await say('/emberfall bot state Anchor', 600); const m = /run=(\d+)/.exec(s); if (m && /weapons=\w/.test(s)) { slot = m[1]; break; } }
  check('P1 Anchor is in a run', slot !== null, 'slot=' + slot);
  check('P2 one player reads partySize=1', (await size(slot)) === 1);
  const mates = ['Mate1', 'Mate2', 'Mate3', 'Mate4'];
  for (const m of mates) await say('/emberfall bot spawn ' + m, 900);
  await say(`/emberfall join ${slot} Mate1`, 1500);
  check('P3 after one /emberfall join it reads partySize=2', (await size(slot)) === 2);
  for (const m of mates.slice(1)) await say(`/emberfall join ${slot} ${m}`, 1500);
  check('P4 after four joins it reads partySize=5', (await size(slot)) === 5);
  // Same protection cap_party5_test gives its bots: the arena has live mobs, and a death would lower the count and hide what we test.
  for (const n of ['Anchor', ...mates]) await say(`/effect give ${n} minecraft:resistance 1000000 4 true`, 250);
  await say('/emberfall bot remove Mate4', 2500); await sleep(1500);
  check('P5 a DISCONNECT (bot remove Mate4) lowers it to 4', (await size(slot)) === 4);
  const st = await say('/emberfall bot state Mate4', 700);
  check('P6 the disconnected bot no longer reports run=' + slot, !(new RegExp('run=' + slot + '\\b').test(st)), st.slice(0, 60));
  await say('/emberfall leave Mate3', 2000);
  check('P7 /emberfall leave Mate3 lowers it to 3', (await size(slot)) === 3);
  { const w = await say('/emberfall wavestatus ' + slot, 900); check('P8 with 3 players still in, the run is still live (director running, partySize=3)', /Slot \d+: tier=/.test(w) && /partySize=3/.test(w), w.slice(0, 60)); }
  await say('/emberfall leave Anchor', 2000); await say('/emberfall leave Mate1', 2000); await say('/emberfall leave Mate2', 2000);
  const last = await say('/emberfall wavestatus ' + slot, 900);
  // After the last leave the director may still answer for a moment (it is stopped on a later tick), so the honest check is that the count reads 0 or there is no director.
  check('P9 everyone out: partySize reads 0 (or the director is already gone)', /partySize=0\b/.test(last) || /No active Wave Director/.test(last), last.replace(/.*partySize=/, 'partySize=').slice(0, 60));
  const code = finish();
  op.quit(); setTimeout(() => process.exit(code), 500);
})().catch(e => { console.log('FAIL suite crashed: ' + (e && e.stack || e)); console.log('RESULT: crashed'); process.exit(1); });
