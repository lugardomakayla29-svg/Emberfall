// MEASUREMENT, not a pass/fail test (issue #13 / V3, R4 "ownerless orbs"). Two non-EmberTester bots (PartyA, PartyB) in ONE live run.
// A pickup has no owner: PickupSystem.tickLevel hands it to the NEAREST run player. This records who actually gets it, using the
// existing op command `/emberfall debugpickup <player> <gold|xp> <amount>` (drops it 2.5 blocks east of <player>). Fixes nothing.
//
//   P1  pickup dropped beside A, B far away      -> A should get it            (control: the plain case works)
//   P2  pickup dropped beside B, A far away      -> B should get it            (control: symmetry, so P1 is not "A always wins")
//   P3  pickup dropped beside A, B standing closer to the drop than A -> who gets it?  (the ownerless case)
//
// OBS = what was seen. CTRL = a control; a failed control voids the measurement, it is not a verdict on the mod.
const mineflayer = require('mineflayer');
const sleep = ms => new Promise(r => setTimeout(r, ms));
const mk = n => new Promise(res => { const b = mineflayer.createBot({ host: '127.0.0.1', port: 25565, username: n, version: '1.21.11', auth: 'offline' }); b.lines = []; b.on('message', m => b.lines.push(m.toString())); b.once('spawn', () => res(b)); b.on('error', e => console.log(n + ' connection error: ' + e.message)); });
const ask = async (b, l, w = 900) => { const n = b.lines.length; b.chat(l); await sleep(w); return b.lines.slice(n).join(' | '); };
let voided = 0;
const ctrl = (name, ok, note = '') => { console.log('CTRL ' + (ok ? 'ok   ' : 'FAIL ') + name + ' ' + note); if (!ok) voided++; };
const obs = (name, note) => console.log('OBS  ' + name + ' :: ' + note);

(async () => {
  const op = await mk('EmberTester'), A = await mk('PartyA'), B = await mk('PartyB');
  await sleep(6000);
  const wallet = async n => { const m = /wallet=(\d+)/.exec(await ask(op, '/emberfall relic state ' + n, 700)); return m ? +m[1] : NaN; };
  const pos = async n => { const r = await ask(op, '/data get entity ' + n + ' Pos', 700); const m = /\[([-\d.]+)d, ([-\d.]+)d, ([-\d.]+)d\]/.exec(r); return m ? m.slice(1).map(Number) : null; };
  await ask(op, '/emberfall hubactivate -29 76 -2', 3500);
  ctrl('the operator started a run', /Expedition started/.test(await ask(op, '/expedition', 2500)));
  await sleep(45000);
  await ask(op, '/emberfall join 0 PartyA', 1500);
  await ask(op, '/expedition leave', 1500);            // the operator must not stay a member (found on party_join_measure run 1)
  await ask(op, '/emberfall join 0 PartyB', 1500);
  ctrl('the operator is out of the run', /none/.test(await ask(op, '/emberfall relic chests EmberTester', 700)));
  ctrl('the run exists', /Slot 0: tier=/.test(await ask(op, '/emberfall wavestatus 0')));
  // Keep the run quiet and the players alive so wave mobs cannot muddy the wallet reads.
  await ask(op, '/emberfall wavestop 0', 500);
  await ask(op, '/kill @e[type=!player]', 700);
  await ask(op, '/effect give PartyA minecraft:resistance 999 4 true', 300);
  await ask(op, '/effect give PartyB minecraft:resistance 999 4 true', 300);
  await ask(op, '/gamemode survival PartyA', 300); await ask(op, '/gamemode survival PartyB', 300);

  // Put the two players far apart, on the same Y, in open ground of the arena (positions are read back, not assumed).
  const pa0 = await pos('PartyA');
  ctrl('read A\'s position', !!pa0, JSON.stringify(pa0));
  if (!pa0) { console.log('MEASUREMENT VOID: no position'); process.exit(0); }
  const [x, y, z] = pa0;
  await ask(op, `/tp PartyB ${x + 40} ${y} ${z}`, 1200);
  const pa = await pos('PartyA'), pb = await pos('PartyB');
  const gap = pa && pb ? Math.hypot(pa[0] - pb[0], pa[2] - pb[2]) : NaN;
  ctrl('A and B are about 40 blocks apart (so P1/P2 are unambiguous)', gap > 30 && gap < 50, 'gap=' + gap.toFixed(1));

  // ---- P1: drop beside A. B is 40 blocks away. Expect A.
  let a0 = await wallet('PartyA'), b0 = await wallet('PartyB');
  await ask(op, '/emberfall debugpickup PartyA gold 7', 300); await sleep(2500);
  let a1 = await wallet('PartyA'), b1 = await wallet('PartyB');
  obs('P1 gold 7 dropped beside A (B 40 away): A ' + a0 + '->' + a1 + ', B ' + b0 + '->' + b1, 'A got ' + (a1 - a0) + ', B got ' + (b1 - b0));

  // ---- P2: drop beside B. A is 40 away. Expect B.
  a0 = a1; b0 = b1;
  await ask(op, '/emberfall debugpickup PartyB gold 5', 300); await sleep(2500);
  a1 = await wallet('PartyA'); b1 = await wallet('PartyB');
  obs('P2 gold 5 dropped beside B (A 40 away): A ' + a0 + '->' + a1 + ', B ' + b0 + '->' + b1, 'A got ' + (a1 - a0) + ', B got ' + (b1 - b0));

  // ---- P3: the ownerless case. The drop is 2.5 east of A. Stand B right ON the drop point, A stays where it was.
  //   Nearest wins, so B (0 away) should beat A (2.5 away) even though the drop was "made for" A.
  const pa3 = await pos('PartyA');
  await ask(op, `/tp PartyB ${pa3[0] + 2.5} ${pa3[1]} ${pa3[2]}`, 1200);
  // Read both positions BACK: on the first run B's teleport was assumed, not checked, and the result contradicted the prediction.
  const paD = await pos('PartyA'), pbD = await pos('PartyB');
  const dropX = paD[0] + 2.5;   // debugpickup drops 2.5 blocks east (+X) of the named player
  const dA = Math.hypot(paD[0] + 2.5 - paD[0], paD[2] - paD[2]), dB = Math.hypot(pbD[0] - dropX, pbD[2] - paD[2]);
  obs('P3 positions before the drop', 'A=' + JSON.stringify(paD) + ' B=' + JSON.stringify(pbD) + ' | A->drop ' + dA.toFixed(2) + ', B->drop ' + dB.toFixed(2));
  // Both must be run members right now: a non-member is not in PickupSystem's runner list and could never collect.
  obs('P3 membership', 'A: ' + (await ask(op, '/emberfall relic chests PartyA', 600)).slice(0, 40) + ' | B: ' + (await ask(op, '/emberfall relic chests PartyB', 600)).slice(0, 40));
  ctrl('B really is closer to the drop point than A (else P3 says nothing about "nearest")', dB < dA - 1.0, 'A ' + dA.toFixed(2) + ' vs B ' + dB.toFixed(2));
  a0 = await wallet('PartyA'); b0 = await wallet('PartyB');
  await ask(op, '/emberfall debugpickup PartyA gold 11', 50); await sleep(2500);
  const paE = await pos('PartyA'), pbE = await pos('PartyB');   // where they are AFTER the drop (a bot client can snap back after a /tp)
  obs('P3 positions after the drop', 'A=' + JSON.stringify(paE) + ' B=' + JSON.stringify(pbE));
  a1 = await wallet('PartyA'); b1 = await wallet('PartyB');
  obs('P3 gold 11 dropped beside A, B standing ON the drop: A ' + a0 + '->' + a1 + ', B ' + b0 + '->' + b1, 'A got ' + (a1 - a0) + ', B got ' + (b1 - b0));
  // ---- P4: the mirror of P3. Drop beside B (2.5 east of B), put A ON the drop point. If the winner is always the one the drop
  //   was "made for" (or always the first player in the list), B wins here; if it is by distance, A wins.
  const pbF = await pos('PartyB');
  await ask(op, `/tp PartyA ${pbF[0] + 2.5} ${pbF[1]} ${pbF[2]}`, 1200);
  const paG = await pos('PartyA'), pbG = await pos('PartyB');
  const dB4 = 2.5, dA4 = Math.hypot(paG[0] - (pbG[0] + 2.5), paG[2] - pbG[2]);
  obs('P4 positions before the drop', 'A=' + JSON.stringify(paG) + ' B=' + JSON.stringify(pbG) + ' | B->drop ' + dB4.toFixed(2) + ', A->drop ' + dA4.toFixed(2));
  ctrl('A really is closer to the drop point than B', dA4 < dB4 - 1.0, 'A ' + dA4.toFixed(2) + ' vs B ' + dB4.toFixed(2));
  a0 = await wallet('PartyA'); b0 = await wallet('PartyB');
  await ask(op, '/emberfall debugpickup PartyB gold 13', 50); await sleep(2500);
  a1 = await wallet('PartyA'); b1 = await wallet('PartyB');
  obs('P4 gold 13 dropped beside B, A standing ON the drop: A ' + a0 + '->' + a1 + ', B ' + b0 + '->' + b1, 'A got ' + (a1 - a0) + ', B got ' + (b1 - b0));
  console.log(voided === 0 ? 'MEASUREMENT COMPLETE (all controls held)' : 'MEASUREMENT VOID: ' + voided + ' control(s) failed');
  process.exit(0);
})().catch(e => { console.log('MEASUREMENT CRASHED ' + (e && e.stack || e)); process.exit(0); });
