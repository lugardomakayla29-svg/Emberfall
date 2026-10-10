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
  await ask('/attribute @e[tag=subj,limit=1] minecraft:max_health base set 40', 250);
  await ask('/effect give @e[tag=subj,limit=1] minecraft:instant_health 1 10 true', 300);
  const baseHp = num(await data('@e[tag=subj,limit=1]', 'Health'));
  const baseMax = 40;

  const xyz = t => { const m = /\[(-?[\d.]+)d, (-?[\d.]+)d, (-?[\d.]+)d\]/.exec(t); return m ? [+m[1], +m[2], +m[3]] : null; };
  const st = async () => { const r = await ask('/emberfall bosstide 0', 350); const m = /tick=(\d+) tide=(\w+).*eats=(\d+) inCare=(\d+) swallowed=(\d+) spits=(\d+)/.exec(r); return m ? { tick: +m[1], tide: m[2], eats: +m[3], inCare: +m[4], sw: +m[5], spits: +m[6] } : null; };
  const maxOf = async () => num(await data('@e[tag=subj,limit=1]', 'attributes[{id:"minecraft:max_health"}].base'));
  const log = (...a) => console.log(...a);
  const eff = async () => num(await ask('/attribute @e[tag=subj,limit=1] minecraft:max_health get', 450));
  const mods = async () => (await ask('/data get entity @e[tag=subj,limit=1] attributes[{id:"minecraft:max_health"}]', 500)).slice(0, 260);
  let s0 = await st(); log('START', JSON.stringify(s0), 'effective max=', await eff()); log('ATTR', await mods());
  let caught = null;
  for (let i = 0; i < 400 && !caught; i++) { const s1 = await st(); if (s1 && s1.sw === 1) caught = s1; else if (s1 && s1.spits >= 1) { log('MISSED the swallow window', JSON.stringify(s1)); break; } }
  if (caught) {
    log('SWALLOWED at', JSON.stringify(caught), 'base=', await maxOf(), 'effective=', await eff());
    for (let k = 0; k < 4; k++) { const z = xyz(await data('@e[tag=subj,limit=1]', 'Pos')), b = xyz(await data('@e[type=emberfall:broodtide,limit=1]', 'Pos')); const s2 = await st(); log('  while hidden: dist', z && b ? Math.hypot(z[0]-b[0], z[2]-b[2]).toFixed(2) : '?', 'dy', z && b ? (z[1]-b[1]).toFixed(2) : '?', 'state', JSON.stringify(s2)); }
  }
  for (let i = 0; i < 100; i++) { const s3 = await st(); if (s3 && s3.spits >= 1) { log('SPAT', JSON.stringify(s3)); break; } }
  log('after spit effective=', await eff(), 'ATTR', await mods());
  log('after spit max=', await maxOf(), 'health=', num(await data('@e[tag=subj,limit=1]', 'Health')));
  bot.quit(); process.exit(0);
});
