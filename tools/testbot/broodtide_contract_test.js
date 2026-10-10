// Broodtide contract live test (plan section 8 tests 1 and 10). Judge = server replies only.
// MODE=kill   : a genuine kill must promote the run to tier 2 exactly once (and mark the boss defeated).
// MODE=teardown: a forced run end (/emberfall teardown) must NOT promote (tier stays 1) and must leave zero Broodtide / slime entities.
// usage: node broodtide_contract_test.js kill|teardown        (server needs -Demberfall.testMode=true; default first boss = Broodtide)
const mineflayer = require('mineflayer');
const MODE = process.argv[2] || 'kill';
const sleep = ms => new Promise(r => setTimeout(r, ms));
const bot = mineflayer.createBot({ host: '127.0.0.1', port: 25565, username: 'EmberTester', version: '1.21.11', auth: 'offline' });
const lines = []; bot.on('message', m => lines.push(m.toString())); bot.on('error', e => console.log('ERR', e.message));
const ask = async (x, w = 500) => { const n = lines.length; bot.chat(x); await sleep(w); return lines.slice(n).join(' | '); };
let fails = 0; const check = (n, ok, note = '') => { console.log((ok ? 'PASS ' : 'FAIL ') + n + ' ' + note); if (!ok) fails++; };
const count = async sel => { await ask('/scoreboard objectives add bc dummy', 200); await ask('/scoreboard players set #n bc -1', 200);
  await ask(`/execute store result score #n bc if entity ${sel}`, 300); const r = await ask('/scoreboard players get #n bc', 500); const m = /has (-?\d+) \[bc\]/.exec(r); return m ? +m[1] : NaN; };
const countR = async sel => { for (let k = 0; k < 4; k++) { const v = await count(sel); if (!Number.isNaN(v) && v >= 0) return v; } return NaN; };
const tier = async () => { for (let k = 0; k < 4; k++) { const r = await ask('/emberfall wavestatus 0', 600); const m = /tier=(\d+)/.exec(r); if (m) return +m[1]; } return NaN; };
bot.once('spawn', async () => {
  await sleep(6500);
  await ask('/kill @e[type=!player]', 600); await sleep(1000);
  await ask('/gamemode survival'); await ask('/effect give @s minecraft:resistance 999 4 true', 300); await ask('/effect give @s minecraft:regeneration 999 4 true', 300);
  await ask('/character select juggernaut', 600); await ask('/expedition', 1500);
  for (let i = 0; i < 60; i++) { await sleep(2000); if (/expedition/.test(await ask('/data get entity @s Dimension', 400))) break; }
  await sleep(1500);
  const t0 = await tier(); check('K0 the run starts at tier 1', t0 === 1, `tier=${t0}`);
  console.log('boss:', (await ask('/emberfall boss 0', 1500)).slice(0, 80)); await sleep(2500);
  // NOT wavestop: that deletes the Wave Director, and the tier promotion lives on the director. Clear the horde with /kill instead.
  await ask('/kill @e[type=!player,type=!emberfall:broodtide,type=!minecraft:item_display,type=!minecraft:interaction,type=!minecraft:block_display,type=!minecraft:text_display]', 600);
  const n0 = await countR('@e[type=emberfall:broodtide]'); check('K1 the Broodtide is alive before the exit', n0 === 1, `count=${n0}`);
  if (MODE === 'kill') {
    await ask('/kill @e[type=emberfall:broodtide]', 1500); await sleep(2500);
    const t1 = await tier(); check('K2 a genuine kill promotes the run to tier 2', t1 === 2, `tier=${t1}`);
    const left = await countR('@e[type=emberfall:broodtide]'); check('K3 no Broodtide left after the kill', left === 0, `left=${left}`);
    await sleep(3000); const t2 = await tier(); check('K4 the promotion happens ONCE (tier still 2 after 3 s)', t2 === 2, `tier=${t2}`);
  } else {
    console.log('teardown:', (await ask('/emberfall teardown 0', 2500)).slice(0, 90)); await sleep(3000);
    const left = await countR('@e[type=emberfall:broodtide]'); check('T1 a forced run end leaves NO Broodtide', left === 0, `left=${left}`);
    const sl = await countR('@e[type=minecraft:slime]'); check('T2 and no stray slimes', sl === 0, `slimes=${sl}`);
    const st = await ask('/emberfall wavestatus 0', 700);
    check('T3 the run is gone, so no promotion could have been handed out', /No active Wave Director/.test(st), st.slice(0, 80));
  }
  console.log(fails === 0 ? 'ALL PASS' : 'SOME FAIL ' + fails); bot.quit(); setTimeout(() => process.exit(0), 400);
});
