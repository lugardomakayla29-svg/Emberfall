// EmberTester #3 step 3c: a bot's run ENDS BY DEATH. Before the invulnerability fix this was impossible: ServerPlayer.isInvulnerableTo is true
// while the connection never got its client acks, so /damage said 'Target is invulnerable' and a bot run could never end by dying
// (runend_probe.js). Two bots share one run: Victim takes a lethal hit, Bystander must be untouched. A control checks the damage is real.
// EXPECTED_CHECKS is the number a complete run makes; fewer is a failure, never a pass (the #17 rule).
const mineflayer = require('mineflayer');
const sleep = ms => new Promise(r => setTimeout(r, ms));
const mk = name => new Promise(res => { const b = mineflayer.createBot({ host: '127.0.0.1', port: 25565, username: name, version: '1.21.11', auth: 'offline' }); b.chat_ = []; b.on('message', m => b.chat_.push(m.toString())); b.on('error', () => {}); b.once('spawn', () => res(b)); });
const EXPECTED_CHECKS = 6;
let fails = 0, ran = 0; const check = (n, ok, note = '') => { ran++; console.log((ok ? 'PASS ' : 'FAIL ') + n + ' ' + note); if (!ok) fails++; };
const finish = () => { const short = ran < EXPECTED_CHECKS; console.log(fails === 0 && !short ? `ALL PASS (${ran} checks)` : `SOME FAIL ${fails}` + (short ? ` (only ${ran} of ${EXPECTED_CHECKS} checks ran)` : '')); return fails === 0 && !short ? 0 : 1; };
const timer = setTimeout(() => { console.log('FAIL suite timed out'); fails++; process.exit(finish()); }, 240000);
(async () => {
  const op = await mk('EmberTester'); await sleep(4000);
  const say = async (cmd, w = 900) => { op.chat_.length = 0; op.chat(cmd); await sleep(w); return op.chat_.join(' | '); };
  const bst = async n => (await say(`/emberfall bot state ${n}`, 700)).replace(/.*BOT state /, '');
  const field = (s, k) => (new RegExp(k + '=([^ ,]+)').exec(s) || [])[1];
  await say('/gamemode creative', 300);
  await say('/emberfall bot spawn Victim', 2000);
  await say('/emberfall bot spawn Bystander', 2000);
  const r1 = await say('/emberfall bot run Victim ranger', 1500);
  const r2 = await say('/emberfall bot run Bystander ranger', 1500);
  check('D0 both bots start a run', /run Victim ok/.test(r1) && /run Bystander ok/.test(r2), (r1 + r2).slice(0, 100));
  let a = '', b = '';
  for (let i = 0; i < 70; i++) { await sleep(2000); a = await bst('Victim'); b = await bst('Bystander'); if (/run=\d/.test(a) && /run=\d/.test(b) && /weapons=\w/.test(a)) break; }
  const slot = field(a, 'run'), slotB = field(b, 'run');
  check('D1 both are in a run', /^\d+$/.test(slot || '') && /^\d+$/.test(slotB || ''), `victim run=${slot} bystander run=${slotB}`);
  await say(`/emberfall wavestop ${slot}`, 500);
  await say('/kill @e[type=!player,type=!minecraft:item_display,type=!minecraft:interaction]', 600);
  // CONTROL: the damage must be real inside a run (this is the exact thing that used to answer 'invulnerable').
  const hit = await say('/damage Victim 1 minecraft:generic', 900);
  check('D2 CONTROL: a small hit inside a run is APPLIED (not invulnerable)', /Applied/.test(hit) && !/invulnerable/i.test(hit), hit.slice(0, 80));
  const stayerHp0 = field(await bst('Bystander'), 'hp');
  // the lethal hit
  const kill = await say('/damage Victim 1000 minecraft:generic', 1200); await sleep(3000);
  check('D3 the lethal hit is applied', /Applied/.test(kill) && !/invulnerable/i.test(kill), kill.slice(0, 80));
  const a1 = await bst('Victim'), b1 = await bst('Bystander');
  const gone = !/run=\d/.test(a1) || /run=-1|run=none/.test(a1);
  check('D4 the victim is no longer in the run (death ended it)', gone, a1.slice(0, 90));
  check('D5 the bystander is UNTOUCHED: same run, same health (run-end is per player)', field(b1, 'run') === slotB && field(b1, 'hp') === stayerHp0, `${b1.slice(0, 70)} hp0=${stayerHp0}`);
  await say('/emberfall bot remove Victim', 1500); await say('/emberfall bot remove Bystander', 1500);
  clearTimeout(timer);
  const code = finish();
  process.exit(code);
})().catch(e => { console.log('FAIL suite crashed: ' + (e && e.stack || e)); process.exit(1); });
