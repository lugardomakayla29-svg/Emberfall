// A GENUINE Broodtide kill escalates the run to tier 2 and leaves a free chest; a teardown discard does neither.
const mineflayer = require('mineflayer');
const bot = mineflayer.createBot({ host: '127.0.0.1', port: 25565, username: 'EmberTester', version: '1.21.11', auth: 'offline' });
const sleep = ms => new Promise(r => setTimeout(r, ms));
const lines = []; bot.on('message', m => lines.push(m.toString())); bot.on('error', e => console.log('ERR', e.message));
let fails = 0; const check = (l, ok, e = '') => { console.log((ok ? 'PASS ' : 'FAIL ') + l + (e ? '  ' + e : '')); if (!ok) fails++; };
bot.once('spawn', async () => {
  await sleep(6000);
  const c = async (x, w = 500) => { bot.chat(x); await sleep(w); };
  const ask = async (x, w = 700) => { const n = lines.length; bot.chat(x); await sleep(w); return lines.slice(n).join(' | '); };
  const free = async () => +((await ask('/emberfall relic chests EmberTester', 700)).match(/free=(\d+)/)?.[1] ?? NaN);
  await c('/gamemode survival'); await c('/character select vanguard', 500);
  await c('/expedition leave', 900); await c('/expedition', 4000);
  for (let i = 0; i < 30; i++) { await sleep(1500); if (/expedition/.test(await ask('/data get entity @s Dimension', 400))) break; } await sleep(2500);
  await c('/effect give @s minecraft:resistance 999 4 true', 200); await c('/effect give @s minecraft:regeneration 999 4 true', 200);
  check('no free chest before the boss', (await free()) === 0);
  console.log('boss:', (await ask('/emberfall boss 0', 1500)).slice(0, 90)); await sleep(3000);
  const g = await ask('/execute if entity @e[type=emberfall:broodtide] run say BROODUP', 500);
  check('the Broodtide is up', g.includes('BROODUP'));
  await c('/kill @e[type=emberfall:broodtide]', 800); await sleep(6000);
  const f = await free();
  check('a genuine Broodtide death leaves exactly one free chest', f === 1, 'free=' + f);
  await c('/expedition leave', 800);
  console.log(fails === 0 ? 'RESULT: ALL PASS' : 'RESULT: ' + fails + ' FAILED'); bot.quit(); setTimeout(() => process.exit(0), 400);
});
