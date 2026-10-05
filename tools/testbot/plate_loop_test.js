// REPRODUCTION of the plate loop: a player steps on the departure plate, a run starts, the run ends
// (leave), the player is put back where the plate fired (= ON the plate) and the plate starts a NEW run.
const mineflayer = require('mineflayer');
const sleep = ms => new Promise(r => setTimeout(r, ms));
const X = -29, Y = 75, Z = -2;
const mk = name => new Promise(res => { const b = mineflayer.createBot({ host: '127.0.0.1', port: 25565, username: name, version: '1.21.11', auth: 'offline' }); b.chat_ = []; b.on('message', m => { const t = m.toString(); if (t.trim()) b.chat_.push(t); }); b.once('spawn', () => res(b)); });
(async () => {
  const op = await mk('EmberTester'); await sleep(5000);
  const say = async (b, cmd, w = 1000) => { b.chat_.length = 0; b.chat(cmd); await sleep(w); return b.chat_.join(' | '); };
  await say(op, '/gamemode creative', 700);
  await say(op, `/tp @s ${X + 0.5} ${Y + 1} ${Z + 6.5}`, 2500);
  await say(op, `/setblock ${X} ${Y + 1} ${Z} emberfall:ember_hearth`, 900);
  await say(op, `/emberfall hubactivate ${X} ${Y + 1} ${Z}`, 3000);
  const pl = await mk('PlainPlayer'); await sleep(5000);
  const inRun = async () => !(await say(pl, '/expedition leave', 1200)).includes('not on an expedition');
  // NOTE: /expedition leave itself ends a run, so probe with the server-side query instead when possible.
  await say(op, `/tp PlainPlayer ${X + 0.5} ${Y + 1} ${Z - 0.5}`, 2500);
  await sleep(45000); // the first map build takes ~30 s
  const where1 = await say(op, '/data get entity PlainPlayer Dimension', 900);
  console.log('1 after stepping on plate   :', where1.slice(0, 90));
  const left = await say(pl, '/expedition leave', 400);
  console.log('2 left the run              :', left.slice(0, 70));
  // Sample dimension + position every 1.5 s for 20 s, so the order of events is visible instead of guessed.
  const rows = [];
  for (let i = 0; i < 14; i++) {
    const d = (await say(op, '/data get entity PlainPlayer Dimension', 500)).replace(/.*data: /, '');
    const q = (await say(op, '/data get entity PlainPlayer Pos', 500)).replace(/.*data: /, '');
    rows.push(`${String(i * 1.5).padStart(4)} s  ${d.padEnd(24)} ${q}`);
    await sleep(500);
  }
  console.log(rows.join('\n'));
  console.log('plate cell is', X, Y + 1, Z - 1, '(stand point', X + 0.5, Y + 1, Z - 0.5, ')');
  const dims = rows.map(r => /expedition/.test(r));
  const backHome = rows.some(r => /overworld/.test(r));
  const reRun = backHome && dims.lastIndexOf(true) > rows.findIndex(r => /overworld/.test(r));
  console.log(reRun ? 'RESULT: LOOP REPRODUCED (home, then into a new run with no input)' : backHome ? 'RESULT: went home and stayed home' : 'RESULT: never reached the overworld');
  pl.quit(); op.quit(); process.exit(0);
})();
