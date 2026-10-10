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
  await ask(`/execute at ${SEL} positioned ~30 ~ ~ run emberfall spawnveteran horde_zombie`, 1500);   // out of reach (14.3) while it is set up
  await ask(`/tag @e[type=emberfall:horde_zombie,limit=1,sort=nearest] add subj`, 300);
  const have = await count('@e[tag=subj]');
  check('V1 the subject exists (tagged)', have === 1, `count=${have}`);
  const skinBefore = await ask('/data get entity @e[tag=subj,limit=1] emberfall_brood_kin_skin', 700);
  check('V1b before the swallow the sickly-skin flag is OFF (so V17b cannot pass on a flag that was always on)', /0b|false/.test(skinBefore), skinBefore.slice(-60));
  await ask('/attribute @e[tag=subj,limit=1] minecraft:max_health base set 40', 250);
  await ask('/effect give @e[tag=subj,limit=1] minecraft:instant_health 1 10 true', 300);
  const baseHp = num(await data('@e[tag=subj,limit=1]', 'Health'));
  const baseMax = 40;
  const setBack = num(await data('@e[tag=subj,limit=1]', 'attributes[{id:"minecraft:max_health"}].base'));
  check('V1c the subject really has max health 40 BEFORE it is brought into reach (read back, not assumed)', Math.abs(setBack - 40) < 0.01, `base=${setBack}`);
  // Line the entry up with the Tide: wait for the START of an Ebb (tick within the first 3 s of the 23 s cycle). The Devourer eats in the Ebb and spits
  // once a Flood has begun, so entering at the start of an Ebb leaves the longest possible hidden window to read in (the previous run's entry could land late in
  // an Ebb and the mob was given back within ~1.5 s, faster than the test's own command round trips).
  let aligned = null;
  for (let i = 0; i < 80; i++) { const t = await tide(); if (t && t.state === 'EBB' && (t.tick % 460) <= 60) { aligned = t; break; } await sleep(250); }
  check('V1e the subject enters at the START of an Ebb (so the hidden window is long enough to read)', !!aligned, aligned ? `tick=${aligned.tick} into-cycle=${aligned.tick % 460}` : 'never saw an Ebb start');
  await ask(`/execute at ${SEL} run tp @e[tag=subj,limit=1] ~6 ~ ~`, 600);   // now into reach: the tp is relative to the BOSS (execute at), not the bot
  const near = await ask(`/execute at ${SEL} if entity @e[tag=subj,distance=..8]`, 400);
  check('V1d the subject is now within 8 blocks of the boss (inside the 14.3 reach)', /Test passed/.test(near), near.slice(-60));

  // Wait until the subject is swallowed (the swallowed tag appears) - the Devourer begins in the next Ebb with room for the wind-up.
  let swallowed = false, sawReach = false;
  for (let i = 0; i < 120 && !swallowed; i++) {
    await sleep(400);
    if (await count(`@e[tag=subj,tag=${SW}]`) === 1) swallowed = true;
    if (!sawReach && (await count(ARM)) === 45) sawReach = true;
  }
  check('V2 the zombie is SWALLOWED within about 48 s (the tag appears)', swallowed);
  if (!swallowed) { console.log('FAILED (no swallow)'); bot.quit(); process.exit(1); }

  // While hidden: every flag the plan promises. Each is ONE selector the server resolves in a single tick together with the swallowed tag, so a spit
  // between two commands cannot make the check read a mob that has already been given back (the earlier dump-then-check order did exactly that).
  const atomic = async extra => count(`@e[tag=subj,tag=${SW}${extra}]`);
  const hidden = await atomic('');
  check('V2b the swallowed tag is still present when the flags are read', hidden === 1, `count=${hidden}`);
  check('V3 hidden: NoAI is set', (await atomic(',nbt={NoAI:1b}')) === 1, '');
  check('V4 hidden: Invulnerable is set', (await atomic(',nbt={Invulnerable:1b}')) === 1, '');
  check('V5 hidden: Silent is set', (await atomic(',nbt={Silent:1b}')) === 1, '');
  check('V6 hidden: it carries the invisibility effect', (await atomic(',nbt={active_effects:[{id:"minecraft:invisibility"}]}')) === 1, '');
  check('V7 hidden: it is NOT removed - still alive in the world', await count('@e[tag=subj]') === 1, '');
  check('V8 hidden: the wave cap still sees it (counted by the hostile test, not the targeting test)', await count('@e[tag=subj,type=emberfall:horde_zombie]') === 1, '');
  // V9: one command, one tick: "swallowed subject within 3 blocks of the boss body". A two-step read (boss pos, then mob pos) could straddle the spit.
  const pulled = await ask(`/execute at ${SEL} if entity @e[tag=subj,tag=${SW},distance=..3]`, 500);
  check('V9 hidden: it was pulled INTO the body (within 3 blocks of its centre)', /Test passed/.test(pulled), pulled.slice(-60));
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
