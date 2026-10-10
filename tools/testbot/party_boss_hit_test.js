// Issue #13 step 3d, the DAMAGE side. A party boss can hold more than vanilla's 1024 max_health, so the surplus is a damage factor
// applied in the boss's own hurtServer. This test hits the real boss with /damage and reads what was taken, solo and party.
// Env: PARTY=1|5, BOSS=broodtide|devourer. Needs -Demberfall.logPartyHp=true. Prints one line "HIT boss=.. party=.. taken=.. pool=..".
const mineflayer = require('mineflayer');
const sleep = ms => new Promise(r => setTimeout(r, ms));
const PARTY = parseInt(process.env.PARTY || '1', 10);
const BOSS = process.env.BOSS || 'broodtide';
const TYPE = BOSS === 'broodtide' ? 'emberfall:broodtide' : 'emberfall:devourer_brain';
const HIT = 100; let ran = 0, fails = 0;
const check = (n, ok, note = '') => { ran++; console.log((ok ? 'PASS ' : 'FAIL ') + n + ' ' + note); if (!ok) fails++; };
const mk = name => new Promise(res => {
  const b = mineflayer.createBot({ host: '127.0.0.1', port: 25565, username: name, version: '1.21.11', auth: 'offline' });
  b.chat_ = []; b.on('message', m => b.chat_.push(m.toString()));
  b.once('spawn', () => res(b));
  b.on('error', e => { console.log('FAIL connection error: ' + e.message); process.exit(1); });
  b.on('kicked', r => { console.log('FAIL kicked: ' + JSON.stringify(r).slice(0, 160)); process.exit(1); });
});
(async () => {
  const op = await mk('EmberTester'); await sleep(4000);
  const say = async (cmd, w = 900) => { op.chat_.length = 0; op.chat(cmd); await sleep(w); return op.chat_.join(' | '); };
  const hp = async () => { const r = await say(`/data get entity @e[type=${TYPE},limit=1] Health`, 600); const m = /(-?[\d.]+)f?$/.exec(r.trim()); return m ? parseFloat(m[1]) : NaN; };
  await say('/gamemode survival', 400);
  await say('/effect give @s minecraft:resistance 999 4 true', 300);
  await say('/effect give @s minecraft:regeneration 999 4 true', 300);
  await say('/character select juggernaut', 600);
  await say('/expedition', 1500);
  let inRun = false;
  for (let i = 0; i < 60; i++) { await sleep(2000); if (/expedition/.test(await say('/data get entity @s Dimension', 400))) { inRun = true; break; } }
  await sleep(2000);
  check('H1 the op is in a run', inRun);
  const mates = ['Mate1', 'Mate2', 'Mate3', 'Mate4'].slice(0, PARTY - 1);
  for (const m of mates) await say('/emberfall bot spawn ' + m, 700);
  for (const m of mates) await say('/emberfall join 0 ' + m, 900);
  for (const m of mates) await say(`/effect give ${m} minecraft:resistance 1000000 4 true`, 200);
  const st = await say('/emberfall wavestatus 0', 900);
  const pm = /partySize=(\d+)/.exec(st);
  check('H2 the run has ' + PARTY + ' players', pm && parseInt(pm[1], 10) === PARTY, st.slice(-30));
  const g = await say(`/emberfall relic summoner EmberTester ${BOSS}`, 2500);
  check('H3 the ' + BOSS + ' summon was accepted', /ok/.test(g), g.slice(0, 50));
  // The Broodtide is armoured x0.35 in Flood and the Devourer may be submerged; both let /damage through
  // only when it is not blocked. If the gate holds, the pool is unchanged and H4 reports it instead of passing.
  await sleep(3000);
    const before = await hp();
  await say(`/damage @e[type=${TYPE},limit=1] ${HIT} minecraft:generic`, 700);
  const after = await hp();
  const taken = before - after;
  console.log(`HIT boss=${BOSS} party=${PARTY} before=${before} after=${after} taken=${taken.toFixed(2)}`);
  check('H4 the boss took damage', taken > 0, 'taken=' + taken);
  for (const n of mates) await say('/emberfall bot remove ' + n, 300);
  console.log(fails === 0 && ran === 4 ? 'RESULT: ALL PASSED (4 checks)' : 'RESULT: ' + (fails || 1) + ' FAILED (ran ' + ran + ' of 4)');
  const code = fails === 0 && ran === 4 ? 0 : 1;
  op.quit(); setTimeout(() => process.exit(code), 500);
})().catch(e => { console.log('FAIL suite crashed: ' + (e && e.stack || e)); console.log('RESULT: crashed'); process.exit(1); });
