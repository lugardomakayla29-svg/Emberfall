// Issue #13, steps 3b to 3d: party scaling wired into the director. Run once with PARTY=1 and once with PARTY=5, each on a FRESH world,
// with the server flag -Demberfall.logPartyHp=true. This script only drives the run and prints what it saw; the VERDICT is made by
// party_scale_grade.sh from the server log (PARTYFROZEN and PARTYHP lines), because the numbers that matter are logged server side.
// PARTY=1: one bot. PARTY=5: Anchor starts the run, Mate1..Mate4 are put in with `/emberfall join` BEFORE the first spawn (tick 100).
const mineflayer = require('mineflayer');
const sleep = ms => new Promise(r => setTimeout(r, ms));
const PARTY = parseInt(process.env.PARTY || '1', 10);
const EXPECTED = 3; let ran = 0, fails = 0;
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
  await say('/gamemode creative', 300);
  await say('/emberfall bot spawn Anchor', 1500);
  await say('/emberfall bot run Anchor ranger', 3000);
  let slot = null;
  for (let i = 0; i < 70; i++) { await sleep(2000); const s = await say('/emberfall bot state Anchor', 600); const m = /run=(\d+)/.exec(s); if (m && /weapons=\w/.test(s)) { slot = m[1]; break; } }
  check('S1 Anchor is in a run', slot !== null, 'slot=' + slot);
  const mates = ['Mate1', 'Mate2', 'Mate3', 'Mate4'].slice(0, PARTY - 1);
  for (const m of mates) await say('/emberfall bot spawn ' + m, 700);
  for (const m of mates) await say(`/emberfall join ${slot} ${m}`, 900);
  for (const n of ['Anchor', ...mates]) await say(`/effect give ${n} minecraft:resistance 1000000 4 true`, 200);
  const st = await say('/emberfall wavestatus ' + slot, 900);
  const m = /partySize=(\d+)/.exec(st);
  check('S2 the run has ' + PARTY + ' players before the first spawn', m && parseInt(m[1], 10) === PARTY, st.slice(-30));
  // let the director spawn a good number of horde mobs (threat up so spawns are frequent)
  await say('/emberfall relic threatadd Anchor 20', 800);
  await sleep(45000);
  const w = await say('/emberfall wavestatus ' + slot, 900);
  const ts = /totalSpawned=(\d+)/.exec(w);
  check('S3 the director spawned at least 15 mobs', ts && parseInt(ts[1], 10) >= 15, w.slice(0, 90));
  for (const n of ['Anchor', ...mates]) await say('/emberfall bot remove ' + n, 300);
  const code = finish();
  op.quit(); setTimeout(() => process.exit(code), 500);
})().catch(e => { console.log('FAIL suite crashed: ' + (e && e.stack || e)); console.log('RESULT: crashed'); process.exit(1); });
