// Do several EmberTesters in one run converge on the same spot? Samples pairwise distances and per-bot yaw jitter over 40 s.
const mineflayer = require('mineflayer'); const fs = require('fs');
const sleep = ms => new Promise(r => setTimeout(r, ms));
const mk = name => new Promise(res => { const b = mineflayer.createBot({ host: '127.0.0.1', port: 25565, username: name, version: '1.21.11', auth: 'offline' }); b.chat_ = []; b.on('message', m => b.chat_.push(m.toString())); b.once('spawn', () => res(b)); });
(async () => {
  const op = await mk('EmberTester'); await sleep(3500);
  const say = async (cmd, w = 700) => { op.chat_.length = 0; op.chat(cmd); await sleep(w); return op.chat_.join(' | '); };
  const names = ['CA', 'CB', 'CC', 'CD'];
  await say('/gamemode creative', 300);
  for (const n of names) { await say('/emberfall bot spawn ' + n, 1800); await say(`/effect give @a[name=${n}] minecraft:resistance 999 4 true`, 250); await say(`/effect give @a[name=${n}] minecraft:regeneration 999 4 true`, 250); }
  console.log('RUN', 'CA', (await say('/emberfall bot run CA ranger', 1500)).slice(0, 60));
  for (let i = 0; i < 70; i++) { await sleep(2000); const s = await say('/emberfall bot state CA', 700); if (/run=\d/.test(s) && /weapons=\w/.test(s)) break; }
  for (const n of ['CB', 'CC', 'CD']) { await say('/character select ranger', 400); console.log('JOIN', n, (await say('/emberfall join 0 ' + n, 1500)).slice(0, 80)); }
  await sleep(6000);
  const pos = async n => { const m = /\[(-?[\d.]+)d, (-?[\d.]+)d, (-?[\d.]+)d\]/.exec(await say('/data get entity ' + n + ' Pos', 450)); return m ? { x: +m[1], z: +m[3] } : null; };
  const yaw = async n => { const m = /\[(-?[\d.]+)f/.exec(await say('/data get entity ' + n + ' Rotation', 450)); return m ? +m[1] : null; };
  const samples = [];
  for (let k = 0; k < 14; k++) {
    const ps = []; for (const n of names) ps.push(await pos(n));
    let minD = 1e9, sum = 0, c = 0; for (let i = 0; i < ps.length; i++) for (let j = i + 1; j < ps.length; j++) if (ps[i] && ps[j]) { const d = Math.hypot(ps[i].x - ps[j].x, ps[i].z - ps[j].z); minD = Math.min(minD, d); sum += d; c++; }
    if (k % 3 === 0) { const st = []; for (const n of names) st.push((await say('/emberfall bot state ' + n, 450)).replace(/.*BOT state /, '').replace(/tomes=\{[^}]*\} /, '').slice(0, 95)); console.log('STATE@' + k, st.join(' || ')); }
    samples.push({ minD: +minD.toFixed(2), meanD: +(sum / Math.max(1, c)).toFixed(2) });
    await sleep(1500);
  }
  fs.writeFileSync('/tmp/clump.json', JSON.stringify(samples));
  const mins = samples.map(s => s.minD), means = samples.map(s => s.meanD);
  console.log('PAIR min distance per sample:', mins.join(' '));
  console.log('PAIR mean distance per sample:', means.join(' '));
  console.log('RESULT clumped (min pair < 1.5 blocks in this many of ' + samples.length + '):', mins.filter(d => d < 1.5).length, ' | mean of means:', (means.reduce((a, b) => a + b, 0) / means.length).toFixed(2));
  process.exit(0);
})();
