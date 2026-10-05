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
  const before = await pk(); console.log('before', JSON.stringify(before));
  sendChoose('challenge', 0); await sleep(2000);
  await ask('/kill @e[type=!player,type=!minecraft:item_display,type=!minecraft:interaction]', 800);
  await sleep(6000); // pickups glint, drift to the player and are collected
  const after = await pk(); console.log('after ', JSON.stringify(after));
  check('P1 gold rose by at least 15', after && before && after.gold - before.gold >= 15, `delta=${after && before ? after.gold - before.gold : '?'}`);
  check('P2 xp rose by at least 20', after && before && after.xp - before.xp >= 20 || (after && before && after.level > before.level), `delta=${after && before ? after.xp - before.xp : '?'} level ${before && before.level}->${after && after.level}`);
  await ask('/expedition leave', 1500); await sleep(1200);
  const log = fs.readFileSync('../server_run.log', 'utf8'); const m = [...log.matchAll(/EmberTester earned (\d+) meta-currency \((\d+)s survived.*\[base (\d+), shrine bonus (\d+)\]/g)].pop();
  console.log('run end line:', m ? m[0] : 'NONE');
  // expected = round(base * 1.0) + 25 with no curse or greed; base from the same formula inputs is unknown here, so compare to the
  // formula-free lower bound: the payout must include the flat 25 bonus, which a 40 s run alone (4 silver + level + gold/10) cannot reach.
  check('P3 the shrine bonus is exactly the flat 25 and earned = base + 25', m && +m[4] === 25 && +m[1] === +m[3] + 25, m ? `earned=${m[1]} base=${m[3]} bonus=${m[4]}` : 'no line');
  console.log(fails === 0 ? 'ALL PASS' : 'SOME FAIL ' + fails); bot.quit(); setTimeout(() => process.exit(0), 400);
});
