// PHASE B: run against a server restarted on the SAME world. Reads what relic_test.js wrote in phase A.
const mineflayer = require('mineflayer');
const sleep = ms => new Promise(r => setTimeout(r, ms));
const b = mineflayer.createBot({ host: '127.0.0.1', port: 25565, username: 'EmberTester', version: '1.21.11', auth: 'offline' });
const chat = []; b.on('message', m => { const t = m.toString(); if (t.trim()) chat.push(t); });
let fails = 0; const check = (l, ok, e = '') => { console.log((ok ? 'PASS ' : 'FAIL ') + l + (e ? '  ' + e : '')); if (!ok) fails++; };
b.once('spawn', async () => {
  await sleep(5000); b.chat('/op EmberTester'); await sleep(600);
  const say = async (c, w = 900) => { chat.length = 0; b.chat(c); await sleep(w); return chat.join(' | '); };
  const u = await say('/emberfall relic unlocks EmberTester');
  check('the open_25_chests unlock (which opens ember_key) survived a server restart', /unlocks \[open_25_chests\]/.test(u), u.slice(0, 150));
  check('open_25_chests progress capped at the goal (25)', /open_25_chests=25/.test(u), '');
  check('partial progress (2 of 3 challenges) survived', /clear_3_challenges=2/.test(u), '');
  check('untouched counter stayed at 0', /reach_level_15=0/.test(u), '');
  // and it is still live: one more challenge unlocks the Anvil, exactly once
  const a = await say('/emberfall relic unlocks EmberTester add clear_3_challenges 1');
  check('the restored counter continues: 3rd challenge unlocks anvil_of_dawn', /unlocked-now \[anvil_of_dawn\]/.test(a), a.slice(0, 100));
  console.log(fails ? `RESULT: ${fails} FAILED` : 'RESULT: ALL PASSED');
  b.quit(); process.exit(fails ? 1 : 0);
});
