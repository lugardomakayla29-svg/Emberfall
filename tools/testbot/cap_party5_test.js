// Issue #4 / #13: tick time with FIVE players in ONE run and a full arena, at the cap given by the server flag -Demberfall.hostileCap.
// CAP env = the same number as the flag (default 40). Five EmberBots (no network) share slot 0: Anchor starts the run, Mate1..Mate4 are
// put in with the real op command `/emberfall join 0 <name>`. Threat is pushed to 20 so the director is at its spawn floor and the CAP, not
// the clock, limits the count. We read /tick query three times with the arena full. ONE sample per cap: treat as a ceiling check, not a trend.
const mineflayer = require('mineflayer');
const sleep = ms => new Promise(r => setTimeout(r, ms));
const CAP = parseInt(process.env.CAP || '40', 10);
const mk = name => new Promise(res => { const b = mineflayer.createBot({ host: '127.0.0.1', port: 25565, username: name, version: '1.21.11', auth: 'offline' }); b.chat_ = []; b.on('message', m => b.chat_.push(m.toString())); b.on('error', () => {}); b.once('spawn', () => res(b)); });
const EXPECTED = 7; let ran = 0, fails = 0;
const check = (n, ok, note = '') => { ran++; console.log((ok ? 'PASS ' : 'FAIL ') + n + ' ' + note); if (!ok) fails++; };
(async () => {
  const op = await mk('EmberTester'); await sleep(4000);
  const say = async (cmd, w = 900) => { op.chat_.length = 0; op.chat(cmd); await sleep(w); return op.chat_.join(' | '); };
  await say('/gamemode creative', 300);
  await say('/emberfall bot spawn Anchor', 1500);
  const r = await say('/emberfall bot run Anchor ranger', 3000);
  console.log('RUN reply :: ' + r.slice(0, 80)); // Q0 below is judged by Q1 (state shows run=N), not by this reply, which can arrive late
  let slot = null;
  for (let i = 0; i < 70; i++) { await sleep(2000); const s = await say('/emberfall bot state Anchor', 600); const m = /run=(\d+)/.exec(s); if (m && /weapons=\w/.test(s)) { slot = m[1]; break; } }
  check('Q1 Anchor is in a run', slot !== null, 'slot=' + slot);
  const mates = ['Mate1', 'Mate2', 'Mate3', 'Mate4'];
  for (const m of mates) await say('/emberfall bot spawn ' + m, 900);
  let joined = 0;
  for (const m of mates) { const j = await say(`/emberfall join ${slot} ${m}`, 1500); if (new RegExp(m + " joined slot " + slot).test(j)) joined++; console.log('JOIN ' + m + ' :: ' + j.slice(0, 90)); }
  check('Q2 all four mates were accepted by /emberfall join', joined === 4, 'joined=' + joined);
  // are they REALLY in the slot? bot state prints run=<slot> for a bot in a run
  let inRun = 0;
  for (const m of mates) { const s = await say('/emberfall bot state ' + m, 700); if (new RegExp('run=' + slot + '\\b').test(s)) inRun++; }
  check('Q3 all four mates report run=' + slot, inRun === 4, 'inRun=' + inRun);
  for (const n of ['Anchor', ...mates]) await say(`/effect give ${n} minecraft:resistance 1000000 4 true`, 250);
  await say('/emberfall relic threatadd Anchor 20', 800);
  await sleep(1500);
  const ta = await say('/emberfall relic threat Anchor', 800);
  check('Q4 threat at the spawn floor', /RELIC threat total=(1[5-9]|20)\./.test(ta), ta.slice(0, 70));
  let peak = 0, samples = [];
  for (let i = 0; i < 30; i++) {
    await sleep(4000);
    const out = await say('/emberfall debugaggro Anchor', 2500);
    const m = /aggro summary: (\d+) mobs/.exec(out); const n = m ? parseInt(m[1], 10) : NaN;
    samples.push(n); if (!isNaN(n)) peak = Math.max(peak, n);
    if (peak >= CAP) break;
  }
  console.log('SAMPLES ' + samples.join(','));
  check('Q5 count readable and within cap + 6', peak > 0 && peak <= CAP + 6, `peak=${peak} cap=${CAP}`);
  check('Q6 the arena actually filled (>= cap - 8); if this FAILS the MSPT is a LOWER BOUND for a full cap', peak >= CAP - 8, `peak=${peak} cap=${CAP}`);
  const ms = [];
  for (let i = 0; i < 3; i++) { await sleep(5000); const q = await say('/tick query', 900); const mm = /Average time per tick: ([\d.]+)ms/.exec(q); if (mm) ms.push(parseFloat(mm[1])); }
  console.log('MSPT5 cap=' + CAP + ' players=5 peak=' + peak + ' readings=' + ms.join(',') + ' avg=' + (ms.length ? (ms.reduce((a, b) => a + b, 0) / ms.length).toFixed(2) : 'NaN'));
  check('Q7 tick time readable', ms.length === 3, ms.join(','));
  for (const n of ['Anchor', ...mates]) await say('/emberfall bot remove ' + n, 400);
  console.log(fails === 0 && ran >= EXPECTED ? 'ALL PASS (' + ran + ')' : 'SOME FAIL ' + fails + ' ran=' + ran);
  process.exit(fails === 0 && ran >= EXPECTED ? 0 : 1);
})();
