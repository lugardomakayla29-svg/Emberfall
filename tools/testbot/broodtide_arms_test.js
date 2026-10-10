// Broodtide ARMS live test. Judge = SERVER REPLIES only (/data, /execute, /scoreboard). Nothing here is claimed as seen: whether the arms LOOK like a kraken
// is UNSEEN until a real client renders them. This proves what a server can: the displays exist, there are never more than 40, they stand round the BODY
// (not at the world origin: Entity.load resets Pos), they move with the Tide, they follow a phase change without a spawn, and none survive the boss.
// Needs a server started with -Demberfall.testMode=true.
const mineflayer = require('mineflayer');
const sleep = ms => new Promise(r => setTimeout(r, ms));
const bot = mineflayer.createBot({ host: '127.0.0.1', port: 25565, username: 'EmberTester', version: '1.21.11', auth: 'offline' });
const lines = []; bot.on('message', m => lines.push(m.toString())); bot.on('error', e => console.log('ERR', e.message));
const ask = async (x, w = 500) => { const n = lines.length; bot.chat(x); await sleep(w); return lines.slice(n).join(' | '); };
let fails = 0; const check = (n, ok, note = '') => { console.log((ok ? 'PASS ' : 'FAIL ') + n + ' ' + note); if (!ok) fails++; };
const SEL = '@e[type=emberfall:broodtide,limit=1]';
const ARM = '@e[tag=emberfall_broodtide_arm]';
const count = async sel => {
  for (let k = 0; k < 4; k++) {
    await ask('/scoreboard objectives add bt dummy', 200); await ask('/scoreboard players set #n bt -1', 200);
    await ask(`/execute store result score #n bt if entity ${sel}`, 300);
    const r = await ask('/scoreboard players get #n bt', 500); const m = /has (-?\d+) \[bt\]/.exec(r);
    if (m && +m[1] >= 0) return +m[1];
  }
  return NaN;
};
const pos = async sel => { const r = await ask(`/data get entity ${sel} Pos`, 500); const m = /\[(-?[\d.]+)d, (-?[\d.]+)d, (-?[\d.]+)d\]/.exec(r); return m ? [+m[1], +m[2], +m[3]] : null; };
const tide = async () => { const r = await ask('/emberfall bosstide 0', 450); const m = /tick=(\d+) tide=(EBB|FLOOD)/.exec(r); return m ? { tick: +m[1], state: m[2] } : null; };
bot.once('spawn', async () => {
  await sleep(6500);
  await ask('/kill @e[type=!player]', 600); await sleep(1000);
  await ask('/gamemode survival'); await ask('/effect give @s minecraft:resistance 999 4 true', 300); await ask('/effect give @s minecraft:regeneration 999 4 true', 300);
  await ask('/character select juggernaut', 600); await ask('/expedition', 1500);
  for (let i = 0; i < 60; i++) { await sleep(2000); if (/expedition/.test(await ask('/data get entity @s Dimension', 400))) break; }
  await sleep(1500);
  console.log('boss:', (await ask('/emberfall boss 0', 1500)).slice(0, 100)); await sleep(2500);
  await ask('/emberfall wavestop 0', 400);

  const body = await pos(SEL);
  check('K0 the Broodtide exists and has a position', !!body, JSON.stringify(body));
  const n1 = await count(ARM);
  check('K1 the arms exist: exactly 40 displays (5 arms x 8 links), allocated once', n1 === 40, `count=${n1}`);

  // Not at the world origin: every arm display is within 20 blocks of the body (Entity.load resets Pos to the tag, so a missing Pos lands at 0,0,0).
  const nearBody = body ? await count(`@e[tag=emberfall_broodtide_arm,x=${body[0]},y=${body[1]},z=${body[2]},distance=..20]`) : NaN;
  check('K2 all 40 displays are within 20 blocks of the body, none at the world origin', nearBody === 40, `near=${nearBody}`);

  // Phase one uses 3 arms: the other 2 are parked at scale 0. Count displays that are visible by scale through the item display transformation.
  const visible = async () => {
    await ask('/scoreboard objectives add bt dummy', 200); await ask('/scoreboard players set #v bt 0', 200);
    await ask(`/execute as ${ARM} if data entity @s {transformation:{scale:[0.0f,0.0f,0.0f]}} run scoreboard players add #v bt 0`, 400);
    await ask(`/execute as ${ARM} unless data entity @s {transformation:{scale:[0.0f,0.0f,0.0f]}} run scoreboard players add #v bt 1`, 600);
    const r = await ask('/scoreboard players get #v bt', 500); const m = /has (-?\d+) \[bt\]/.exec(r); return m ? +m[1] : NaN;
  };
  const vis1 = await visible();
  check('K3 phase one shows 3 arms (24 visible links), the other 16 are parked at scale 0', vis1 === 24, `visible=${vis1}`);

  // Tide: the tip of arm 0 must be farther from the body in EBB than in FLOOD (it opens and curls). Read the last link of arm 0.
  const tipDist = async () => {
    const t = await pos('@e[tag=emberfall_broodtide_arm_0_7,limit=1]'); const b = await pos(SEL);
    return t && b ? Math.hypot(t[0] - b[0], t[2] - b[2]) : NaN;
  };
  const seen = { EBB: null, FLOOD: null };
  for (let i = 0; i < 90 && !(seen.EBB != null && seen.FLOOD != null); i++) {
    const t0 = await tide(); const d = await tipDist(); const t1 = await tide();
    // count a sample only when the state is old enough for the 18 tick open/close to have finished: 25+ ticks into it
    if (t0 && t1 && t0.state === t1.state) {
      const into = t0.state === 'EBB' ? t0.tick % 460 : (t0.tick % 460) - 280;
      if (into >= 25 && seen[t0.state] == null) seen[t0.state] = d;
    }
    await sleep(500);
  }
  check('K4 measured the arm tip in EBB and in FLOOD', seen.EBB != null && seen.FLOOD != null, JSON.stringify(seen));
  if (seen.EBB != null && seen.FLOOD != null) {
    check('K5 the arm tip is farther from the body in EBB than in FLOOD (arms open, then curl back)', seen.EBB > seen.FLOOD + 1.0, `ebb=${seen.EBB.toFixed(2)} flood=${seen.FLOOD.toFixed(2)}`);
    check('K6 a curled tip stays within 4.5 blocks of the body centre (it wraps the body)', seen.FLOOD <= 4.5, `flood=${seen.FLOOD.toFixed(2)}`);
  }

  // Phase change: drop health to phase two (below 2/3) and then three (below 1/3). The entity count must stay 40 (nothing spawned or removed) and the visible arms
  // must grow 3 -> 4 -> 5 (24 -> 32 -> 40 links).
  await ask(`/data modify entity ${SEL} Health set value 500f`, 400); await sleep(1500);
  const vis2 = await visible(); const n2 = await count(ARM);
  check('K7 phase two shows 4 arms (32 visible links) and still exactly 40 displays', vis2 === 32 && n2 === 40, `visible=${vis2} count=${n2}`);
  await ask(`/data modify entity ${SEL} Health set value 200f`, 400); await sleep(1500);
  const vis3 = await visible(); const n3 = await count(ARM);
  check('K8 phase three shows 5 arms (40 visible links) and still exactly 40 displays', vis3 === 40 && n3 === 40, `visible=${vis3} count=${n3}`);

  // Exit: a genuine /kill must remove every display. No orphans.
  await ask(`/kill ${SEL}`, 1500); await sleep(2000);
  const left = await count(ARM); const leftBoss = await count('@e[type=emberfall:broodtide]');
  check('K9 after the boss is killed no arm display is left behind', left === 0 && leftBoss === 0, `arms left=${left} boss left=${leftBoss}`);

  // Second exit path: the run is TORN DOWN while the boss is alive (no kill). A kill ends the run, so a fresh run is needed: leave, re-enter, spawn, then teardown.
  await ask('/emberfall teardown 0', 1500); await sleep(2000);
  await ask('/character select juggernaut', 600); await ask('/expedition', 1500);
  for (let i = 0; i < 60; i++) { await sleep(2000); if (/expedition/.test(await ask('/data get entity @s Dimension', 400))) break; }
  await sleep(1500);
  console.log('boss2:', (await ask('/emberfall boss 0', 1500)).slice(0, 80)); await sleep(2500);
  await ask('/emberfall wavestop 0', 400);
  const again = await count(ARM); const bossAgain = await count('@e[type=emberfall:broodtide]');
  check('K10 a second run gets its own boss and exactly 40 displays (nothing leaked from the first)', again === 40 && bossAgain === 1, `arms=${again} boss=${bossAgain}`);
  await ask('/emberfall teardown 0', 1500); await sleep(2500);
  const afterTear = await count(ARM); const bossTear = await count('@e[type=emberfall:broodtide]');
  check('K11 the boss is still ALIVE when the run is torn down; after teardown no arm display and no boss remain (discard path, not a kill)', afterTear === 0 && bossTear === 0, `arms=${afterTear} boss=${bossTear}`);

  console.log(fails === 0 ? 'ALL PASS' : `FAILED ${fails}`);
  process.exit(fails === 0 ? 0 : 1);
});
