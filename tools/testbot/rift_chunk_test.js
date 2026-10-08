// Rift chunk hold, live. An open Rift's click target is an entity saved WITH its chunk. Before the hold, a run took the only player away,
// the chunk unloaded, the target went, and it came back as a stranger the load hook discarded: an open Rift nobody could enter again.
// MEASURED before the fix: server=0 client=0 after the run. Each claim has a control: the same sequence, only the hold differs (see the PR).
const mineflayer = require('mineflayer');
const bot = mineflayer.createBot({ host: '127.0.0.1', port: 25565, username: 'EmberTester', version: '1.21.11', auth: 'offline' });
const sleep = ms => new Promise(r => setTimeout(r, ms));
const lines = []; bot.on('message', m => lines.push(m.toString())); bot.on('error', e => console.log('ERR', e.message));
const ask = async (x, w = 600) => { const n = lines.length; bot.chat(x); await sleep(w); return lines.slice(n).join(' | '); };
let fails = 0; const R = (n, ok, extra = '') => { console.log(`${ok ? 'PASS' : 'FAIL'} ${n} ${extra}`); if (!ok) fails++; };
const srv = async () => { const t = await ask('/execute if entity @e[type=minecraft:interaction,tag=emberfall_rift_target]', 500); return /Test passed\. Count: (\d+)/i.test(t) ? +/Count: (\d+)/i.exec(t)[1] : (/Test failed/i.test(t) ? 0 : -1); };
const cli = () => Object.values(bot.entities).filter(e => e.name === 'interaction').length;
const dim = async () => /entity data: "emberfall:expedition"/.test(await ask('/data get entity EmberTester Dimension', 500)) ? 'expedition' : 'other';
const forced = async () => /is marked for force loading/.test(await ask('/forceload query 100 100', 500));
bot.once('spawn', async () => {
  await sleep(4000);
  await ask('/gamemode creative', 400); await ask('/emberfall rift clear', 200); await ask('/emberfall rift closeall', 200);
  await ask('/fill 94 199 94 106 199 106 minecraft:stone', 900); await ask('/fill 94 200 94 106 235 106 minecraft:air', 900);
  await ask('/tp @s 100 200 100', 800); await sleep(1500);
  await ask('/gamemode survival', 400); await ask('/effect give @s minecraft:resistance 999 4 true', 300);
  await ask('/character select juggernaut', 700); await ask('/clear @s', 300);
  R('K0 nothing is forced before a Rift opens', !(await forced()));
  await ask('/emberfall rift open', 900); await sleep(6500);
  R('K1 opening a Rift holds its chunk', await forced());
  R('K2 the target stands on the server and the client sees it', (await srv()) === 1 && cli() === 1, `server=${await srv()} client=${cli()}`);
  await ask('/tp @s 100.5 200 103.5', 600); await sleep(1200);
  const t = Object.values(bot.entities).find(e => e.name === 'interaction');
  await bot.activateEntity(t); await sleep(300);
  let inRun = false; for (let i = 0; i < 45; i++) { await sleep(2000); if ((await dim()) === 'expedition') { inRun = true; break; } }
  R('K3 the player really is in a run', inRun);
  R('K4 DURING the run (nobody near the Rift) the target is still on the server', (await srv()) === 1, `server=${await srv()}`);
  await ask('/expedition leave', 1500);
  let back = false; for (let i = 0; i < 10 && !back; i++) { await sleep(1000); back = (await dim()) === 'other' && cli() === 1; }
  R('K5 back home the client sees the target again (a new spawn packet, not a stale entity)', back, `client=${cli()}`);
  R('K6 the target is still the only one (no duplicate from the return)', (await srv()) === 1, `server=${await srv()}`);
  await ask('/emberfall rift closeall', 800); await sleep(1500);
  R('K7 closing the Rift releases the chunk', !(await forced()));
  R('K8 and removes the target', (await srv()) === 0, `server=${await srv()}`);
  console.log(fails ? `FAILED ${fails}` : 'ALL PASS');
  process.exit(fails ? 1 : 0);
});
