// Challenge payout: clearing the small trial drops 15 Gold + 20 XP as pickups the player collects, and the run's Silver
// is raised by the flat bonus. Numbers come from the server (pickupstate) and the run-end log line.
const mineflayer = require('mineflayer'); const fs = require('fs'); const { Vec3 } = require('vec3');
const D = JSON.parse(fs.readFileSync('../mod/src/main/resources/data/emberfall/map/expedition_map.json', 'utf8'));
const sleep = ms => new Promise(r => setTimeout(r, ms));
const bot = mineflayer.createBot({ host: '127.0.0.1', port: 25565, username: 'EmberTester', version: '1.21.11', auth: 'offline' });
const lines = []; bot.on('message', m => lines.push(m.toString())); bot.on('error', e => console.log('ERR', e.message));
const ask = async (x, w = 500) => { const n = lines.length; bot.chat(x); await sleep(w); return lines.slice(n).join(' | '); };
let fails = 0; const check = (n, ok, note = '') => { console.log((ok ? 'PASS ' : 'FAIL ') + n + ' ' + note); if (!ok) fails++; };
const sendChoose = (type, option) => { const t = Buffer.from(type, 'utf8'); bot._client.write('custom_payload', { channel: 'emberfall:choose_shrine', data: Buffer.concat([Buffer.from([t.length]), t, Buffer.from([option])]) }); };
const pk = async () => { for (let k = 0; k < 4; k++) { const r = await ask('/emberfall pickupstate EmberTester', 600); const m = /PICKUPSTATE gold=(\d+) xpTotal=(\d+) level=(\d+)/.exec(r); if (m) return { gold: +m[1], xp: +m[2], level: +m[3] }; } return null; };
const C = D.shrines.find(s => s.type === 'challenge');
bot.once('spawn', async () => {
  await sleep(6500);
  await ask('/gamemode survival'); await ask('/effect give @s minecraft:resistance 999 4 true', 300); await ask('/effect give @s minecraft:regeneration 999 4 true', 300);
  await ask('/character select juggernaut', 600); await ask('/expedition', 1500);
  for (let i = 0; i < 60; i++) { await sleep(2000); if (/expedition/.test(await ask('/data get entity @s Dimension', 400))) break; }
  await sleep(1500); await ask('/emberfall wavestop 0', 500);
  await ask('/kill @e[type=!player,type=!minecraft:item_display,type=!minecraft:interaction]', 600);
  await ask(`/tp @s ${C.x + 4} 65 ${C.z + 4}`, 1500);
  const free = async () => +((await ask('/emberfall relic chests EmberTester', 700)).match(/free=(\d+)/)?.[1] ?? NaN);
  const f0 = await free();
  check('SH0 no free chest before the shrine is started', f0 === 0, 'free=' + f0);
  // control: killing bystanders without starting a challenge leaves nothing
  await ask('/kill @e[type=!player,type=!minecraft:item_display,type=!minecraft:interaction]', 800); await sleep(1500);
  const fc = await free();
  check('SH1 control: no challenge started, still no free chest', fc === 0, 'free=' + fc);
  sendChoose('challenge', 0); await sleep(2000);
  await ask('/kill @e[type=!player,type=!minecraft:item_display,type=!minecraft:interaction]', 800);
  await sleep(4000);
  const f1 = await free();
  check('SH2 a cleared challenge shrine leaves exactly one free chest', f1 === 1, 'free=' + f1);
  await ask('/expedition leave', 1200);
  console.log(fails === 0 ? 'ALL PASS' : 'SOME FAIL ' + fails); bot.quit(); setTimeout(() => process.exit(0), 400);
});
