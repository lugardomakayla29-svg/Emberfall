// EmberTester step 3: a bot starts a run as a character and answers its own level-up screens through the real
// managers. Control: a second bot that is never given XP must own no tome.
const mineflayer = require('mineflayer');
const sleep = ms => new Promise(r => setTimeout(r, ms));
const mk = name => new Promise(res => { const b = mineflayer.createBot({ host: '127.0.0.1', port: 25565, username: name, version: '1.21.11', auth: 'offline' }); b.chat_ = []; b.on('message', m => b.chat_.push(m.toString())); b.once('spawn', () => res(b)); });
let fails = 0; const check = (n, ok, note = '') => { console.log((ok ? 'PASS ' : 'FAIL ') + n + ' ' + note); if (!ok) fails++; };
(async () => {
  const op = await mk('EmberTester'); await sleep(4000);
  const say = async (cmd, w = 900) => { op.chat_.length = 0; op.chat(cmd); await sleep(w); return op.chat_.join(' | '); };
  const state = async n => (await say(`/emberfall bot state ${n}`, 700)).replace(/.*BOT state /, '');
  await say('/gamemode creative', 300);
  await say('/emberfall bot spawn BrainBot', 2000);
  await say('/emberfall bot spawn ControlBot', 2000);
  await say('/effect give @a[name=BrainBot] minecraft:resistance 999 4 true', 300);
  await say('/effect give @a[name=BrainBot] minecraft:regeneration 999 4 true', 300);
  const run = await say('/emberfall bot run BrainBot ranger', 1500);
  check('R1 the bot starts a run as a character', /BOT run BrainBot ok/.test(run), run.slice(0, 110));
  let s = '';
  for (let i = 0; i < 70; i++) { await sleep(2000); s = await state('BrainBot'); if (/run=\d/.test(s) && /weapons=\w/.test(s)) break; }
  check('R1b the bot is in a run with a starting weapon (it answered the weapon screen itself)', /run=\d/.test(s) && /weapons=\w/.test(s), s.slice(0, 160));
  const before = await state('BrainBot');
  check('R2 before any level-up the bot owns no tome', /tomes=\{\}/.test(before), before.slice(0, 120));
  await say('/xp add @a[name=BrainBot] 3 levels', 500);
  let after = '';
  for (let i = 0; i < 20; i++) { await sleep(1500); after = await state('BrainBot'); if (!/tomes=\{\}/.test(after)) break; }
  check('R3 after a level-up the bot answered the tome offer and owns a tome', !/tomes=\{\}/.test(after) && /tomes=\{/.test(after), after.slice(0, 200));
  const ctl = await state('ControlBot');
  check('R4 control: the bot that was never leveled owns no tome and has no run', /tomes=\{\}/.test(ctl) && /run=null/.test(ctl), ctl.slice(0, 140));
  await say('/emberfall bot remove BrainBot', 1500); await say('/emberfall bot remove ControlBot', 1500);
  console.log(fails === 0 ? 'ALL PASS' : 'SOME FAIL ' + fails);
  op.quit(); setTimeout(() => process.exit(0), 400);
})();
