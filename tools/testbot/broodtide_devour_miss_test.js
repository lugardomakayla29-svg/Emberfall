// Broodtide DEVOUR "the mob ran before the arm landed" live test. REPRODUCES a server crash: BroodtideDevourer.swallow() removed the entry from the list that tick()
// was iterating (ConcurrentModificationException at BroodtideDevourer.tick, seen in crash-2026-10-10_10.54.45). Judge = the bot stays connected, the Broodtide is still
// alive and still ticking (its tide counter advances), and the server log has no exception. Needs -Demberfall.testMode=true.
const mineflayer = require('mineflayer');
const sleep = ms => new Promise(r => setTimeout(r, ms));
const bot = mineflayer.createBot({ host: '127.0.0.1', port: 25565, username: 'EmberTester', version: '1.21.11', auth: 'offline' });
const lines = []; let dead = false;
bot.on('message', m => { const t = m.toString(); if (!/^Teleported EmberTester/.test(t)) lines.push(t); }); bot.on('error', e => console.log('ERR', e.message)); bot.on('end', () => { dead = true; });
const ask = async (x, w = 400) => { const n = lines.length; bot.chat(x); await sleep(w); return lines.slice(n).join(' | '); };
let fails = 0; const check = (n, ok, note = '') => { console.log((ok ? 'PASS ' : 'FAIL ') + n + ' ' + note); if (!ok) fails++; };
const SEL = '@e[type=emberfall:broodtide,limit=1]';
const info = async () => { const r = await ask('/emberfall bosstide 0', 450); const t = /tick=(\d+) tide=(EBB|FLOOD)/.exec(r), e = /eats=(\d+)/.exec(r); return { tick: t ? +t[1] : null, eats: e ? +e[1] : null, raw: r }; };
bot.once('spawn', async () => {
  await sleep(6500);
  await ask('/kill @e[type=!player]', 600); await sleep(1000);
  await ask('/gamemode survival'); await ask('/effect give @s minecraft:resistance 999 4 true', 300); await ask('/effect give @s minecraft:regeneration 999 4 true', 300);
  await ask('/character select juggernaut', 600); await ask('/expedition', 1500);
  for (let i = 0; i < 60; i++) { await sleep(2000); if (/expedition/.test(await ask('/data get entity @s Dimension', 400))) break; }
  await sleep(1500);
  console.log('boss:', (await ask('/emberfall boss 0', 1500)).slice(0, 100)); await sleep(2500);
  await ask('/emberfall wavestop 0', 400);
  const b0 = await info();
  check('M0 the Broodtide exists and ticks', b0.tick !== null, b0.raw.slice(-120));

  // Feed it zombies that are INSIDE reach when chosen and are thrown OUT of reach every ~100 ms afterwards, so some eat lands on a mob that has run away.
  let misses = 0, startedAtLeast = b0.eats || 0;
  for (let round = 0; round < 6 && !dead; round++) {
    await ask(`/execute at ${SEL} positioned ~7 ~ ~ run emberfall spawnveteran horde_zombie`, 500);
    await ask('/tag @e[type=emberfall:horde_zombie,tag=!runner] add runner', 200);
    await ask('/effect give @e[tag=runner] minecraft:resistance 90 4 true', 150);
    for (let k = 0; k < 40 && !dead; k++) {
      await ask(`/execute at ${SEL} run tp @e[tag=runner,distance=..14] ~7 ~ ~`, 90);   // keep it inside reach so it is chosen
      const i = await info();
      if (i.eats !== null && i.eats > startedAtLeast) { startedAtLeast = i.eats; break; }
    }
    for (let k = 0; k < 12 && !dead; k++) {   // now the eat is winding up: shove it far away over and over, covering the 1 s window
      await ask(`/execute at ${SEL} run tp @e[tag=runner] ~40 ~ ~`, 70);
      misses++;
    }
    await ask('/kill @e[tag=runner]', 150);
    await sleep(500);
  }
  await sleep(1500);
  const a = dead ? null : await info();
  check('M1 the bot is still connected after the missed eats (the server did not crash)', !dead, 'dead=' + dead);
  check('M2 the Broodtide still ticks after the missed eats', !!a && a.tick !== null && a.tick > b0.tick, a ? `tick ${b0.tick} -> ${a.tick}` : 'no reply');
  check('M3 at least one eat was actually started (test is not vacuous)', startedAtLeast > (b0.eats || 0), `eats ${b0.eats} -> ${startedAtLeast} shoves=${misses}`);
  console.log(fails === 0 ? 'ALL PASS' : 'FAILED ' + fails);
  process.exit(fails === 0 ? 0 : 1);
});
setTimeout(() => { console.log('TIMEOUT dead=' + dead); process.exit(2); }, 230000);
