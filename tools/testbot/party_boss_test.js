// Issue #13, step 3d: boss health scales with the party. Run with PARTY=1 and PARTY=5 on FRESH worlds and -Demberfall.logPartyHp=true,
// BOSS=guardian|devourer (one boss per run: a second cannot be summoned during a fight).
// EmberTester (the only op) starts the run itself with the real /character + /expedition path, because the summoner item refuses a
// non-op user. PARTY-1 bots (Mate1..) are then put in with `/emberfall join`. The boss is summoned through the real summoner item.
// The verdict is made by party_boss_grade.sh from the server log (PARTYHP line of the boss).
const mineflayer = require('mineflayer');
const sleep = ms => new Promise(r => setTimeout(r, ms));
const PARTY = parseInt(process.env.PARTY || '1', 10);
const BOSS = process.env.BOSS || 'guardian';
const EXPECTED = 3; let ran = 0, fails = 0;
const check = (n, ok, note = '') => { ran++; console.log((ok ? 'PASS ' : 'FAIL ') + n + ' ' + note); if (!ok) fails++; };
function finish() {
  const short_ = ran < EXPECTED;
  if (fails === 0 && !short_) { console.log('RESULT: ALL PASSED (' + ran + ' checks)'); return 0; }
  console.log('RESULT: ' + (fails || 1) + ' FAILED' + (short_ ? ' (only ' + ran + ' of ' + EXPECTED + ' checks ran)' : ''));
  return 1;
}
const mk = name => new Promise(res => {
  const b = mineflayer.createBot({ host: '127.0.0.1', port: 25565, username: name, version: '1.21.11', auth: 'offline' });
  b.chat_ = []; b.on('message', m => b.chat_.push(m.toString()));
  b.once('spawn', () => res(b));
  b.on('error', e => { console.log('FAIL connection error: ' + e.message); fails++; process.exit(finish()); });
  b.on('kicked', r => { console.log('FAIL kicked: ' + JSON.stringify(r).slice(0, 160)); fails++; process.exit(finish()); });
});
(async () => {
  const op = await mk('EmberTester'); await sleep(4000);
  const say = async (cmd, w = 900) => { op.chat_.length = 0; op.chat(cmd); await sleep(w); return op.chat_.join(' | '); };
  await say('/gamemode survival', 400);
  await say('/effect give @s minecraft:resistance 999 4 true', 300);
  await say('/effect give @s minecraft:regeneration 999 4 true', 300);
  await say('/character select juggernaut', 600);
  await say('/expedition', 1500);
  let inRun = false;
  for (let i = 0; i < 60; i++) { await sleep(2000); if (/expedition/.test(await say('/data get entity @s Dimension', 400))) { inRun = true; break; } }
  await sleep(2000);
  check('B1 the op is in a run', inRun, '');
  const mates = ['Mate1', 'Mate2', 'Mate3', 'Mate4'].slice(0, PARTY - 1);
  for (const m of mates) await say('/emberfall bot spawn ' + m, 700);
  for (const m of mates) await say('/emberfall join 0 ' + m, 900);
  for (const m of mates) await say(`/effect give ${m} minecraft:resistance 1000000 4 true`, 200);
  const st = await say('/emberfall wavestatus 0', 900);
  const m = /partySize=(\d+)/.exec(st);
  check('B2 the run has ' + PARTY + ' players when the boss is summoned', m && parseInt(m[1], 10) === PARTY, st.slice(-30));
  const g = await say(`/emberfall relic summoner EmberTester ${BOSS}`, 2500);
  console.log('BOSS ' + BOSS + ' :: ' + g.slice(0, 80));
  await sleep(3000);
  check('B3 the ' + BOSS + ' summon was accepted', new RegExp('summoner ' + BOSS + ' ok').test(g), g.slice(0, 60));
  for (const n of mates) await say('/emberfall bot remove ' + n, 300);
  const code = finish();
  op.quit(); setTimeout(() => process.exit(code), 500);
})().catch(e => { console.log('FAIL suite crashed: ' + (e && e.stack || e)); console.log('RESULT: crashed'); process.exit(1); });
