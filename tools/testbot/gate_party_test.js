// Expedition Gate party formation (issue #13). N players stand near the gate and each clicks it inside the first player's
// countdown: they must depart TOGETHER in ONE run (one "Started MAP run" line, partySize == N), and each returns home.
// Env: N (2..4, default 3). Run counts come from the SERVER LOG (gate_party_grade.sh); party size and positions from the game.
// Needs a FRESH world with the hub activated by this test.
const mineflayer = require('mineflayer');
const sleep = ms => new Promise(r => setTimeout(r, ms));
const N = parseInt(process.env.N || '3', 10);
const X = -29, Y = 75, Z = -2;            // hearth; the gate cell is (X, Y, Z-1)
const EXPECTED = 7; let ran = 0, fails = 0;
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
  const say = async (b, cmd, w = 900) => { b.chat_.length = 0; b.chat(cmd); await sleep(w); return b.chat_.join(' | '); };
  const dimOf = async name => (await say(op, `/data get entity ${name} Dimension`, 500)).includes('expedition') ? 'expedition' : 'other';
  // Read each player's own position from that player's own client, so the op's chat can never alias it.
  const posOf = async name => { const b = name === 'EmberTester' ? op : (mates.find(m => m.name === name) || {}).bot; const e = b && b.entity; return e ? [e.position.x, e.position.y, e.position.z] : null; };
  await say(op, '/gamemode creative', 500);
  await say(op, `/tp @s ${X + 0.5} ${Y + 1} ${Z + 6.5}`, 2500);
  await say(op, `/setblock ${X} ${Y + 1} ${Z} emberfall:ember_hearth`, 900);
  await say(op, `/emberfall hubactivate ${X} ${Y + 1} ${Z}`, 3500);
  const names = ['EmberTester'];
  const mates = [];
  for (let i = 1; i < N; i++) {
    const nm = 'Mate' + i; const b = await mk(nm); await sleep(2500);
    await say(op, '/op ' + nm, 500);
    mates.push({ name: nm, bot: b }); names.push(nm);
  }
  // Everyone survival, a character, and a spot 2 to 3.5 blocks from the gate (inside reach 4, outside the 1.2 clearance).
  // The gate hotspot sits at about (-28.5, -2.5). Fixed spots 2.0 to 3.2 blocks from it: inside the 4-block click reach and
  // outside the 1.2 return clearance, so each member keeps a personal return spot.
  const SPOTS = [[-28.5, -4.6], [-26.0, -3.6], [-31.0, -3.8], [-28.5, -0.2]];
  for (let i = 0; i < names.length; i++) {
    const b = i === 0 ? op : mates[i - 1].bot;
    await say(b, '/gamemode survival', 400);
    await say(b, '/character select juggernaut', 600);
    await say(op, `/tp ${names[i]} ${SPOTS[i][0]} ${Y + 1} ${SPOTS[i][1]}`, 700);
  }
  await sleep(1500);
  check('P1 all ' + N + ' players start in the overworld', (await Promise.all(names.map(dimOf))).every(d => d === 'other'), '');
  // The leader clicks, the others click 1 s apart (inside the 3 s countdown).
  const r0 = await say(op, '/emberfall hubclick hubact_gate', 500);
  console.log('CLICK EmberTester :: ' + r0.slice(0, 80));
  let joinedMsgs = 0;
  for (const m of mates) { const r = await say(m.bot, '/emberfall hubclick hubact_gate', 400); console.log('CLICK ' + m.name + ' :: ' + r.slice(0, 80)); await sleep(300); }
  for (let i = 0; i < 70; i++) { await sleep(2000); const ds = await Promise.all(names.map(dimOf)); if (ds.every(d => d === 'expedition')) break; }
  const dims = await Promise.all(names.map(dimOf));
  check('P2 every player who clicked is now in the expedition', dims.every(d => d === 'expedition'), JSON.stringify(dims));
  await sleep(2500);
  const st = await say(op, '/emberfall wavestatus 0', 900);
  const pm = /partySize=(\d+)/.exec(st);
  check('P3 the run reports partySize == ' + N, pm && parseInt(pm[1], 10) === N, st.slice(-30));
  const st1 = await say(op, '/emberfall wavestatus 1', 900);
  check('P4 there is NO second run (slot 1 does not exist)', /No active|no active/i.test(st1), st1.slice(0, 60));
  // Everyone goes home and lands near the gate, not on it, not stacked on one block.
  for (const n of names) await say(op, '/expedition leave', 100);   // op leaves; mates leave themselves below
  for (const m of mates) await say(m.bot, '/expedition leave', 500);
  await sleep(2500);
  const homeDims = await Promise.all(names.map(dimOf));
  check('P5 everyone is home after leaving', homeDims.every(d => d === 'other'), JSON.stringify(homeDims));
  const ps = await Promise.all(names.map(posOf));
  const dist = p => p ? Math.hypot(p[0] - (X + 0.5), p[2] - (Z - 0.5)) : -1;
  check('P6 nobody landed ON the gate (all >= 1.0 block from it)', ps.every(p => dist(p) >= 1.0), ps.map(p => dist(p).toFixed(2)).join(','));
  const keys = new Set(ps.map(p => p ? Math.round(p[0] * 2) + ',' + Math.round(p[2] * 2) : 'x'));
  check('P7 nobody stacked on the same half-block', keys.size === names.length, [...keys].join(' | '));
  const code = finish();
  op.quit(); for (const m of mates) m.bot.quit(); setTimeout(() => process.exit(code), 600);
})().catch(e => { console.log('FAIL suite crashed: ' + (e && e.stack || e)); console.log('RESULT: crashed'); process.exit(1); });
