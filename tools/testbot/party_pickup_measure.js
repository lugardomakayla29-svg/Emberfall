// MEASUREMENT, not a pass/fail test (issue #13 / V3, R4 "ownerless orbs"). Two non-EmberTester bots (PartyA, PartyB) in ONE live run.
// PickupSystem.tickLevel hands a pickup to the NEAREST run player within 1.6 blocks. This records who actually gets one, using the
// existing op command `/emberfall debugpickup <player> <gold|xp> <amount>` (drops it 2.5 blocks east of <player>). Fixes nothing.
//
// Lessons baked in (Koda's review of #13, and three of my own mistakes):
//  * A mineflayer client sends its own position and can undo a /tp, so each bot is PINNED: the operator re-issues /tp to the exact
//    spot every 400 ms for the whole contest, and the positions are confirmed by the SERVER (execute if entity ... distance), not
//    by arithmetic or by the bot's own view.
//  * `/tp` with a whole-number coordinate is block-centred (+0.5): "3" means 3.5. Every coordinate here is written with a .5 or .25.
//  * `debugpickup <player>` drops 2.5 east of that player's CURRENT position, so the drop point is computed from the pinned spot.
//  * The pickup list must be empty before a drop: wallets are read until stable for 3 s, so an earlier pickup cannot leak in.
//
// OBS = what was seen. CTRL = a control; a failed control voids the measurement, it is not a verdict on the mod.
const mineflayer = require('mineflayer');
const sleep = ms => new Promise(r => setTimeout(r, ms));
const mk = n => new Promise(res => { const b = mineflayer.createBot({ host: '127.0.0.1', port: 25565, username: n, version: '1.21.11', auth: 'offline' }); b.lines = []; b.on('message', m => b.lines.push(m.toString())); b.once('spawn', () => res(b)); b.on('error', e => console.log(n + ' connection error: ' + e.message)); });
const ask = async (b, l, w = 700) => { const n = b.lines.length; b.chat(l); await sleep(w); return b.lines.slice(n).join(' | '); };
let voided = 0;
const ctrl = (name, ok, note = '') => { console.log('CTRL ' + (ok ? 'ok   ' : 'FAIL ') + name + ' ' + note); if (!ok) voided++; };
const obs = (name, note) => console.log('OBS  ' + name + ' :: ' + note);

(async () => {
  const op = await mk('EmberTester'), A = await mk('PartyA'), B = await mk('PartyB');
  await sleep(6000);
  const wallet = async n => { const m = /wallet=(\d+)/.exec(await ask(op, '/emberfall relic state ' + n, 500)); return m ? +m[1] : NaN; };
  // Server-side yes/no: is <who> within <r> blocks of the point (x,y,z)? `execute if entity` prints "Test passed" or "Test failed".
  // (A bare `PartyB[distance=..1.6]` is a syntax error and silently read as "false" in my first version: use @a[name=...].)
  const near = async (who, x, y, z, r) => /Test passed/i.test(await ask(op, `/execute positioned ${x} ${y} ${z} if entity @a[name=${who},distance=..${r}]`, 500));
  const stable = async () => { let last = [NaN, NaN], same = 0; for (let i = 0; i < 12 && same < 3; i++) { const cur = [await wallet('PartyA'), await wallet('PartyB')]; same = (cur[0] === last[0] && cur[1] === last[1]) ? same + 1 : 0; last = cur; await sleep(700); } return same >= 3; };
  ctrl('the operator started a run', /Expedition started/.test(await ask(op, '/expedition', 2500)));
  await sleep(45000);
  await ask(op, '/emberfall join 0 PartyA', 1500);
  await ask(op, '/expedition leave', 1500);            // the operator must not stay a member (found on party_join_measure run 1)
  await ask(op, '/emberfall join 0 PartyB', 1500);
  ctrl('the operator is out of the run', /none/.test(await ask(op, '/emberfall relic chests EmberTester', 700)));
  ctrl('the run exists', /Slot 0: tier=/.test(await ask(op, '/emberfall wavestatus 0')));
  await ask(op, '/emberfall wavestop 0', 500);
  await ask(op, '/kill @e[type=!player]', 700);
  await ask(op, '/effect give PartyA minecraft:resistance 999 4 true', 300);
  await ask(op, '/effect give PartyB minecraft:resistance 999 4 true', 300);

  // Pinning: the operator re-sends /tp for both bots on a timer; `spot` is the only thing a case changes.
  const spot = { A: [0.5, 65, 0.5], B: [40.5, 65, 0.5] };
  let pinTimer = null;
  const pinOnce = () => { op.chat(`/tp PartyA ${spot.A.join(' ')}`); op.chat(`/tp PartyB ${spot.B.join(' ')}`); };
  pinTimer = setInterval(pinOnce, 400);
  const place = async (a, b) => { spot.A = a; spot.B = b; await sleep(1800); };

  // One contested drop. Drop is for PartyA and lands 2.5 east of A. The SERVER confirms the distances before and we compare to the winner.
  async function contest(label, a, b, drop = 'PartyA') {
    await place(a, b);
    const dropFor = drop === 'PartyA' ? a : b;
    const dx = dropFor[0] + 2.5, dy = dropFor[1] + 0.4, dz = dropFor[2];
    const nearA = await near('PartyA', dx, dy, dz, 1.6), nearB = await near('PartyB', dx, dy, dz, 1.6);
    const aNearer = Math.hypot(a[0] - dx, a[1] - dy, a[2] - dz), bNearer = Math.hypot(b[0] - dx, b[1] - dy, b[2] - dz);
    // The server must agree with the geometry on who is inside the 1.6 collect range, else the pin did not hold and the case is void.
    ctrl(label + ': the SERVER agrees who is within 1.6 of the drop (A=' + (aNearer <= 1.6) + ', B=' + (bNearer <= 1.6) + ')', nearA === (aNearer <= 1.6) && nearB === (bNearer <= 1.6), 'server A=' + nearA + ' B=' + nearB);
    ctrl(label + ': pickup list quiet (wallets stable 3 s) before the drop', await stable());
    const a0 = await wallet('PartyA'), b0 = await wallet('PartyB');
    await ask(op, `/emberfall debugpickup ${drop} gold 1`, 50); await sleep(2500);
    const a1 = await wallet('PartyA'), b1 = await wallet('PartyB');
    const got = (a1 > a0 ? 'A' : '') + (b1 > b0 ? 'B' : '') || 'nobody';
    const expected = Math.abs(aNearer - bNearer) < 0.01 ? 'tie' : (aNearer < bNearer ? 'A' : 'B');
    obs(label, `drop at (${dx},${dy.toFixed(1)},${dz}) | by geometry A ${aNearer.toFixed(2)} B ${bNearer.toFixed(2)} | server says within 1.6: A=${nearA} B=${nearB} | nearest=${expected} | winner=${got}`);
    return { expected, got, nearA, nearB };
  }

  // Controls: the simple cases must work or nothing below means anything.
  const c1 = await contest('P1 B far (40 blocks), drop for A', [0.5, 65, 0.5], [40.5, 65, 0.5]);
  ctrl('P1: A collects when B is far', c1.got === 'A');
  const c2 = await contest('P2 A far, drop for B', [40.5, 65, 0.5], [0.5, 65, 0.5], 'PartyB');
  ctrl('P2: B collects when A is far', c2.got === 'B');
  // Contested, each repeated, with the server confirming who is within the collect range.
  const rep = async (label, a, b, drop) => { for (let i = 1; i <= 3; i++) await contest(label + ' #' + i, a, b, drop); };
  await rep('P3 drop for A, B on the drop (0.5), A 2.5 away', [0.5, 65, 0.5], [3.5, 65, 0.5], 'PartyA');
  await rep('P4 drop for B, A on the drop (0.5), B 2.5 away', [6.5, 65, 0.5], [3.5, 65, 0.5], 'PartyB');
  await rep('P5 TIE: drop for A at 3.0, A 2.5 west and B 2.5 east of it', [0.5, 65, 0.5], [5.5, 65, 0.5], 'PartyA');
  clearInterval(pinTimer);
  console.log(voided === 0 ? 'MEASUREMENT COMPLETE (all controls held)' : 'MEASUREMENT VOID: ' + voided + ' control(s) failed');
  process.exit(0);
})().catch(e => { console.log('MEASUREMENT CRASHED ' + (e && e.stack || e)); process.exit(0); });
