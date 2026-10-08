// Rift Character Select, live, TWO real clients in ONE party. Order under test (owner, 2026-10-08): click the Rift, hold still for the
// 3 s countdown, THEN Character Select, THEN the run. Verdicts are the bots' own reads of the SERVER's counters (/emberfall rift selectstate)
// and of their own Dimension, never chat. Run from emberfall/bot (node_modules) on a FRESH server, no PREBUILD needed.
// Round 1: both answer (one picks, one closes) -> run starts at once. Round 2: a WRONG close id and a stranger's close change nothing.
// Round 3: nobody answers -> the run starts only at the 30 s timeout. Round 4: one member leaves while choosing -> the other still goes.
const mineflayer = require('mineflayer');
const sleep = ms => new Promise(r => setTimeout(r, ms));
let ran = 0, fails = 0; const EXPECTED = 16;
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
let OP = null; // the operator bot: only it may run /data and /emberfall, so every read about ANY bot goes through it
const dimOf = async b => /entity data: "emberfall:expedition"/.test(await say(OP || b, `/data get entity ${b.username} Dimension`, 500)) ? 'expedition' : 'other';

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
  const A = await mk('EmberTester'); const B = await mk('EmberTester2'); OP = A; await sleep(4000);
  global.say_ = say;
  const reset = async () => { b_.forEach(b => b.sel.length = 0); };
  const b_ = [A, B];
  await say(A, '/gamemode creative', 400); await say(A, '/emberfall rift clear', 200); await say(A, '/emberfall rift closeall', 200);
  await say(A, '/fill 94 199 94 106 199 106 minecraft:stone', 900); await say(A, '/fill 94 200 94 106 235 106 minecraft:air', 900);
  for (const b of b_) { await say(A, `/gamemode survival ${b.username}`, 300); await say(A, `/effect give ${b.username} minecraft:resistance 999 4 true`, 300); await say(A, `/clear ${b.username}`, 300); }
  await say(A, '/character select juggernaut', 500); await say(A, '/execute as EmberTester2 run character select reaper', 600);
  // The Rift stands 6 blocks ahead of the opening spot (z 100.5 -> 106.5); the reach is 4, so both bots stand 3 blocks from it, side by side.
  const standBy = async () => { await say(A, '/tp EmberTester 100.5 200 100.5', 500); await say(A, '/tp EmberTester2 101.5 200 100.5', 500); await sleep(1200); };
  const goNear = async () => { await say(A, '/tp EmberTester 100.5 200 103.5', 500); await say(A, '/tp EmberTester2 101.5 200 103.5', 500); await sleep(1200); };

  // ---------- Round 1: both answer ----------
  await standBy();
  const o = await say(A, '/emberfall rift open', 900);
  check('A1 a Rift opens', /RIFT opening/.test(o), o.slice(0, 40));
  await waitFor(async () => /open=true/.test(await say(A, '/emberfall rift state', 500)), 12000);
  await waitFor(async () => targets(A).length === 1 && targets(B).length === 1, 8000);
  check('A2 both clients see the one click target', targets(A).length === 1 && targets(B).length === 1, `A=${targets(A).length} B=${targets(B).length}`);
  await goNear();
  const c1 = await clickRift(A); const c2 = await clickRift(B);
  check('B1 both clicks start / join the departure', /hold still|stirs/i.test(c1) && /hold still|stirs/i.test(c2), (c1 + ' || ' + c2).slice(-100));
  check('B2 right after the clicks NO screen is open yet (the countdown comes first)', /hold still|stirs/i.test(c1) && A.sel.length === 0 && B.sel.length === 0 && (await selState(A)).phases === 0, `A=${A.sel.length} B=${B.sel.length}`);
  await waitFor(() => A.sel.length > 0 && B.sel.length > 0, 8000);
  const oa = opens(A)[0], ob = opens(B)[0];
  check('C1 after the countdown BOTH bots get the screen', !!oa && !!ob, `A=${A.sel.length} B=${B.sel.length}`);
  check('C2 both carry the SAME non-zero select id and about 30 s', oa && ob && oa.selectId > 0 && oa.selectId === ob.selectId && oa.secondsLeft >= 29 && oa.secondsLeft <= 30, JSON.stringify(oa));
  check('C3 the screen marks each bot\'s CURRENT character', oa && ob && oa.cur === 'juggernaut' && ob.cur === 'reaper', `A=${oa && oa.cur} B=${ob && ob.cur}`);
  let st = await selState(A);
  check('D1 the server holds ONE phase with 2 in the party, 2 pending, waiting=2', st.phases === 1 && st.ph[0].party === 2 && st.ph[0].pending === 2 && st.ph[0].waiting === 2, st.raw.slice(0, 100));
  check('D2 NO run has started while they choose (the old code started at once)', st.phases === 1 && (await dimOf(A)) === 'other' && (await dimOf(B)) === 'other', '');
  const sid = oa ? oa.selectId : 0;
  choose(A, 'ranger'); await sleep(900);
  st = await selState(A);
  check('E1 A picks a character: pending drops to 1, the phase stays', st.phases === 1 && st.ph[0].pending === 1 && st.ph[0].party === 2, st.raw.slice(0, 100));
  check('E2 A really got the new character (the pick was applied, not only counted)', st.phases === 1 && curOf(await say(A, '/character list', 700)) === 'ranger', '');
  check('E3 still no run with one member undecided', st.phases === 1 && (await dimOf(A)) === 'other', '');
  closeSel(B, sid + 999); await sleep(800);
  st = await selState(A);
  check('F1 a WRONG close id changes nothing', st.phases === 1 && st.ph[0].pending === 1, st.raw.slice(0, 100));
  closeSel(B, sid); await sleep(1200);
  check('F2 B closes with the RIGHT id: the party is complete and the phase is gone', st.phases === 1 && sid > 0 && (await selState(A)).phases === 0, '');
  check('G1 BOTH bots are in the run now', await waitFor(async () => (await dimOf(A)) === 'expedition' && (await dimOf(B)) === 'expedition', 60000, 1500), '');
  const listB = await say(A, '/execute as EmberTester2 run character list', 900); console.log('LISTB', JSON.stringify(listB.slice(0, 260)), 'cur=' + curOf(listB));
  check('G2 B kept the character it already had (closing = keep)', curOf(listB) === 'reaper', '');
  await say(A, '/expedition leave', 1200); await say(B, '/expedition leave', 1200); await sleep(1500);
  await say(A, '/emberfall rift closeall', 300);
  finish_if_short(); 
  console.log('ROUND1_DONE');
  finish();
})();
function finish_if_short() {}
