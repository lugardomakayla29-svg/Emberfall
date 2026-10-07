// MEASUREMENT, not a pass/fail test. Four EmberTesters in ONE run, NO resistance effect (they can die), watched for WINDOW seconds.
// Truth source for deaths is the SERVER LOG (RUNEND_TEST lines, testMode), not what the bots say about themselves.
// This script only starts the run and samples; the grader (party_survival_grade.sh) reads the log afterwards.
// FAIRNESS NOTE: bot names fix the offline UUIDs, so a personality-aware jar deals the SAME personalities on every run with these names.
// The OLD jar has no personalities and no spreading, so "same bots" does not apply across jars: this compares the two builds, not two identical bots.
const mineflayer = require('mineflayer'); const fs = require('fs');
const sleep = ms => new Promise(r => setTimeout(r, ms));
const WINDOW = +(process.env.WINDOW || 180);
const mk = name => new Promise(res => { const b = mineflayer.createBot({ host: '127.0.0.1', port: 25565, username: name, version: '1.21.11', auth: 'offline' }); b.chat_ = []; b.on('message', m => b.chat_.push(m.toString())); b.once('spawn', () => res(b)); });
(async () => {
  const op = await mk('EmberTester'); await sleep(3500);
  const say = async (cmd, w = 700) => { op.chat_.length = 0; op.chat(cmd); await sleep(w); return op.chat_.join(' | '); };
  const names = ['SA', 'SB', 'SC', 'SD'];
  await say('/gamemode creative', 300);
  for (const n of names) await say('/emberfall bot spawn ' + n, 1800);   // NOTE: deliberately NO resistance effect
  console.log('RUN', (await say('/emberfall bot run SA ranger', 1500)).slice(0, 60));
  for (let i = 0; i < 70; i++) { await sleep(2000); const s = await say('/emberfall bot state SA', 700); if (/run=\d/.test(s) && /weapons=\w/.test(s)) break; }
  for (const n of ['SB', 'SC', 'SD']) { await say('/character select ranger', 400); console.log('JOIN', n, (await say('/emberfall join 0 ' + n, 1500)).slice(0, 80)); }
  const t0 = Date.now(); console.log('WINDOW_START', new Date(t0).toISOString(), 'seconds', WINDOW);
  const rows = [];
  while ((Date.now() - t0) / 1000 < WINDOW) {
    await sleep(15000);
    const row = { t: Math.round((Date.now() - t0) / 1000) };
    for (const n of names) { const s = await say('/emberfall bot state ' + n, 450); const m = /hp=([\d.]+)/.exec(s), r = /run=(\S+)/.exec(s), l = /level=(\d+)/.exec(s); row[n] = /not online/.test(s) ? 'offline' : { hp: m ? +m[1] : '?', run: r ? r[1] : '?', lvl: l ? +l[1] : '?' }; }
    rows.push(row); console.log('SAMPLE', JSON.stringify(row));
  }
  fs.writeFileSync('/tmp/party_survival_samples.json', JSON.stringify(rows));
  console.log('WINDOW_END'); process.exit(0);
})();
