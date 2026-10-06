// RELIC FOUNDATION live test. Phase A: give/caps/attributes/price/Ledger/detach. Writes unlock progress for phase B.
const mineflayer = require('mineflayer');
const sleep = ms => new Promise(r => setTimeout(r, ms));
const mk = name => new Promise(res => { const b = mineflayer.createBot({ host: '127.0.0.1', port: 25565, username: name, version: '1.21.11', auth: 'offline' }); b.chat_ = []; b.on('message', m => { const t = m.toString(); if (t.trim()) b.chat_.push(t); }); b.once('spawn', () => res(b));
  // A refused connection or a kick (for example a leftover bot with the same name) never fires 'spawn': end the run, never print nothing.
  b.on('error', e => { console.log('FAIL connection error: ' + e.message); fails++; process.exit(finish()); });
  b.on('kicked', r => { console.log('FAIL kicked: ' + JSON.stringify(r).slice(0, 160)); fails++; process.exit(finish()); });
});
// EXPECTED_CHECKS is what a complete run makes (measured live on a fresh world: 27). A run with fewer did not finish, and a run
// with none (no server, or kicked) must never read as a pass: the verdict needs fails === 0 AND ran >= EXPECTED_CHECKS.
// NOTE: this suite writes unlock progress, so it needs a FRESH world; a second run on the same world fails 4 checks (measured).
const EXPECTED_CHECKS = 27;
let fails = 0; let ran = 0;
const check = (label, ok, extra = '') => { ran++; console.log((ok ? 'PASS ' : 'FAIL ') + label + (extra ? '  ' + extra : '')); if (!ok) fails++; };
// Print the verdict and return the exit code that agrees with it. Every exit goes through here so none can disagree.
function finish() {
  const short_ = ran < EXPECTED_CHECKS;
  if (fails === 0 && !short_) { console.log('RESULT: ALL PASSED (' + ran + ' checks)'); return 0; }
  console.log('RESULT: ' + (fails || 1) + ' FAILED' + (short_ ? ' (only ' + ran + ' of ' + EXPECTED_CHECKS + ' checks ran)' : ''));
  return 1;
}
(async () => {
  const op = await mk('EmberTester'); await sleep(5000);
  const say = async (cmd, w = 900) => { op.chat_.length = 0; op.chat(cmd); await sleep(w); return op.chat_.join(' | '); };
  const num = (s, k) => { const m = s.match(new RegExp(k + '=(-?[0-9.]+)')); return m ? parseFloat(m[1]) : NaN; };
  const startRun = async () => {
    await say('/expedition', 2500);
    for (let i = 0; i < 40; i++) { await sleep(2000); if (/expedition/.test(await say('/data get entity @s Dimension', 400))) break; }
    await sleep(1500);
  };
  await say('/gamemode survival EmberTester', 600);
  const list = await say('/emberfall relic list');
  check('pool lists 24 relics', /RELIC pool 24:/.test(list), list.slice(0, 60));
  // not in a run: nothing can be given
  const pre = await say('/emberfall relic give EmberTester clover');
  check('cannot give a relic outside a run', /given=0/.test(pre), pre.slice(0, 90));
  // start a run the way tests do (debug join)
  await startRun();
  const st0 = await say('/emberfall relic state EmberTester');
  check('run start activates the store', /active=true/.test(st0), st0.slice(0, 120));
  check('fresh run: no relics, price 30, opened 0', /owned=\{\}/.test(st0) && num(st0, 'price') === 30 && num(st0, 'opened') === 0, '');
  const hp0 = num(st0, 'max'), sp0 = num(st0, 'speed'); // IN-RUN base (the character's own modifiers are active)
  const CH = 1.2; // the test character's max-health multiplier, measured: 24 in run vs 20 outside
  // stacks and caps
  let r = await say('/emberfall relic give EmberTester clover 3');
  check('clover x3 given', /given=3 now=3/.test(r), r.slice(0, 80));
  r = await say('/emberfall relic give EmberTester clover 20');
  check('clover caps at 10', /now=10/.test(r), r.slice(0, 80));
  r = await say('/emberfall relic give EmberTester nonsense');
  check('unknown id gives nothing', /given=0/.test(r), r.slice(0, 80));
  const st1 = await say('/emberfall relic state EmberTester');
  check('luck is 8 per clover (80)', num(st1, 'luck') === 80, 'luck=' + num(st1, 'luck'));
  // attributes
  await say('/emberfall relic give EmberTester oat_loaf 3');
  const st2 = await say('/emberfall relic state EmberTester');
  check('oat_loaf x3 raises max health by 12 before the character multiplier (12 x1.2 = 14.4)', Math.abs(num(st2, 'max') - (hp0 + 12 * CH)) < 0.01, `max ${hp0} -> ${num(st2, 'max')}`);
  check('new hearts arrive filled (health within 1 of max)', num(st2, 'max') - num(st2, 'hp') < 1.0, `hp ${num(st2, 'hp')} max ${num(st2, 'max')}`);
  await say('/emberfall relic give EmberTester iron_boots 2');
  const st3 = await say('/emberfall relic state EmberTester');
  check('iron_boots x2 raises speed ~16%', Math.abs(num(st3, 'speed') / sp0 - 1.16) < 0.01, `speed ${sp0} -> ${num(st3, 'speed')}`);
  // recompute is idempotent: giving a non-attribute relic must not stack the attribute again
  await say('/emberfall relic give EmberTester gold_nugget 2');
  const st4 = await say('/emberfall relic state EmberTester');
  check('unrelated give leaves max health unchanged', num(st4, 'max') === num(st3, 'max'), `${num(st3, 'max')} vs ${num(st4, 'max')}`);
  check('gold_nugget x2 = x1.30 gold', Math.abs(num(st4, 'gold') - 1.30) < 0.001, 'gold=' + num(st4, 'gold'));
  // take
  r = await say('/emberfall relic take EmberTester oat_loaf');
  const st5 = await say('/emberfall relic state EmberTester');
  check('take removes one stack (8 x1.2 above base)', /take true/.test(r) && Math.abs(num(st5, 'max') - (hp0 + 8 * CH)) < 0.01, `max ${num(st5, 'max')}`);
  r = await say('/emberfall relic take EmberTester hourglass');
  check('take of an unowned relic is refused', /take false/.test(r), r.slice(0, 40));
  // chest price counter
  r = await say('/emberfall relic price 0'); check('price(0)=30', /=30/.test(r), r.slice(0, 40));
  r = await say('/emberfall relic price 10'); check('price(10)=279', /=279/.test(r), r.slice(0, 40));
  // run end detaches everything
  await say('/expedition leave', 2500);
  const st6 = await say('/emberfall relic state EmberTester');
  check('leaving the run clears the store', /active=false/.test(st6) && /owned=\{\}/.test(st6), st6.slice(0, 100));
  check('leaving the run restores the OUTSIDE base (20, no relic and no character modifier left)', num(st6, 'max') === 20, `max ${num(st6, 'max')}`);
  check('leaving the run restores the OUTSIDE speed (0.100)', Math.abs(num(st6, 'speed') - 0.1) < 1e-6, `speed ${num(st6, 'speed')}`);
  // second run starts clean (the tome crash lesson: a repeat run must not collide)
  await startRun();
  r = await say('/emberfall relic give EmberTester oat_loaf 3');
  const st7 = await say('/emberfall relic state EmberTester');
  check('a second run takes the same relic again with the SAME result (no leftover from run 1)', /given=3/.test(r) && Math.abs(num(st7, 'max') - (hp0 + 12 * CH)) < 0.01, `max ${num(st7, 'max')}`);
  await say('/expedition leave', 2500);
  // unlock progress (persisted for phase B)
  let u = await say('/emberfall relic unlocks EmberTester');
  check('fresh player has no unlocks', /unlocks \[\]/.test(u), u.slice(0, 100));
  u = await say('/emberfall relic unlocks EmberTester add open_25_chests 10');
  check('10 of 25 chests unlocks nothing yet', /unlocked-now \[\]/.test(u), u.slice(0, 80));
  u = await say('/emberfall relic unlocks EmberTester');
  check('progress is 10', /open_25_chests=10/.test(u), u.slice(0, 160));
  u = await say('/emberfall relic unlocks EmberTester add open_25_chests 15');
  check('reaching 25 unlocks ember_key', /unlocked-now \[ember_key\]/.test(u), u.slice(0, 80));
  u = await say('/emberfall relic unlocks EmberTester add open_25_chests 5');
  check('a further add after unlock does not unlock twice', /unlocked-now \[\]/.test(u), u.slice(0, 80));
  u = await say('/emberfall relic unlocks EmberTester add nonsense 5');
  check('unknown unlock id is ignored', /unlocked-now \[\]/.test(u), u.slice(0, 80));
  await say('/emberfall relic unlocks EmberTester add clear_3_challenges 2');
  const code = finish();
  op.quit(); setTimeout(() => process.exit(code), 500);
})().catch(e => { console.log('FAIL suite crashed: ' + (e && e.stack || e)); console.log('RESULT: crashed'); process.exit(1); });
