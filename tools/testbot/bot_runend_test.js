// EmberTester #3 step 3b: a bot's run ENDS through the real RunEndHandler.finishRun and leaves cleanly. This covers the LEAVE path
// (bot removed from the run: cause null, no run-end screen). It does NOT cover death: /kill and /damage cannot end a bot's run (both
// leave it in the run, /damage replies 'Target is invulnerable to the given damage type', measured by runend_probe.js), so the
// "fallen" path with the run-end screen is UNTESTED for bots. Two bots share one run: Faller leaves, Stayer must be untouched.
// Server truth is the RUNEND_TEST trace (graded by runend_grade.sh); this file only drives and reports.
const mineflayer = require('mineflayer');
const sleep = ms => new Promise(r => setTimeout(r, ms));
const mk = name => new Promise(res => { const b = mineflayer.createBot({ host: '127.0.0.1', port: 25565, username: name, version: '1.21.11', auth: 'offline' }); b.chat_ = []; b.on('message', m => b.chat_.push(m.toString())); b.on('error', () => {}); b.once('spawn', () => res(b)); });
let fails = 0; const check = (n, ok, note = '') => { console.log((ok ? 'PASS ' : 'FAIL ') + n + ' ' + note); if (!ok) fails++; };
(async () => {
  const op = await mk('EmberTester'); await sleep(4000);
  const say = async (cmd, w = 900) => { op.chat_.length = 0; op.chat(cmd); await sleep(w); return op.chat_.join(' | '); };
  const bst = async n => (await say(`/emberfall bot state ${n}`, 700)).replace(/.*BOT state /, '');
  const field = (s, k) => (new RegExp(k + '=([^ ,]+)').exec(s) || [])[1];
  await say('/gamemode creative', 300);
  await say('/emberfall bot spawn Faller', 2000);
  await say('/emberfall bot spawn Stayer', 2000);
  const r1 = await say('/emberfall bot run Faller ranger', 1500);
  const r2 = await say('/emberfall bot run Stayer ranger', 1500);
  check('E0 both bots start a run', /run Faller ok/.test(r1) && /run Stayer ok/.test(r2), (r1 + r2).slice(0, 100));
  let a = '', b = '';
  for (let i = 0; i < 70; i++) { await sleep(2000); a = await bst('Faller'); b = await bst('Stayer'); if (/run=\d/.test(a) && /run=\d/.test(b) && /weapons=\w/.test(a)) break; }
  const slot = field(a, 'run'), slotB = field(b, 'run');
  check('E1 both are in a run', /^\d+$/.test(slot || '') && /^\d+$/.test(slotB || ''), `faller run=${slot} stayer run=${slotB}`);
  await say(`/emberfall wavestop ${slot}`, 500);
  await say('/kill @e[type=!player,type=!minecraft:item_display,type=!minecraft:interaction]', 600);
  // give the faller gold so "cleared at run end" is a change, not a 0 staying 0
  await say('/emberfall relic gold Faller 500', 500);
  await say('/emberfall relic gold Stayer 500', 500);
  const g0a = +field(await bst('Faller'), 'gold'), g0b = +field(await bst('Stayer'), 'gold');
  check('E2 both hold gold before the end', g0a >= 500 && g0b >= 500, `faller ${g0a} stayer ${g0b}`);
  // the leave path: the bot disconnects, which goes through handlePlayerLeftRun -> finishRun(null). Stayer is checked BEFORE its own cleanup.
  await say('/emberfall bot remove Faller', 1500); await sleep(2500);
  const a1 = await bst('Faller'), b1 = await bst('Stayer');
  check('E3 the faller is gone from the run (offline)', /not online/.test(a1), a1.slice(0, 90));
  check('E5 the stayer is UNTOUCHED: same run, gold kept (run-end is per player)', field(b1, 'run') === slotB && +field(b1, 'gold') >= 500, b1.slice(0, 90));
  await say('/emberfall bot remove Stayer', 1500);
  console.log(fails ? 'SOME FAIL ' + fails : 'CLIENT CHECKS PASS; the real verdict is runend_grade.sh on the server log');
  process.exit(0);
})();
