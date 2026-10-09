// Broodtide live test (slice 2: body, tide armour, rooted, kill). Judge = SERVER REPLIES only (/data, /execute). Nothing here is claimed as seen.
// Needs a server started with -Demberfall.testMode=true. The default first boss is the Broodtide (FirstBoss), so /emberfall boss 0 spawns it.
const mineflayer = require('mineflayer');
const sleep = ms => new Promise(r => setTimeout(r, ms));
const bot = mineflayer.createBot({ host: '127.0.0.1', port: 25565, username: 'EmberTester', version: '1.21.11', auth: 'offline' });
const lines = []; bot.on('message', m => lines.push(m.toString())); bot.on('error', e => console.log('ERR', e.message));
const ask = async (x, w = 500) => { const n = lines.length; bot.chat(x); await sleep(w); return lines.slice(n).join(' | '); };
let fails = 0; const check = (n, ok, note = '') => { console.log((ok ? 'PASS ' : 'FAIL ') + n + ' ' + note); if (!ok) fails++; };
const SEL = '@e[type=emberfall:broodtide,limit=1]';
const num = async (path, w = 500) => { for (let k = 0; k < 4; k++) { const r = await ask(`/data get entity ${SEL} ${path}`, w); const m = /: (-?[\d.]+)[fd]?\b/.exec(r); if (m) return +m[1]; } return NaN; };
const count = async sel => { await ask('/scoreboard objectives add bt dummy', 200); await ask('/scoreboard players set #n bt -1', 200);
  await ask(`/execute store result score #n bt if entity ${sel}`, 300); const r = await ask('/scoreboard players get #n bt', 500); const m = /has (-?\d+) \[bt\]/.exec(r); return m ? +m[1] : NaN; };
const countR = async sel => { for (let k = 0; k < 4; k++) { const v = await count(sel); if (!Number.isNaN(v) && v >= 0) return v; } return NaN; };
const pos = async () => { const r = await ask(`/data get entity ${SEL} Pos`, 500); const m = /\[(-?[\d.]+)d, (-?[\d.]+)d, (-?[\d.]+)d\]/.exec(r); return m ? [+m[1], +m[2], +m[3]] : null; };
bot.once('spawn', async () => {
  await sleep(6500);
  await ask('/kill @e[type=!player]', 600); await sleep(1000);
  await ask('/gamemode survival'); await ask('/effect give @s minecraft:resistance 999 4 true', 300); await ask('/effect give @s minecraft:regeneration 999 4 true', 300);
  await ask('/character select juggernaut', 600); await ask('/expedition', 1500);
  for (let i = 0; i < 60; i++) { await sleep(2000); if (/expedition/.test(await ask('/data get entity @s Dimension', 400))) break; }
  await sleep(1500);
  console.log('boss:', (await ask('/emberfall boss 0', 1500)).slice(0, 100)); await sleep(2500);
  await ask('/emberfall wavestop 0', 400);
  const n = await countR('@e[type=emberfall:broodtide]');
  check('B1 exactly one Broodtide exists', n === 1, `count=${n}`);
  const noGuardian = await countR('@e[type=emberfall:ember_guardian]');
  check('B2 no Ember Guardian spawned (the default first boss is the Broodtide)', noGuardian === 0, `guardians=${noGuardian}`);
  const maxHp = await num('attributes[{id:"minecraft:max_health"}].base');
  check('B3 max health is 810 (600 x 1.35, BossTuning)', Math.abs(maxHp - 810) < 1, `max=${maxHp}`);
  const spd = await num('attributes[{id:"minecraft:movement_speed"}].base');
  check('B4 movement speed attribute is 0 (rooted)', spd === 0, `speed=${spd}`);
  const size = await num('Size');
  // /data get entity ... Size reads the SAVED value, which vanilla writes as getSize() - 1 (Slime.addAdditionalSaveData: getSize, iconst_1, isub). So size 6 reads 5.
  check('B5 body size is 6 (saved NBT Size = 6 - 1 = 5)', size === 5, `saved Size=${size}`);
  const hp0 = await num('Health');
  check('B6 spawns at full health', Math.abs(hp0 - 810) < 1, `hp=${hp0}`);
  // Rooted: the position must not move over 8 s, with a player standing in reach (aggressive Slimes hop at their target).
  const p0 = await pos(); await ask('/tp @s ~ ~ ~', 100); await sleep(8000); const p1 = await pos();
  const drift = p0 && p1 ? Math.hypot(p1[0] - p0[0], p1[2] - p0[2]) : NaN;
  check('B7 rooted: no horizontal drift in 8 s', drift < 0.3, `drift=${drift && drift.toFixed(3)}`);
  // Tide: damage through the REAL damage path (/damage ... minecraft:generic), health lost per hit. A measurement counts only if bosstide reads the SAME
  // state before and after the hit, so a state change mid-measurement cannot fake a result. Uses /emberfall bosstide for the clock.
  const tide = async () => { const r = await ask('/emberfall bosstide 0', 450); const m = /tick=(\d+) tide=(EBB|FLOOD) armour=([\d.]+)/.exec(r); return m ? { tick: +m[1], state: m[2], armour: +m[3] } : null; };
  const hit = async (amount) => { await ask(`/data modify entity ${SEL} Health set value 700f`, 250); const t0 = await tide(); const h0 = await num('Health', 350);
    await ask(`/damage ${SEL} ${amount} minecraft:generic`, 350); const h1 = await num('Health', 350); const t1 = await tide(); return { t0, t1, lost: h0 - h1 }; };
  await ask('/effect clear @e[type=emberfall:broodtide]', 200);
  const seen = { EBB: null, FLOOD: null };
  for (let i = 0; i < 40 && !(seen.EBB && seen.FLOOD); i++) {
    const r = await hit(20);
    if (r.t0 && r.t1 && r.t0.state === r.t1.state && seen[r.t0.state] === null) seen[r.t0.state] = r;
    if (!(seen.EBB && seen.FLOOD)) await sleep(400);
  }
  check('B8 measured a hit entirely inside EBB', !!seen.EBB, seen.EBB ? `lost=${seen.EBB.lost.toFixed(2)} at tick ${seen.EBB.t0.tick}` : 'none');
  check('B9 measured a hit entirely inside FLOOD', !!seen.FLOOD, seen.FLOOD ? `lost=${seen.FLOOD.lost.toFixed(2)} at tick ${seen.FLOOD.t0.tick}` : 'none');
  if (seen.EBB) check('B10 a hit of 20 costs about 20 in Ebb', Math.abs(seen.EBB.lost - 20) < 1.0, `lost=${seen.EBB.lost.toFixed(2)}`);
  if (seen.FLOOD) check('B11 the same hit costs about 7 (x0.35) in Flood', Math.abs(seen.FLOOD.lost - 7) < 1.0, `lost=${seen.FLOOD.lost.toFixed(2)}`);
  if (seen.FLOOD) check('B12 Flood never makes it immune (lost > 0)', seen.FLOOD.lost > 0.5, `lost=${seen.FLOOD.lost.toFixed(2)}`);
  // Genuine kill: /kill must work (bypass), the body must go, and no smaller Broodtide may be left behind (the Slime split trap).
  await ask(`/kill ${SEL}`, 1500); await sleep(1500);
  const left = await countR('@e[type=emberfall:broodtide]');
  check('B13 /kill removes the boss and leaves NO split-off copies', left === 0, `broodtides left=${left}`);
  const slimes = await countR('@e[type=minecraft:slime]');
  check('B14 no vanilla slimes were left behind either', slimes === 0, `slimes=${slimes}`);
  console.log(fails === 0 ? 'ALL PASS' : 'SOME FAIL ' + fails); bot.quit(); setTimeout(() => process.exit(0), 400);
});
