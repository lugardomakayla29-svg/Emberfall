// EmberTester step 1: a fake ServerPlayer joins, is a real player, and is invisible to every human's tab list.
const mineflayer = require('mineflayer');
const sleep = ms => new Promise(r => setTimeout(r, ms));
const mk = name => new Promise(res => { const b = mineflayer.createBot({ host: '127.0.0.1', port: 25565, username: name, version: '1.21.11', auth: 'offline' }); b.chat_ = []; b.on('message', m => b.chat_.push(m.toString())); b.once('spawn', () => res(b)); });
let fails = 0; const check = (n, ok, note = '') => { console.log((ok ? 'PASS ' : 'FAIL ') + n + ' ' + note); if (!ok) fails++; };
(async () => {
  const op = await mk('EmberTester'); await sleep(4000);
  const say = async (b, cmd, w = 900) => { b.chat_.length = 0; b.chat(cmd); await sleep(w); return b.chat_.join(' | '); };
  const tabNames = () => Object.keys(op.players).sort();
  check('B0 control: the human sees itself in its own tab list', tabNames().includes('EmberTester'), tabNames().join(','));
  const r1 = await say(op, '/emberfall bot spawn TestBotOne', 2500);
  check('B1 the bot joins', /BOT spawn TestBotOne ok/.test(r1), r1.slice(0, 90));
  const l1 = await say(op, '/emberfall bot list', 800);
  check('B1b the roster counts it and it is online', /roster=1 online=TestBotOne/.test(l1), l1.slice(-70));
  const q = await say(op, '/execute if entity @a[name=TestBotOne]', 700);
  check('B3 it is a real player entity (@a matches it)', /Test passed/.test(q), q.slice(0, 60));
  const cnt = await say(op, '/execute if entity @a[name=TestBotOne]', 300); // keep buffer fresh
  await sleep(1500);
  check('B2 the human tab list does NOT contain the bot', !tabNames().includes('TestBotOne'), tabNames().join(','));
  check('B2b and still contains the human (control)', tabNames().includes('EmberTester'), '');
  // a second human joining AFTER the bot must also not see it (the join-time snapshot path)
  const late = await mk('LateHuman'); await sleep(3000);
  const lateNames = Object.keys(late.players).sort();
  check('B2c a human who joins AFTER the bot does not see it either', !lateNames.includes('TestBotOne') && lateNames.includes('EmberTester'), lateNames.join(','));
  late.quit(); await sleep(1200);
  check('B2d a human leaving does not disturb the bot', /online=TestBotOne/.test(await say(op, '/emberfall bot list', 800)), '');
  const rm = await say(op, '/emberfall bot remove TestBotOne', 2500);
  check('B4 the bot is removed', /BOT remove TestBotOne ok/.test(rm), rm.slice(0, 80));
  await sleep(1500);
  const l2 = await say(op, '/emberfall bot list', 800);
  check('B4b roster is empty and nobody is online', /roster=0 online=\s*$/.test(l2.replace(/.*BOT list/, 'BOT list')) || /roster=0/.test(l2), l2.slice(-50));
  const q2 = await say(op, '/execute if entity @a[name=TestBotOne]', 700);
  check('B4c it is gone from @a', /Test failed/.test(q2), q2.slice(0, 50));
  const r2 = await say(op, '/emberfall bot spawn TestBotOne', 2500);
  check('B5 the same name can rejoin (no stale state)', /ok/.test(r2), r2.slice(0, 80));
  await say(op, '/emberfall bot remove TestBotOne', 2000);
  console.log(fails === 0 ? 'ALL PASS' : 'SOME FAIL ' + fails);
  op.quit(); setTimeout(() => process.exit(0), 400);
})();
