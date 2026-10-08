// Chest reveal, TWO real clients in ONE run. The one-bot test cannot see these four lines, so deleting any of them left every assertion green:
//   ChestManager.REVEALS keyed by player UUID         (a wrong key shares one slot between players)
//   RunManager.leavePlayer -> ChestManager.forgetReveal (a player who leaves with a reveal open)
//   EmberfallMod: END_SERVER_TICK -> ChestManager::tickReveals (the sweep is registered)
//   ChestManager.tickReveals: server.getTickCount() % 20 == 0  (the sweep actually runs)
// The reader is `/emberfall relic revealstate`: stored= counts expired entries too, so only the sweep and forgetReveal can lower it.
// Run from emberfall/bot (node_modules) on a FRESH server. Verdicts are the bot's own reads of the server's counters.
const mineflayer = require('mineflayer');
const { Vec3 } = require('vec3');
const sleep = ms => new Promise(r => setTimeout(r, ms));
let ran = 0, fails = 0; const EXPECTED = 10;
const check = (n, ok, note = '') => { ran++; console.log((ok ? 'PASS ' : 'FAIL ') + n + (note ? '  ' + note : '')); if (!ok) fails++; };
const mk = name => new Promise(res => {
  const b = mineflayer.createBot({ host: '127.0.0.1', port: 25565, username: name, version: '1.21.11', auth: 'offline' });
  b.chat_ = []; b.on('message', m => b.chat_.push(m.toString()));
  b.once('spawn', () => res(b));
  b.on('error', e => { console.log('ERR ' + name + ' ' + e.message); });
});
const finish = () => { const short_ = ran < EXPECTED; console.log(fails === 0 && !short_ ? `ALL PASS (${ran})` : `FAILED ${fails}` + (short_ ? ` (only ${ran} of ${EXPECTED} checks ran)` : '')); process.exit(fails === 0 && !short_ ? 0 : 1); };

(async () => {
  const op = await mk('EmberTester'); await sleep(4000);
  const say = async (b, cmd, w = 700) => { const n = b.chat_.length; b.chat(cmd); await sleep(w); return b.chat_.slice(n).join(' | '); };
  const rs = async () => { const t = await say(op, '/emberfall relic revealstate', 600); const m = /REVEAL stored=(\d+) open=(\d+)/.exec(t); return m ? { stored: +m[1], open: +m[2] } : { stored: -1, open: -1, raw: t }; };
  const chestsFor = async name => { const t = await say(op, `/emberfall relic chests ${name}`, 700); const m = t.match(/total=(\d+) closed=(\d+) free=(\d+) nearest=(-?\d+) (-?\d+) (-?\d+)/); return m ? { total: +m[1], closed: +m[2], x: +m[4], y: +m[5], z: +m[6] } : null; };
  const click = async (b, x, y, z) => { const blk = b.blockAt(new Vec3(x, y, z)); if (!blk) return 'noblock'; try { await b.activateBlock(blk); } catch (e) { return 'err ' + e.message; } await sleep(1200); return 'ok'; };

  await say(op, '/gamemode survival', 300); await say(op, '/character select vanguard', 500);
  await say(op, '/expedition leave', 900); await say(op, '/expedition', 4000);
  for (let i = 0; i < 40; i++) { await sleep(1500); if (/emberfall:expedition/.test(await say(op, '/data get entity @s Dimension', 400))) break; }
  await sleep(2500);
  await say(op, '/emberfall wavestop 0', 300); await say(op, '/effect give @s minecraft:resistance 999 4 true', 200);

  // Mate1 is a real second client, put into the SAME run as the op.
  let mate = await mk('Mate1'); await sleep(3000);
  await say(op, '/gamemode survival Mate1', 300);
  await say(op, '/emberfall join 0 Mate1', 2500);
  await say(op, '/effect give Mate1 minecraft:resistance 999 4 true', 300);
  await say(op, '/gamemode survival', 300);

  const base = await rs();
  check('T0 CONTROL: the counter reads and starts at stored=0 open=0', base.stored === 0 && base.open === 0, JSON.stringify(base));
  const c1 = await chestsFor('EmberTester'), c2 = await chestsFor('Mate1');
  check('T0b the run has at least 2 closed chests (two players need two different ones)', !!c1 && c1.closed >= 2, JSON.stringify(c1));
  if (!c1 || c1.closed < 2) { console.log('cannot continue without 2 chests'); return finish(); }

  // T1: op opens chest A, mate opens a DIFFERENT chest B. Two reveals at once.
  await say(op, '/emberfall relic gold EmberTester 500', 300); await say(op, '/emberfall relic gold Mate1 500', 300);
  await say(op, `/tp @s ${c1.x + 0.5} ${c1.y} ${c1.z + 2.5} 180 0`, 1500);
  await click(op, c1.x, c1.y, c1.z); await sleep(800);
  const afterA = await rs();
  check('T1 one player opens a chest: stored=1 open=1', afterA.stored === 1 && afterA.open === 1, JSON.stringify(afterA));
  const c2b = await chestsFor('Mate1');   // the nearest CLOSED chest to Mate1 now excludes the one just opened
  await say(op, `/tp Mate1 ${c2b.x + 0.5} ${c2b.y} ${c2b.z + 2.5} 180 0`, 1500);
  await click(mate, c2b.x, c2b.y, c2b.z); await sleep(800);
  const both = await rs();
  check('T2 the SECOND player opens a different chest: stored=2 open=2 (a wrong player key would hold ONE entry)', both.stored === 2 && both.open === 2, JSON.stringify(both));

  // T3: the second player DISCONNECTS with its reveal open. forgetReveal in leavePlayer is the only thing that can drop it (it is far from expiring).
  mate.end(); mate = null; await sleep(2500);
  const afterLeave = await rs();
  check('T3 a player who leaves with a reveal open is FORGOTTEN at once: stored=1 (RunManager forgetReveal line)', afterLeave.stored === 1 && afterLeave.open === 1, JSON.stringify(afterLeave));

  // T4: relog. Nothing may come back.
  mate = await mk('Mate1'); await sleep(3500);
  const afterRelog = await rs();
  check('T4 relogging resurrects nothing: stored still 1', afterRelog.stored === 1, JSON.stringify(afterRelog));
  await say(op, '/kick Mate1', 600); await sleep(1500);

  // T5: the remaining reveal expires (30 s) and ONLY the sweep removes it. open drops to 0 first (lazy expiry); stored must follow within the next 20-tick sweep.
  let t0 = Date.now(), openZero = null, storedZero = null;
  for (let i = 0; i < 60; i++) {
    const s = await rs();
    if (openZero === null && s.open === 0) openZero = Date.now() - t0;
    if (s.stored === 0) { storedZero = Date.now() - t0; break; }
    await sleep(1000);
  }
  check('T5 the abandoned reveal EXPIRES (open reaches 0)', openZero !== null, 'after ' + openZero + ' ms');
  check('T5b ...and the SWEEP then removes it (stored reaches 0: needs tickReveals registered AND the % 20 gate true)', storedZero !== null, 'after ' + storedZero + ' ms');
  check('T5c the sweep runs promptly after expiry: stored is 0 within 3 s of open being 0', storedZero !== null && openZero !== null && storedZero - openZero <= 3000, `gap ${storedZero - openZero} ms`);

  // T6: no exceptions are checked by the grader on the server log; here, a control that a NEW reveal still works after all of the above.
  await say(op, '/emberfall relic gold EmberTester 500', 300);
  const c3 = await chestsFor('EmberTester');
  if (c3 && c3.closed >= 1) {
    await say(op, `/tp @s ${c3.x + 0.5} ${c3.y} ${c3.z + 2.5} 180 0`, 1500); await click(op, c3.x, c3.y, c3.z); await sleep(800);
    const again = await rs();
    check('T6 CONTROL: after sweep and forget a fresh open stores a fresh reveal: stored=1 open=1', again.stored === 1 && again.open === 1, JSON.stringify(again));
  } else check('T6 CONTROL: a third chest exists to open', false, JSON.stringify(c3));
  console.log('SUMMARY ' + (ran - fails) + ' of ' + ran);
  finish();
})();
