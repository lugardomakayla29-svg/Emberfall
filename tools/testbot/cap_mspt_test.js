// Does -Demberfall.hostileCap really move the WaveDirector's hostile cap? The director only spawns while hostiles < cap, so with
// nobody killing anything the live hostile count climbs and then PLATEAUS at (about) the cap. Run once with no property (expect <= 40
// plus one pack overshoot guard) and once with -Demberfall.hostileCap=60 (expect clearly above 40, <= 60). CAP is passed as the env var CAP and must equal the -Demberfall.hostileCap given to the server (default 40).
// Count = '/emberfall debugaggro' summary (all emberfall mobs within 60 blocks of the bot): a proxy for the director's own count, which it
// also includes the bot's friendly summons in; fine for a plateau, not an exact cap reading.
const mineflayer = require('mineflayer');
const sleep = ms => new Promise(r => setTimeout(r, ms));
const CAP = parseInt(process.env.CAP || '40', 10);
const mk = name => new Promise(res => { const b = mineflayer.createBot({ host: '127.0.0.1', port: 25565, username: name, version: '1.21.11', auth: 'offline' }); b.chat_ = []; b.on('message', m => b.chat_.push(m.toString())); b.on('error', () => {}); b.once('spawn', () => res(b)); });
const EXPECTED = 7; // P0 P1 P2a P2 P3 P4 P5
let ran = 0, fails = 0;
const check = (n, ok, note = '') => { ran++; console.log((ok ? 'PASS ' : 'FAIL ') + n + ' ' + note); if (!ok) fails++; };
(async () => {
  const op = await mk('EmberTester'); await sleep(4000);
  const say = async (cmd, w = 900) => { op.chat_.length = 0; op.chat(cmd); await sleep(w); return op.chat_.join(' | '); };
  await say('/gamemode creative', 300);
  await say('/emberfall bot spawn Anchor', 2000);
  const r = await say('/emberfall bot run Anchor ranger', 1500);
  check('P0 a run starts', /run Anchor ok/.test(r), r.slice(0, 80));
  let slot = null;
  for (let i = 0; i < 70; i++) { await sleep(2000); const s = await say('/emberfall bot state Anchor', 600); const m = /run=(\d+)/.exec(s); if (m && /weapons=\w/.test(s)) { slot = m[1]; break; } }
  check('P1 the bot is in a run', slot !== null, 'slot=' + slot);
  // keep the bot alive and the director fast: creative + max threat is not available, so just let it run; read totalSpawned and the live count
  await say('/effect give Anchor minecraft:resistance 1000000 4 true', 500);
  // The natural ramp spawns about 3 elites in 5 minutes, which never nears the cap. Push threat to its maximum (the same call a Greed Shrine
  // makes) so the interval drops to its 0.75 s floor and the cap is what stops the spawns, not the clock.
  await say('/emberfall relic threatadd Anchor 20', 800);
  // threatLevel is recomputed on the director's next tick, so the reply to threatadd still shows the OLD value: read it again afterwards.
  await sleep(1500);
  const ta = await say('/emberfall relic threat Anchor', 800);
  check('P2a threat raised (the director is at its spawn floor)', /RELIC threat total=(1[5-9]|20)\./.test(ta), ta.slice(0, 80));
  let peak = 0, samples = [];
  for (let i = 0; i < 30; i++) {
    await sleep(4000);
    const out = await say('/emberfall debugaggro Anchor', 2500);
    const m = /aggro summary: (\d+) mobs/.exec(out);
    const n = m ? parseInt(m[1], 10) : NaN;
    samples.push(n); if (!isNaN(n)) peak = Math.max(peak, n);
    if (peak >= CAP) break;
  }
  console.log('SAMPLES ' + samples.join(','));
  // Tick time WITH the arena full: vanilla /tick query, three readings 5 s apart (the average covers the last 100 ticks).
  const ms = [];
  for (let i = 0; i < 3; i++) { await sleep(5000); const q = await say('/tick query', 900); const mm = /Average time per tick: ([\d.]+)ms/.exec(q); if (mm) ms.push(parseFloat(mm[1])); }
  console.log('MSPT cap=' + CAP + ' peak=' + peak + ' readings=' + ms.join(',') + ' avg=' + (ms.length ? (ms.reduce((a, b) => a + b, 0) / ms.length).toFixed(2) : 'NaN'));
  check('P5 tick time was readable', ms.length === 3, ms.join(','));
  check('P2 the live hostile count was readable', samples.some(x => !isNaN(x)), 'peak=' + peak);
  check('P3 the count plateaus at the configured cap (<= cap + pack slack 6)', peak <= CAP + 6, `peak=${peak} cap=${CAP}`);
  // Without this a slow ramp would pass P3 for the wrong reason (never got near the cap).
  check('P4 the director actually REACHED the cap region (>= cap - 6), so the plateau is real', peak >= CAP - 6, `peak=${peak} cap=${CAP}`);
  await say('/emberfall bot remove Anchor', 1500);
  console.log(fails === 0 && ran >= EXPECTED ? 'ALL PASS (' + ran + ')' : 'SOME FAIL ' + fails + ' ran=' + ran);
  process.exit(fails === 0 && ran >= EXPECTED ? 0 : 1);
})();
