// MEASUREMENT, not a pass/fail test (issue #13 / V3 step 0, the five-bot part). Five bots that are NOT EmberTester (Party1..Party5)
// are put in ONE live map run with the existing op command `/emberfall join <slot> <player>`. EmberTester is only the operator that
// starts the run and issues commands, and it leaves the run before anything is measured. It records what the server does; it
// fixes nothing and asserts nothing about the design. New file only: nothing in bot/, src/ or any existing suite is touched.
//
//   F1  do the command and the run accept FIVE names (the plan says this is untested; checked first)?
//   F2  do mobs target all five? (/emberfall debugaggro prints each mob's target; ONE query, each mob counted once)
//   (A spread-out variant, F2b, was tried and REMOVED: /tp to the player's own coordinates sent the party to the hub spawn, outside the
//   arena, so it measured nothing and it also left the players outside the run when F4 ran. The five stand together in this file.)
//   F3  what does the WaveDirector do with five players inside? (/emberfall wavestatus, sampled twice; totals only)
//   F4  what does each of the five get on leaving? (+N Silver in the message of /emberfall leave <player>, EmberfallCommands:806; the
//       player's own /expedition leave prints no Silver, RunCommand:158, which my first version of this file got wrong)
//   F5  server tick time with five players (vanilla /tick query), sampled during the fight.
//
// Output lines start with "OBS" (what was seen) or "CTRL" (a control that must hold for the observations to mean anything).
// A CTRL line that says FAIL means the measurement is void; it is not a verdict on the mod.
// Not measured here, on purpose: caps 40 / 60 / 80 (the cap is a constant in WaveDirector and changing it means editing src/).
const mineflayer = require('mineflayer');
const sleep = ms => new Promise(r => setTimeout(r, ms));
const NAMES = ['Party1', 'Party2', 'Party3', 'Party4', 'Party5'];
const mk = n => new Promise(res => { const b = mineflayer.createBot({ host: '127.0.0.1', port: 25565, username: n, version: '1.21.11', auth: 'offline' }); b.lines = []; b.on('message', m => b.lines.push(m.toString())); b.on('error', e => console.log('ERR ' + n + ' ' + e.message)); b.once('spawn', () => res(b)); });
const ask = async (b, l, w = 900) => { const n = b.lines.length; b.chat(l); await sleep(w); return b.lines.slice(n).join(' | '); };
let voided = 0;
const ctrl = (name, ok, note = '') => { console.log('CTRL ' + (ok ? 'ok   ' : 'FAIL ') + name + ' ' + note); if (!ok) voided++; };
const obs = (name, note) => console.log('OBS  ' + name + ' :: ' + note);

(async () => {
  const op = await mk('EmberTester');
  const bots = {}; for (const n of NAMES) bots[n] = await mk(n);
  await sleep(6000);
  ctrl('all five party bots are online and none of them is EmberTester', NAMES.every(n => bots[n].entity) && !NAMES.includes('EmberTester'), NAMES.map(n => n + ':' + (bots[n].entity ? 'in' : 'NOT')).join(' '));
  const started = await ask(op, '/expedition', 2500);
  ctrl('the operator started a run', /Expedition started/.test(started), started.slice(0, 80));
  await sleep(45000);                                   // map build
  const ws0 = await ask(op, '/emberfall wavestatus 0');
  ctrl('slot 0 has a wave director (a run exists)', /Slot 0: tier=/.test(ws0), ws0.slice(0, 90));

  // ---- F1: five joins. The operator joins nobody; each reply is recorded as seen.
  const first = await ask(op, '/emberfall join 0 ' + NAMES[0], 1500);
  obs('F1 join ' + NAMES[0], first);
  // the operator's own /expedition put EmberTester into slot 0; it leaves AFTER the first party bot is in (else it would be the last member)
  obs('F1 operator leaves slot 0', await ask(op, '/expedition leave', 1500));
  for (const n of NAMES.slice(1)) obs('F1 join ' + n, await ask(op, '/emberfall join 0 ' + n, 1500));
  let inRun = 0;
  for (const n of NAMES) { const r = await ask(op, '/emberfall relic chests ' + n, 700); const ok = !/none/.test(r); if (ok) inRun++; obs('F1 ' + n + ' is in the run?', String(ok) + ' :: ' + r.slice(0, 70)); }
  ctrl('the operator is out of every run', /none/.test(await ask(op, '/emberfall relic chests EmberTester', 700)));
  obs('F1 RESULT', inRun + ' of 5 are in the run');
  ctrl('the run still exists after the operator left', /Slot 0: tier=/.test(await ask(op, '/emberfall wavestatus 0')), '');
  if (inRun < 2) { console.log('MEASUREMENT VOID: fewer than 2 party bots in the run, F2..F5 would measure nothing'); process.exit(0); }

  // ---- F2/F3/F5: let the director run with the party inside, sample three times
  await ask(op, '/gamemode survival ' + NAMES[0], 300);
  for (const n of NAMES) await ask(op, '/effect give ' + n + ' minecraft:resistance 999 4 true', 200);
  await ask(op, '/emberfall wavestatus 0', 300);
  const w1 = await ask(op, '/emberfall wavestatus 0');
  obs('F3 wavestatus at t0', w1.slice(0, 140));
  await sleep(40000);
  const pos = n => { const e = bots[n].entity; return e ? e.position : null; };
  const spread = () => { const ps = NAMES.map(pos).filter(Boolean); let m = 0; for (const p of ps) for (const q of ps) m = Math.max(m, p.distanceTo(q)); return m; };
  for (const sample of [1, 2, 3]) {
    // ONE debugaggro query. Every bot stands at the same spot, so a query per player lists the same mobs; counting five lists
    // five times (my first version) credited one player with all of them. Count each mob once, from one list.
    const r = await ask(op, '/emberfall debugaggro ' + NAMES[0], 2500);
    const m = /aggro summary: (\d+) mobs, (\d+) with a target/.exec(r);
    const byTarget = {}; for (const t of r.matchAll(/target=([A-Za-z0-9_]+)/g)) byTarget[t[1]] = (byTarget[t[1]] || 0) + 1;
    obs('F2 sample ' + sample + ' mobs within 60 blocks of ' + NAMES[0] + ' / with a target', m ? m[1] + ' / ' + m[2] : 'no summary line :: ' + r.slice(0, 120));
    obs('F2 sample ' + sample + ' target counts, one list, each mob once', JSON.stringify(byTarget));
    obs('F2 sample ' + sample + ' how far apart are the five bots (blocks, max pair)', spread().toFixed(1) + '  (near 0 means the five cannot be told apart by distance)');
    obs('F5 sample ' + sample + ' tick', (await ask(op, '/tick query', 900)).replace(/\n/g, ' ').slice(0, 200));
    obs('F3 sample ' + sample + ' wavestatus', (await ask(op, '/emberfall wavestatus 0')).slice(0, 140));
    await sleep(20000);
  }

  // ---- F4: each of the five leaves by the real path; the message carries the Silver
  const pay = {};
  for (const n of NAMES) { const r = await ask(op, '/emberfall leave ' + n, 1500); const m = /\+(\d+) Silver/.exec(r); pay[n] = m ? +m[1] : null; obs('F4 ' + n + ' leaves', r.slice(0, 110)); }
  obs('F4 Silver per player', JSON.stringify(pay) + '  (equal values are NOT a finding: nobody fought, so the formula inputs may be identical)');
  await sleep(3000);
  obs('F6 director after the last player left', (await ask(op, '/emberfall wavestatus 0')).slice(0, 90));
  console.log(voided === 0 ? 'MEASUREMENT COMPLETE (all controls held)' : 'MEASUREMENT VOID: ' + voided + ' control(s) failed');
  process.exit(0);
})().catch(e => { console.log('MEASUREMENT CRASHED ' + (e && e.stack || e)); process.exit(0); });
