// EmberTester #3 step 3: a bot WALKS to the Challenge Shrine and starts the trial through the real MapShrines code (the same open
// range check and the same onChoice as a real click). Server truth is the TEST_MODE trace "SHRINE_TEST challenge start" plus the
// bot's own position. Checks: it goes, it starts exactly ONE trial (the shrine is single-use), and it survives and keeps fighting.
const mineflayer = require('mineflayer');
const sleep = ms => new Promise(r => setTimeout(r, ms));
const mk = name => new Promise(res => { const b = mineflayer.createBot({ host: '127.0.0.1', port: 25565, username: name, version: '1.21.11', auth: 'offline' }); b.chat_ = []; b.on('message', m => b.chat_.push(m.toString())); b.on('error', () => {}); b.once('spawn', () => res(b)); });
let fails = 0; const check = (n, ok, note = '') => { console.log((ok ? 'PASS ' : 'FAIL ') + n + ' ' + note); if (!ok) fails++; };
(async () => {
  const op = await mk('EmberTester'); await sleep(4000);
  const say = async (cmd, w = 900) => { op.chat_.length = 0; op.chat(cmd); await sleep(w); return op.chat_.join(' | '); };
  const bst = async n => (await say(`/emberfall bot state ${n}`, 700)).replace(/.*BOT state /, '');
  await say('/gamemode creative', 300);
  await say('/emberfall bot spawn Pilgrim', 2000);
  await say('/effect give @a[name=Pilgrim] minecraft:resistance 999 4 true', 300);
  await say('/effect give @a[name=Pilgrim] minecraft:regeneration 999 4 true', 300);
  const r1 = await say('/emberfall bot run Pilgrim juggernaut', 1500);
  check('H0 the bot starts a run', /run Pilgrim ok/.test(r1), r1.slice(0, 80));
  let s = '';
  for (let i = 0; i < 70; i++) { await sleep(2000); s = await bst('Pilgrim'); if (/run=\d/.test(s) && /weapons=\w/.test(s)) break; }
  check('H1 the bot is in a run with a weapon', /run=\d/.test(s) && /weapons=\w/.test(s), s.slice(0, 110));
  const slot = (/run=(\d+)/.exec(s) || [])[1] || '0';
  const p0 = /pos=(-?[\d.]+),(-?[\d.]+)/.exec(s);
  console.log('INFO start position ' + (p0 ? p0[1] + ',' + p0[2] : '?'));
  // leave the arena's own mobs alone: they are the trial's business. The shrine is free, so nothing else is needed.
  // Give the bot up to 90 s to walk there and start the trial on its own.
  let moved = 0;
  for (let i = 0; i < 45; i++) {
    await sleep(2000);
    const now = await bst('Pilgrim'); const p = /pos=(-?[\d.]+),(-?[\d.]+)/.exec(now);
    if (p && p0) moved = Math.max(moved, Math.hypot(+p[1] - +p0[1], +p[2] - +p0[2]));
    if (i % 5 === 4) console.log('T+' + ((i + 1) * 2) + 's ' + now.slice(0, 95));
  }
  console.log('INFO furthest the bot got from its start: ' + moved.toFixed(1) + ' blocks');
  check('H2 context only: the bot is active (NOT proof of the shrine, it also walks to foes; the verdict is shrine_grade.sh)', moved > 10, 'moved ' + moved.toFixed(1));
  check('H3 the run is still alive at the end', /run=\d/.test(await bst('Pilgrim')), (await bst('Pilgrim')).slice(0, 60));
  await say('/emberfall bot remove Pilgrim', 1500);
  console.log(fails ? 'SOME FAIL ' + fails : 'CLIENT CHECKS PASS; the real verdict is shrine_grade.sh on the server log');
  console.log('SHRINE_SLOT ' + slot);
  process.exit(0);
})();
