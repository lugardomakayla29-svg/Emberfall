// Turns the permanent server traces of the measurement-style suites into real PASS/FAIL lines, so a regression run can fail them.
// Usage: node trace_verdict.js <suite> <server log>. Thresholds sit far below what was measured on 2026-10-04 and far above the muted control:
//   phantom_test : 6 of 6 foes hit, 85 cuts (control: 1 foe hit)       -> foes hit >= 5, cuts >= 60
//   rite_test    : spread 7.8 -> 0.9, 8 of 8 hit, 603 cuts (control: spread unchanged, 1 hit) -> foes >= 7, cuts >= 300
//   legion_test  : 5 raised, 50 strikes (control: 0)                   -> raised >= 3, strikes >= 20
const fs = require('fs');
const [suite, log] = process.argv.slice(2);
const text = fs.readFileSync(log, 'utf8');
let fails = 0; const R = (n, ok, extra = '') => { console.log(`${ok ? 'PASS' : 'FAIL'} ${n} ${extra}`); if (!ok) fails++; };
const all = tag => [...text.matchAll(new RegExp(tag + '[^\\n]*', 'g'))].map(x => x[0]);
const last = tag => { const m = all(tag); return m.length ? m[m.length - 1] : null; };
const num = (line, key) => { const m = line && new RegExp(key + '=(-?[\\d.]+)').exec(line); return m ? +m[1] : null; };
if (suite === 'phantom_test') {
  const l = last('PHANTOM_TEST');
  R('V1 the Phantom Blades ran and left a trace', !!l, l || 'none');
  R('V2 at least 60 cuts landed', num(l, 'cuts') >= 60, `cuts ${num(l, 'cuts')}`);
  R('V3 the blades changed foe many times (zooming about)', num(l, 'darts') >= 20, `darts ${num(l, 'darts')}`);
} else if (suite === 'rite_test') {
  const l = last('RITE_TEST');
  R('V1 the Reaper Rite ran and left a trace', !!l, l || 'none');
  R('V2 at least 7 foes were gathered and cut', num(l, 'foes') >= 7, `foes ${num(l, 'foes')}`);
  R('V3 at least 300 cuts landed in the disc', num(l, 'cuts') >= 300, `cuts ${num(l, 'cuts')}`);
  R('V4 the shatter hit the pile', num(l, 'shatterHit') >= 7, `shatterHit ${num(l, 'shatterHit')}`);
} else if (suite === 'legion_test') {
  // the trace is written at the end of EVERY vortex, including empty ones: judge the best one, not the trailing one
  const ls = all('LEGION_TEST'); const l = ls.reduce((b, x) => (num(x, 'raised') > num(b, 'raised') ? x : b), ls[0] || null);
  R('V1 the Grave Legion ran and left a trace', !!l, l || 'none');
  R('V2 at least 3 foes rose as revenants', num(l, 'raised') >= 3, `raised ${num(l, 'raised')}`);
  R('V3 the revenants struck at least 20 blows', num(l, 'strikes') >= 20, `strikes ${num(l, 'strikes')}`);
} else { console.log('FAIL unknown suite', suite); fails++; }
console.log(fails === 0 ? 'ALL PASS' : 'SOME FAIL ' + fails);
