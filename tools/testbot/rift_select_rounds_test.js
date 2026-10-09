// Rift Character Select, live, TWO clients: the paths round 1 (rift_select_test.js) does not reach.
//  R2  nobody answers: the run must NOT start while the party is still deciding, and MUST start at the 30 s timeout (the only protection
//      against a client that never replies). Timing is proven from both sides: not in a run at ~20 s, in a run by ~45 s.
//  R3  a stranger (not in the party) sends a close with the party's valid id: it must change nothing.
//  R4  one member disconnects while choosing: the other still goes, alone, once they answer.
// Verdicts are the bots' reads of the server's own counters (/emberfall rift selectstate) and their Dimension, through the operator bot.
const mineflayer = require('mineflayer');
const sleep = ms => new Promise(r => setTimeout(r, ms));
let ran = 0, fails = 0; const EXPECTED = 11;
const check = (n, ok, note = '') => { ran++; console.log((ok ? 'PASS ' : 'FAIL ') + n + (note ? '  ' + note : '')); if (!ok) fails++; };
const mk = name => new Promise(res => {
  const b = mineflayer.createBot({ host: '127.0.0.1', port: 25565, username: name, version: '1.21.11', auth: 'offline' });
  b.chat_ = []; b.sel = []; b.on('message', m => b.chat_.push(m.toString()));
  b._client.on('packet', (d, m) => { if (m.name === 'custom_payload' && d && d.channel === 'emberfall:open_character_select') b.sel.push(d); });
  b.once('spawn', () => res(b));
  b.on('error', e => console.log('ERR ' + name + ' ' + e.message));
});
const finish = () => { const short_ = ran < EXPECTED; console.log(fails === 0 && !short_ ? `ALL PASS (${ran})` : `FAILED ${fails}` + (short_ ? ` (only ${ran} of ${EXPECTED} checks ran)` : '')); process.exit(fails === 0 && !short_ ? 0 : 1); };
process.on('unhandledRejection', e => { console.log('FAIL unhandled ' + (e && e.message)); fails++; finish(); });
const say = async (b, cmd, w = 700) => { const n = b.chat_.length; b.chat(cmd); await sleep(w); return b.chat_.slice(n).join(' | '); };
let OP = null;
const dimOf = async b => /entity data: "emberfall:expedition"/.test(await say(OP, `/data get entity ${b.username} Dimension`, 500)) ? 'expedition' : 'other';
// ---- wire helpers (same bytes the real client writes) ----
const wrVar = v => { const a = []; do { let x = v & 0x7f; v >>>= 7; if (v) x |= 0x80; a.push(x); } while (v); return Buffer.from(a); };
const wrUtf = t => { const b = Buffer.from(t, 'utf8'); return Buffer.concat([wrVar(b.length), b]); };
const rdVar = (b, o) => { let v = 0, s = 0, x; do { x = b[o.i++]; v |= (x & 0x7f) << s; s += 7; } while (x & 0x80); return v; };
const rdUtf = (b, o) => { const n = rdVar(b, o); const t = b.slice(o.i, o.i + n).toString('utf8'); o.i += n; return t; };
const decodeOpen = d => { const buf = Buffer.isBuffer(d.data) ? d.data : Buffer.from(d.data || []); const o = { i: 0 }; const cur = rdUtf(buf, o); const selectId = rdVar(buf, o); const secondsLeft = rdVar(buf, o); return { cur, selectId, secondsLeft }; };
const choose = (b, id) => b._client.write('custom_payload', { channel: 'emberfall:choose_character', data: wrUtf(id) });
const closeSel = (b, id) => b._client.write('custom_payload', { channel: 'emberfall:close_character_select', data: wrVar(id) });
const opens = b => b.sel.map(decodeOpen);
const selState = async op => { const t = await say(op, '/emberfall rift selectstate', 600); const m = /SELECT phases=(\d+)/.exec(t); const ph = [...t.matchAll(/\[id=(\d+) party=(\d+) pending=(\d+) waiting=(\d+)\]/g)].map(x => ({ id: +x[1], party: +x[2], pending: +x[3], waiting: +x[4] })); return { phases: m ? +m[1] : -1, ph, raw: t }; };
// The list marks the CURRENT character with '> ' before its name and the id in brackets: read ONLY that line, never an option that merely appears in the list.
const curOf = t => { const line = t.split(' | ').find(x => /^\s*(\u00a7.)*> /.test(x) || x.includes('> ')); const m = line && /\((\w+)\)/.exec(line); return m ? m[1] : null; };
const targets = b => Object.values(b.entities).filter(e => e.name === 'interaction');
const clickRift = async b => { const t = targets(b); if (t.length !== 1) return 'targets=' + t.length; const n = b.chat_.length; await b.activateEntity(t[0]); await sleep(900); return b.chat_.slice(n).join(' | '); };
const waitFor = async (fn, ms, step = 250) => { const end = Date.now() + ms; while (Date.now() < end) { if (await fn()) return true; await sleep(step); } return false; };


(async () => {
  const A = await mk('EmberTester'); let B = await mk('EmberTester2'); const C = await mk('EmberTester3'); OP = A; await sleep(4000);
  const bots = () => [A, B, C];
  await say(A, '/gamemode creative', 400); await say(A, '/emberfall rift clear', 200); await say(A, '/emberfall rift closeall', 200);
  // The platform MUST exist before anyone is teleported onto it: a run where the fills were swallowed had no floor, every bot fell and the
  // test failed with A=0 B=0 (no Rift was ever clicked). Retry the fill until a block read-back says stone, and stop loudly if it never does.
  let floor = false;
  for (let k = 0; k < 6 && !floor; k++) {
    await say(A, '/fill 94 199 94 106 199 106 minecraft:stone', 900); await say(A, '/fill 94 200 94 106 235 106 minecraft:air', 900);
    floor = /FLOOR_OK/.test(await say(A, '/execute if block 100 199 100 minecraft:stone run say FLOOR_OK', 500));
  }
  if (!floor) { console.log('FAIL SETUP the stone platform never appeared, so nothing below can be trusted'); process.exit(1); }
  for (const b of bots()) { await say(A, `/gamemode survival ${b.username}`, 300); await say(A, `/effect give ${b.username} minecraft:resistance 999 4 true`, 300); await say(A, `/clear ${b.username}`, 300); }
  // C is the STRANGER: it never goes near the Rift, so it is never in a party.
  await say(A, '/tp EmberTester3 60.5 200 60.5', 400);
  const openRift = async () => { await say(A, '/emberfall rift closeall', 300); await sleep(500); const o = await say(A, '/emberfall rift open', 900); await waitFor(async () => /open=true/.test(await say(A, '/emberfall rift state', 500)), 12000); return o; };
  const near = async list => { for (let k = 0; k < list.length; k++) await say(A, `/tp ${list[k].username} ${100.5 + k} 200 103.5`, 400); await sleep(1200); };
  const far = async list => { for (let k = 0; k < list.length; k++) await say(A, `/tp ${list[k].username} ${100.5 + k} 200 100.5`, 400); await sleep(1000); };
  const leaveAll = async () => { for (const b of [A, B]) { await say(A, `/execute as ${b.username} run expedition leave`, 700); } await sleep(2500); };

  // ---------- Round 2: nobody answers ----------
  await far([A, B]); await openRift();
  await waitFor(async () => targets(A).length === 1 && targets(B).length === 1, 8000);
  await near([A, B]); await clickRift(A); await clickRift(B);
  await waitFor(() => A.sel.length > 0 && B.sel.length > 0, 8000);
  const t0 = Date.now();
  check('R2a both screens opened', A.sel.length === 1 && B.sel.length === 1, `A=${A.sel.length} B=${B.sel.length}`);
  await sleep(20000);
  let st = await selState(A);
  check('R2b at ~20 s with NOBODY answering the phase is still open and nobody is in a run', st.phases === 1 && st.ph[0].pending === 2 && (await dimOf(A)) === 'other' && (await dimOf(B)) === 'other', `${Math.round((Date.now() - t0) / 1000)} s ${st.raw.slice(0, 80)}`);
  const started = await waitFor(async () => (await selState(A)).phases === 0, 25000, 500);
  const tEnd = Math.round((Date.now() - t0) / 1000);
  check('R2c the phase ENDS by itself at the timeout, between 28 and 40 s after the screens opened', started && tEnd >= 28 && tEnd <= 40, `ended at ${tEnd} s`);
  check('R2d and BOTH bots are then in the run', await waitFor(async () => (await dimOf(A)) === 'expedition' && (await dimOf(B)) === 'expedition', 70000, 1500), '');
  await leaveAll(); await say(A, '/emberfall rift closeall', 300);

  // ---------- Round 3: a stranger's close ----------
  await far([A, B]); await openRift();
  A.sel.length = 0; B.sel.length = 0;
  await waitFor(async () => targets(A).length === 1 && targets(B).length === 1, 8000);
  await near([A, B]); await clickRift(A); await clickRift(B);
  await waitFor(() => A.sel.length > 0 && B.sel.length > 0, 8000);
  const sid = A.sel.length ? decodeOpen(A.sel[0]).selectId : 0;
  st = await selState(A);
  check('R3a a fresh party is choosing (phase with 2 pending, a NEW id)', st.phases === 1 && st.ph[0].pending === 2 && sid > 1, `sid=${sid} ${st.raw.slice(0, 70)}`);
  closeSel(C, sid); await sleep(1200);
  st = await selState(A);
  check('R3b a STRANGER closing with the party\'s VALID id changes nothing', st.phases === 1 && st.ph[0].pending === 2 && st.ph[0].party === 2, st.raw.slice(0, 80));
  check('R3c control: the same close from a MEMBER does count', (closeSel(A, sid), await sleep(900), (await selState(A)).ph[0].pending === 1), '');
  closeSel(B, sid); await sleep(1500);
  await waitFor(async () => (await dimOf(A)) === 'expedition', 70000, 1500);
  await leaveAll(); await say(A, '/emberfall rift closeall', 300);

  // ---------- Round 4: a member disconnects while choosing ----------
  await far([A, B]); await openRift();
  A.sel.length = 0; B.sel.length = 0;
  await waitFor(async () => targets(A).length === 1 && targets(B).length === 1, 8000);
  await near([A, B]); await clickRift(A); await clickRift(B);
  await waitFor(() => A.sel.length > 0 && B.sel.length > 0, 8000);
  st = await selState(A);
  check('R4a party of 2 is choosing', st.phases === 1 && st.ph[0].party === 2, st.raw.slice(0, 70));
  B.end(); await sleep(2500);
  st = await selState(A);
  check('R4b B disconnects: the party shrinks to 1 and the phase stays open for A', st.phases === 1 && st.ph[0].party === 1 && st.ph[0].pending === 1, st.raw.slice(0, 80));
  check('R4c the Rift is still held open for the one who remains (waiting=1)', st.phases === 1 && st.ph[0].waiting === 1, st.raw.slice(0, 80));
  closeSel(A, decodeOpen(A.sel[0]).selectId); await sleep(1500);
  check('R4d A answers: the phase ends and A, alone, goes to the run', await waitFor(async () => (await selState(A)).phases === 0 && (await dimOf(A)) === 'expedition', 70000, 1500), '');
  await say(A, '/expedition leave', 1200); await say(A, '/emberfall rift closeall', 300);
  finish();
})();
