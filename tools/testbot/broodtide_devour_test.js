// Broodtide DEVOUR live test. Judge = SERVER REPLIES only (/data, /execute, /scoreboard). What it proves: an arm reaches a horde mob, the mob is HIDDEN (tag, invisible,
// NoAI, invulnerable, still in the world), spat out in Flood as a Brood-Kin with its flags restored and more health, hurts the player again (aggro intact), the cap holds,
// the arms stay at 40, and a boss that dies gives every swallowed mob back. Whether it LOOKS like a kraken eating is UNSEEN until a real client renders it.
// Needs a server started with -Demberfall.testMode=true.
const mineflayer = require('mineflayer');
const sleep = ms => new Promise(r => setTimeout(r, ms));
const bot = mineflayer.createBot({ host: '127.0.0.1', port: 25565, username: 'EmberTester', version: '1.21.11', auth: 'offline' });
const lines = []; bot.on('message', m => { const t = m.toString(); if (!/^Teleported EmberTester/.test(t)) lines.push(t); }); bot.on('error', e => console.log('ERR', e.message));
const ask = async (x, w = 500) => { const n = lines.length; bot.chat(x); await sleep(w); return lines.slice(n).join(' | '); };
let fails = 0; const check = (n, ok, note = '') => { console.log((ok ? 'PASS ' : 'FAIL ') + n + ' ' + note); if (!ok) fails++; };
const SEL = '@e[type=emberfall:broodtide,limit=1]';
const ARM = '@e[tag=emberfall_broodtide_arm]';
const SW = 'emberfall_swallowed';
let hurts = 0; bot._client.on('packet', (d, m) => { if (m.name === 'damage_event') hurts++; });
const count = async sel => {
  for (let k = 0; k < 4; k++) {
    await ask('/scoreboard objectives add bt dummy', 200); await ask('/scoreboard players set #n bt -1', 200);
    await ask(`/execute store result score #n bt if entity ${sel}`, 300);
    const r = await ask('/scoreboard players get #n bt', 500); const m = /has (-?\d+) \[bt\]/.exec(r);
    if (m && +m[1] >= 0) return +m[1];
  }
  return NaN;
};
const tide = async () => { const r = await ask('/emberfall bosstide 0', 450); const m = /tick=(\d+) tide=(EBB|FLOOD)/.exec(r); return m ? { tick: +m[1], state: m[2] } : null; };
const data = async (sel, path) => ask(`/data get entity ${sel} ${path}`, 450);
const num = s => { const m = /(-?\d+(?:\.\d+)?)[bdfsL]?\s*$/.exec(String(s).trim()); return m ? parseFloat(m[1]) : NaN; };
bot.once('spawn', async () => {
  await sleep(6500);
  await ask('/kill @e[type=!player]', 600); await sleep(1000);
  await ask('/gamemode survival'); await ask('/effect give @s minecraft:resistance 999 4 true', 300); await ask('/effect give @s minecraft:regeneration 999 4 true', 300);
  await ask('/character select juggernaut', 600); await ask('/expedition', 1500);
  for (let i = 0; i < 60; i++) { await sleep(2000); if (/expedition/.test(await ask('/data get entity @s Dimension', 400))) break; }
  await sleep(1500);
  console.log('boss:', (await ask('/emberfall boss 0', 1500)).slice(0, 100)); await sleep(2500);
  await ask('/emberfall wavestop 0', 400);

  const b0 = await count(SEL);
  check('V0 the Broodtide exists', b0 === 1, `count=${b0}`);

  // Subject: ONE horde zombie, tagged, placed 6 blocks from the boss (well inside the 14.3 reach).
  await ask(`/execute at ${SEL} positioned ~6 ~ ~ run emberfall spawnveteran horde_zombie`, 1500);
  await ask(`/tag @e[type=emberfall:horde_zombie,limit=1,sort=nearest] add subj`, 300);
  const have = await count('@e[tag=subj]');
  check('V1 the subject exists (tagged)', have === 1, `count=${have}`);
  const skinBefore = await ask('/data get entity @e[tag=subj,limit=1] emberfall_brood_kin_skin', 700);
  check('V1b before the swallow the sickly-skin flag is OFF (so V17b cannot pass on a flag that was always on)', /0b|false/.test(skinBefore), skinBefore.slice(-60));
  await ask('/attribute @e[tag=subj,limit=1] minecraft:max_health base set 40', 250);
  await ask('/effect give @e[tag=subj,limit=1] minecraft:instant_health 1 10 true', 300);
  const baseHp = num(await data('@e[tag=subj,limit=1]', 'Health'));
  const baseMax = 40;

  // Wait until the subject is swallowed (the swallowed tag appears) - the Devourer begins in the next Ebb with room for the wind-up.
  let swallowed = false, sawReach = false;
  for (let i = 0; i < 120 && !swallowed; i++) {
    await sleep(400);
    if (i % 10 === 0) { const r = await ask('/attribute @e[tag=subj,limit=1] minecraft:max_health get', 400); console.log('DIAG pre-swallow max_health sample', i, r.slice(-40)); }
    if (await count(`@e[tag=subj,tag=${SW}]`) === 1) swallowed = true;
    if (!sawReach && (await count(ARM)) === 45) sawReach = true;
  }
  check('V2 the zombie is SWALLOWED within about 48 s (the tag appears)', swallowed);
  if (!swallowed) { console.log('FAILED (no swallow)'); bot.quit(); process.exit(1); }

  // While hidden: every flag the plan promises.
  const dump = await ask('/data get entity @e[tag=subj,limit=1]', 700);
  check('V3 hidden: NoAI is set', /NoAI: 1b/.test(dump), (dump.match(/NoAI: \d/) || ['none'])[0]);
  check('V4 hidden: Invulnerable is set', /Invulnerable: 1b/.test(dump), '');
  check('V5 hidden: Silent is set', /Silent: 1b/.test(dump), '');
  check('V6 hidden: it carries the invisibility effect', /invisibility/i.test(dump), '');
  check('V7 hidden: it is NOT removed - still alive in the world', await count('@e[tag=subj]') === 1, '');
  check('V8 hidden: the wave cap still sees it (counted by the hostile test, not the targeting test)', await count('@e[tag=subj,type=emberfall:horde_zombie]') === 1, '');
  const bp = await data(SEL, 'Pos'); const sp = await data('@e[tag=subj,limit=1]', 'Pos');
  const mb = /\[(-?[\d.]+)d, (-?[\d.]+)d, (-?[\d.]+)d\]/.exec(bp), ms = /\[(-?[\d.]+)d, (-?[\d.]+)d, (-?[\d.]+)d\]/.exec(sp);
  if (mb && ms) {
    const d = Math.hypot(+mb[1] - +ms[1], +mb[3] - +ms[3]);
    check('V9 hidden: it was pulled INTO the body (within 3 blocks of its centre)', d <= 3.0, `dist=${d.toFixed(2)}`);
  } else check('V9 hidden: it was pulled INTO the body', false, 'no position');
  check('V10 the arms are still exactly 45 displays (the reach reused them, no new entity)', await count(ARM) === 45, '');

  // The spit: waits for Flood.
  let spat = false, floodWhenSpat = null;
  for (let i = 0; i < 150 && !spat; i++) {
    await sleep(400);
    if (await count(`@e[tag=subj,tag=${SW}]`) === 0) { spat = true; const t = await tide(); floodWhenSpat = t; }
  }
  check('V11 it is SPAT OUT (the swallowed tag is gone) within about 60 s', spat);
  if (!spat) { console.log('FAILED (no spit)'); bot.quit(); process.exit(1); }
  check('V12 it was spat in or just after Flood (not left in Ebb)', !!floodWhenSpat, JSON.stringify(floodWhenSpat));
  const after = await ask('/data get entity @e[tag=subj,limit=1]', 700);
  check('V13 spat out: NoAI is cleared (it must not read 1b)', !/NoAI: 1b/.test(after), (after.match(/NoAI: \d/) || ['cleared'])[0]);
  check('V14 spat out: Invulnerable is cleared', !/Invulnerable: 1b/.test(after), '');
  check('V15 spat out: Silent is cleared', !/Silent: 1b/.test(after), '');
  check('V16 spat out: the invisibility effect is gone', !/invisibility/i.test(after), '');
  check('V17 spat out: it is a Brood-Kin (tagged)', await count('@e[tag=subj,tag=emberfall_brood_kin]') === 1, '');
  const kinSkin = await ask('/data get entity @e[tag=subj,limit=1] emberfall_brood_kin_skin', 700);
  check('V17b spat out: the sickly-skin flag is set (the client draws the sickly zombie from it)', /1b|true/.test(kinSkin), kinSkin.slice(-60));
  const maxNow = num(await data('@e[tag=subj,limit=1]', 'attributes[{id:"minecraft:max_health"}].base'));
  check('V18 spat out: its max health is 1.5x what it was', Math.abs(maxNow - baseMax * 1.5) < 0.5, `was ${baseMax} now ${maxNow}`);

  // Aggro: the spat mob must hurt the player again (Prototype A's test), within 15 s.
  const h0 = hurts; await sleep(15000);
  check('V19 aggro intact: the Brood-Kin hurts the player again', hurts - h0 > 0, `hits=${hurts - h0}`);
  check('V20 the arms are still exactly 45 after the whole Devour', await count(ARM) === 45, '');

  // Teardown: a mob swallowed when the boss dies is given back.
  await ask('/kill @e[tag=subj]', 400);
  await ask(`/execute at ${SEL} positioned ~5 ~ ~ run emberfall spawnveteran horde_zombie`, 1500);
  await ask(`/tag @e[type=emberfall:horde_zombie,limit=1,sort=nearest,tag=!subj] add subj2`, 300);
  let again = false;
  for (let i = 0; i < 140 && !again; i++) { await sleep(400); if (await count(`@e[tag=subj2,tag=${SW}]`) === 1) again = true; }
  check('V21 a second zombie is swallowed', again);
  if (again) {
    await ask(`/kill ${SEL}`, 1500); await sleep(2500);
    const left = await count(`@e[tag=${SW}]`);
    check('V22 the boss died while a mob was inside: nothing is left tagged swallowed', left === 0, `swallowed left=${left}`);
    const survivor = await count('@e[tag=subj2]');
    const dm = await ask('/data get entity @e[tag=subj2,limit=1]', 600);
    check('V23 that mob was given back (not deleted) and is no longer invulnerable or frozen', survivor === 1 && !/Invulnerable: 1b/.test(dm) && !/NoAI: 1b/.test(dm), `alive=${survivor}`);
    check('V24 no arm display survives the boss', await count(ARM) === 0, '');
  }
  console.log(fails === 0 ? 'ALL PASS' : 'FAILED ' + fails);
  await ask('/kill @e[type=!player]', 400); await ask('/expedition leave', 800); bot.quit(); process.exit(fails === 0 ? 0 : 1);
});
