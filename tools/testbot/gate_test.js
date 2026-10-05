// Expedition Gate end to end. Verdicts for run starts/ends come from the SERVER LOG (gate_grade.py), not from chat.
const mineflayer = require('mineflayer');
const sleep = ms => new Promise(r => setTimeout(r, ms));
const X = -29, Y = 75, Z = -2;   // hearth; gate cell is (X, Y, Z-1); frame cell y = Y
const mk = name => new Promise(res => { const b = mineflayer.createBot({ host: '127.0.0.1', port: 25565, username: name, version: '1.21.11', auth: 'offline' }); b.chat_ = []; b.on('message', m => b.chat_.push(m.toString())); b.once('spawn', () => res(b)); });
let fails = 0; const check = (n, ok, note = '') => { console.log((ok ? 'PASS ' : 'FAIL ') + n + ' ' + note); if (!ok) fails++; };
(async () => {
  const op = await mk('EmberTester'); await sleep(5000);
  const say = async (b, cmd, w = 900) => { b.chat_.length = 0; b.chat(cmd); await sleep(w); return b.chat_.join(' | '); };
  const pos = async () => { const t = await say(op, '/data get entity EmberTester Pos', 500); const m = t.match(/\[(-?[\d.]+)d, (-?[\d.]+)d, (-?[\d.]+)d\]/); return m ? m.slice(1).map(Number) : null; };
  const dim = async () => (await say(op, '/data get entity EmberTester Dimension', 500)).includes('expedition') ? 'expedition' : 'other';
  await say(op, '/gamemode survival', 500); await say(op, '/effect give @s minecraft:resistance 999 4 true', 300);
  await say(op, '/gamemode creative', 500);
  await say(op, `/tp @s ${X + 0.5} ${Y + 1} ${Z + 6.5}`, 2500);
  await say(op, `/setblock ${X} ${Y + 1} ${Z} emberfall:ember_hearth`, 900);
  await say(op, `/emberfall hubactivate ${X} ${Y + 1} ${Z}`, 3500);
  await say(op, '/gamemode survival', 500); await say(op, '/effect give @s minecraft:resistance 999 4 true', 300);
  await say(op, '/character select juggernaut', 600);
  // T1: stand ON the gate frame for 8 s. The old plate started a run here.
  await say(op, `/tp @s ${X + 0.5} ${Y + 1} ${Z - 0.5}`, 1500);
  await sleep(8000);
  check('T1 standing on the gate frame starts nothing (still outside)', (await dim()) === 'other', '');
  // T2: click, countdown, run starts in about 3 s + map build
  console.log('CLICK1', (await say(op, '/emberfall hubclick hubact_gate', 700)).slice(0, 90));
  const c = await say(op, '/execute as @s run say ping', 300);
  for (let i = 0; i < 60; i++) { await sleep(2000); if ((await dim()) === 'expedition') break; }
  check('T2 a click, then a hold, puts the player in an expedition', (await dim()) === 'expedition', '');
  await sleep(2500);
  console.log('MARK leaving');
  await say(op, '/expedition leave', 1500);
  await sleep(1500);
  const p = await pos();
  check('T3 player is home (overworld) after leaving', (await dim()) === 'other', JSON.stringify(p));
  const dx = p ? p[0] - (X + 0.5) : 99, dz = p ? p[2] - (Z - 0.5) : 99;
  const d = Math.hypot(dx, dz);
  check('T3b the return point is BESIDE the gate: 0.4 to 1.7 blocks from it, not on it', d > 0.4 && d < 1.7, 'distance ' + d.toFixed(2));
  await sleep(14000);
  check('T3c 14 s later still home: no run restarted (the loop)', (await dim()) === 'other', '');
  // T4: lockout. A click straight after returning was long enough ago (>10 s), so prove the refusal with a fresh return.
  console.log('CLICK2', (await say(op, '/emberfall hubclick hubact_gate', 700)).slice(0, 90));
  for (let i = 0; i < 40; i++) { await sleep(2000); if ((await dim()) === 'expedition') break; }
  check('T4 after the lockout has passed, the gate works again', (await dim()) === 'expedition', '');
  await sleep(2000); await say(op, '/expedition leave', 1200);
  await sleep(600);
  const refuse = await say(op, '/emberfall hubclick hubact_gate', 800);
  check('T4b a click right after a run ends is refused (settling)', /settling/.test(refuse) || /ran=true/.test(refuse) && (await dim()) === 'other', refuse.slice(0, 100));
  await sleep(4000);
  check('T4c and no run started from that refused click', (await dim()) === 'other', '');
  // T5: moving away cancels. Wait out the lockout, click, walk 6 blocks.
  await sleep(9000);
  await say(op, '/emberfall hubclick hubact_gate', 600);
  await say(op, `/tp @s ${X + 6.5} ${Y + 1} ${Z - 0.5}`, 500);
  await sleep(6500);
  check('T5 walking away during the countdown cancels it', (await dim()) === 'other', '');
  console.log(fails === 0 ? 'ALL PASS (run starts/ends graded from the server log by gate_grade.py)' : 'SOME FAIL ' + fails);
  op.quit(); setTimeout(() => process.exit(0), 400);
})();
