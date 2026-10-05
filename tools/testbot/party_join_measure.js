// MEASUREMENT, not a pass/fail test (issue #13 / V3 step 0). Two bots that are NOT EmberTester (PartyA, PartyB) are put in ONE
// live map run with the existing op command `/emberfall join <slot> <player>`. EmberTester is used only as the operator that
// starts the run and issues commands. It records what the server does; it fixes nothing and asserts nothing about the design.
//
//   R2  does a join into a RUNNING run reset the joiner (and touch the first player)?        (observations M1..M3)
//   R7  when one of two players leaves by the real path, does the other keep the run?         (observations M4..M6)
//   R4  who is credited for a kill next to one player? (gold is per player, see PickupSystem)  (observations M7..M8)
//
// Output lines start with "OBS" (what was seen) or "CTRL" (a control that must hold for the observations to mean anything).
// A CTRL line that says FAIL means the measurement is void; it is not a verdict on the mod.
const mineflayer = require('mineflayer');
const sleep = ms => new Promise(r => setTimeout(r, ms));
const mk = n => new Promise(res => { const b = mineflayer.createBot({ host: '127.0.0.1', port: 25565, username: n, version: '1.21.11', auth: 'offline' }); b.lines = []; b.on('message', m => b.lines.push(m.toString())); b.once('spawn', () => res(b)); b.on('error', e => console.log(n + ' connection error: ' + e.message)); });
const ask = async (b, l, w = 900) => { const n = b.lines.length; b.chat(l); await sleep(w); return b.lines.slice(n).join(' | '); };
let voided = 0;
const ctrl = (name, ok, note = '') => { console.log('CTRL ' + (ok ? 'ok   ' : 'FAIL ') + name + ' ' + note); if (!ok) voided++; };
const obs = (name, note) => console.log('OBS  ' + name + ' :: ' + note);
const parse = s => { const g = k => { const m = new RegExp(k + '=([^ ]+)').exec(s); return m ? m[1] : null; }; return { owned: (/owned=(\{[^}]*\})/.exec(s) || [])[1] || null, wallet: g('wallet'), hp: g('hp'), opened: g('opened'), price: g('price') }; };

(async () => {
  const op = await mk('EmberTester'), A = await mk('PartyA'), B = await mk('PartyB');
  await sleep(6000);
  const state = async n => parse(await ask(op, '/emberfall relic state ' + n, 700));
  await ask(op, '/emberfall hubactivate -29 76 -2', 3500);
  const started = await ask(op, '/expedition', 2500);
  ctrl('the operator started a run', /Expedition started/.test(started), started.slice(0, 80));
  await sleep(45000);                                   // map build
  const ws0 = await ask(op, '/emberfall wavestatus 0');
  ctrl('slot 0 has a wave director (a run exists)', /Slot 0: tier=/.test(ws0), ws0.slice(0, 90));

  // ---- R2: A joins first (nobody has anything yet), gets a relic and gold, then B joins the RUNNING run.
  obs('M0 join A', await ask(op, '/emberfall join 0 PartyA', 1500));
  // The operator's own /expedition put EmberTester INTO slot 0 as a member. It must leave, or it keeps the arena alive and the
  // "last player leaves" observation (M6) measures the operator, not the party (found on the first run of this file).
  obs('M-1 operator leaves slot 0 AFTER PartyA is in (if it left first, it would be the last member and the arena would be torn down)', await ask(op, '/expedition leave', 1500));
  ctrl('the operator is out of every run', /none/.test(await ask(op, '/emberfall relic chests EmberTester', 700)));
  ctrl('the run still exists after the operator left (a director for slot 0 exists)', /Slot 0: tier=/.test(await ask(op, '/emberfall wavestatus 0')), '');

  await ask(op, '/emberfall relic give PartyA ember_ledger', 600);
  await ask(op, '/emberfall relic gold PartyA 77', 600);
  const a1 = await state('PartyA');
  ctrl('A really holds the relic and 77 gold before B joins (else M1 proves nothing)', /ember_ledger/.test(a1.owned || '') && a1.wallet === '77', JSON.stringify(a1));
  obs('M1 A before B joins', JSON.stringify(a1));
  obs('M2 join B into the running run', await ask(op, '/emberfall join 0 PartyB', 1500));
  const a2 = await state('PartyA'), b2 = await state('PartyB');
  obs('M3a A after B joined', JSON.stringify(a2));
  obs('M3b B right after joining', JSON.stringify(b2));
  obs('M3c A unchanged by B joining?', String(a1.owned === a2.owned && a1.wallet === a2.wallet));
  // the joiner's own reset, same test on the already-equipped player: join A AGAIN (a late join of someone who has things)
  obs('M3d re-join A (has relic + gold) into the running run', await ask(op, '/emberfall join 0 PartyA', 1500));
  const a3 = await state('PartyA');
  obs('M3e A after being joined again', JSON.stringify(a3));
  obs('M3f late join wipes the joiner\'s relics/gold?', String(a3.owned !== a1.owned || a3.wallet !== a1.wallet));

  // ---- R7: B leaves by the REAL path (/expedition leave, which goes through RunEndHandler.finishRun), A stays.
  const wsBefore = await ask(op, '/emberfall wavestatus 0');
  obs('M4 B leaves', await ask(B, '/expedition leave', 1500));
  await sleep(1500);
  const wsAfter = await ask(op, '/emberfall wavestatus 0');
  obs('M5 wave director for slot 0 after B left, A still inside', wsAfter.slice(0, 110));
  ctrl('the director existed before B left', /Slot 0: tier=/.test(wsBefore), wsBefore.slice(0, 60));
  obs('M5b A still in the run (shares the chest map)?', await ask(op, '/emberfall relic chests PartyA', 700));
  obs('M5c B is out of the run?', await ask(op, '/emberfall relic chests PartyB', 700));
  obs('M5d B is at (home side)', String(B.entity && B.entity.position ? B.entity.position.toString() : 'n/a'));

  // ---- R7 (last player): A leaves too -> the arena must be torn down (server log "Tore down").
  obs('M6 A (last player) leaves', await ask(A, '/expedition leave', 1500));
  await sleep(3000);
  obs('M6b director after the last player left', (await ask(op, '/emberfall wavestatus 0')).slice(0, 90));
  console.log(voided === 0 ? 'MEASUREMENT COMPLETE (all controls held)' : 'MEASUREMENT VOID: ' + voided + ' control(s) failed');
  process.exit(0);
})().catch(e => { console.log('MEASUREMENT CRASHED ' + (e && e.stack || e)); process.exit(0); });
