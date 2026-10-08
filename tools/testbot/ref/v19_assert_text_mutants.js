// Usage (repo root): node tools/testbot/ref/v19_assert_text_mutants.js
// Pulls the three refusal-text regexes OUT OF THE TEST FILES (so it tests what is written there) and feeds each the right text and a set of wrong ones.
// It proves the asserts CAN fail on a different refusal. It does NOT prove the text arrives from a live server: that is READ FROM HANDLER, NOT SEEN ARRIVING.
const fs = require('fs');
const mgr = fs.readFileSync('tools/testbot/rift_manager_test.js', 'utf8');
const run = fs.readFileSync('tools/testbot/rift_inrun_test.js', 'utf8');
function rx(src, label) {
  const line = src.split('\n').find(l => l.includes("R('" + label + " "));
  if (!line) throw new Error('assert not found: ' + label);
  const m = /\.test\((\w+)\)/.exec(line); const r = /(\/[^\n]*?\/)\.test\(/.exec(line);
  if (!r) throw new Error('no regex in ' + label);
  return eval(r[1]);
}
// The real strings, copied from the source files at the line numbers in the comments.
const SHARD = t => 'The shard cannot open a Rift here: ' + t + '.';      // RiftShardItem.java:44
const REASONS = { spacing: 'too close to another Rift', air: 'no open air', run: 'the expedition map is a run arena' };  // RiftRules.java:119, :122; RiftManager.java:104
const CMD = t => 'RIFT refused: ' + t;                                    // EmberfallCommands.java:784
const asserts = [
  ['C3b', rx(mgr, 'C3b'), SHARD(REASONS.spacing), [
    ['no refusal text at all (click never reached the server)', ''], ['refused for open air instead', SHARD(REASONS.air)], ['refused for the run instead', SHARD(REASONS.run)],
    ['a generic refusal', SHARD('nope')], ['a Rift opened (no refusal)', 'RIFT opening events=12'], ['the right words without the shard prefix', REASONS.spacing],
    ['a different near-miss: too far from another Rift', SHARD('too far from another Rift')]]],
  ['D1b', rx(mgr, 'D1b'), SHARD(REASONS.air), [
    ['no refusal text at all', ''], ['refused for spacing instead', SHARD(REASONS.spacing)], ['refused for the run instead', SHARD(REASONS.run)],
    ['a generic refusal', SHARD('nope')], ['the right words without the shard prefix', REASONS.air], ['near-miss: no open space', SHARD('no open space')]]],
  ['I2', rx(run, 'I2'), CMD(REASONS.run), [
    ['no output at all', ''], ['refused for open air', CMD(REASONS.air)], ['refused for spacing', CMD(REASONS.spacing)], ['just the word refused (the OLD assert would pass this)', 'RIFT refused: anything at all'],
    ['a Rift opened', 'RIFT opening events=12'], ['the right words without the RIFT refused prefix', REASONS.run]]],
];
let bad = 0, total = 0;
for (const [name, re, good, wrongs] of asserts) {
  total++; const okGood = re.test(good); if (!okGood) { bad++; console.log('FAIL ' + name + ' does NOT accept the real text: ' + good); } else console.log('PASS ' + name + ' accepts the real text');
  for (const [why, txt] of wrongs) { total++; const passes = re.test(txt); if (passes) { bad++; console.log('FAIL ' + name + ' wrongly ACCEPTS: ' + why); } else console.log('PASS ' + name + ' rejects: ' + why); }
}
// The OLD asserts, to show what changed: the old I2 regex accepted any refusal.
const oldI2 = /refused/; total++;
if (oldI2.test(CMD(REASONS.air))) console.log('NOTE the OLD I2 (/refused/) accepted a refusal for the WRONG reason; the new one does not'); else { bad++; console.log('FAIL old I2 control'); }
console.log(bad === 0 ? 'ALL PASS (' + total + ')' : 'FAILED ' + bad + ' of ' + total); process.exit(bad ? 1 : 0);
