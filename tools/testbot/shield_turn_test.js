// Does the Shieldbearer turn slowly? It walks toward the player; the player then jumps to the opposite side and we sample
// the mob's yaw every ~60 ms. Expect a half circle to take ~1.5 s (6 deg/tick) and never exceed ~6 deg/tick.
// A plain horde zombie is the control: it must turn far faster.
const mineflayer = require('mineflayer');
const sleep = ms => new Promise(r => setTimeout(r, ms));
const bot = mineflayer.createBot({ host: '127.0.0.1', port: 25565, username: 'EmberTester', version: '1.21.11', auth: 'offline' });
const lines = []; bot.on('message', m => lines.push(m.toString())); bot.on('error', e => console.log('ERR', e.message));
const ask = async (x, w = 500) => { const n = lines.length; bot.chat(x); await sleep(w); return lines.slice(n).join(' | '); };
let fails = 0; const R = (n, ok, extra = '') => { console.log(`${ok ? 'PASS' : 'FAIL'} ${n} ${extra}`); if (!ok) fails++; };
const yaw = async tag => { const r = await ask(`/data get entity @e[tag=${tag},limit=1] Rotation`, 90); const m = /\[(-?[\d.]+)f, (-?[\d.]+)f\]/.exec(r); return m ? +m[1] : null; };
const wrap = a => ((a + 540) % 360) - 180;
async function measure(label, type) {
  await ask('/kill @e[tag=tt]', 300);
  const bx = bot.entity.position.x, by = bot.entity.position.y, bz = bot.entity.position.z;
  bot.chat(`/tp @s ${bx.toFixed(2)} ${by} ${bz.toFixed(2)} 0 0`);
  await ask(`/execute at @s positioned ~0 ~ ~6 run emberfall spawnveteran ${type}`, 500);     // mob 6 south of the player
  await ask(`/tag @e[type=emberfall:${type},limit=1] add tt`, 200);
  await ask('/data merge entity @e[tag=tt,limit=1] {Invulnerable:1b,PersistenceRequired:1b}', 300);
  // hold the player still until the mob has walked up and faces him (yaw ~180 = facing north)
  const pin = setInterval(() => bot.chat(`/tp @s ${bx.toFixed(2)} ${by} ${bz.toFixed(2)} 0 0`), 400);
  await sleep(3500); clearInterval(pin);
  const y0 = await yaw('tt');
  // jump to a point 10 blocks NORTH of where the mob now is (it has to turn ~180)
  const mp = await ask('/data get entity @e[tag=tt,limit=1] Pos', 200); const m = /\[(-?[\d.]+)d, (-?[\d.]+)d, (-?[\d.]+)d\]/.exec(mp);
  const mx = +m[1], mz = +m[3];
  const far = mz + 9;        // the mob was facing north toward us; the player goes SOUTH of it instead
  const t0 = Date.now(); const pin2 = setInterval(() => bot.chat(`/tp @s ${mx.toFixed(2)} ${by} ${far.toFixed(2)} 0 0`), 250);
  const samples = []; let prev = y0, tPrev = t0, maxRate = 0;
  while (Date.now() - t0 < 4000) {
    const y = await yaw('tt'); const t = Date.now(); if (y === null) continue;
    const rate = Math.abs(wrap(y - prev)) / ((t - tPrev) / 50);     // degrees per 50 ms tick
    if (rate > maxRate) maxRate = rate; samples.push([(t - t0) / 1000, y]);
    prev = y; tPrev = t;
  }
  clearInterval(pin2);
  const turned = samples.find(s => Math.abs(wrap(s[1] - y0)) >= 150);
  console.log(`  ${label}: start yaw ${y0 && y0.toFixed(0)}, reached 150 deg of turn at ${turned ? turned[0].toFixed(2) + ' s' : 'NEVER'}, max rate ${maxRate.toFixed(1)} deg/tick`);
  await ask('/kill @e[tag=tt]', 300);
  return { t150: turned ? turned[0] : null, maxRate };
}
bot.once('spawn', async () => {
  await sleep(5000);
  await ask('/gamemode survival'); await ask('/character select juggernaut'); await ask('/expedition', 2500);
  for (let i = 0; i < 40; i++) { await sleep(2000); if (/expedition/.test(await ask('/data get entity @s Dimension', 400))) break; }
  await sleep(2000);
  await ask('/emberfall wavestop 0', 300); await ask('/time set midnight', 200);
  await ask('/effect give @s minecraft:resistance 999 4 true', 200);
  await ask('/kill @e[type=!player,type=!minecraft:item_display,type=!minecraft:interaction]', 900);
  const s = await measure('shieldbearer', 'horde_shieldbearer');
  const z = await measure('zombie (control)', 'horde_zombie');
  R('T1 the Shieldbearer needs at least 0.8 s to turn 150 degrees', s.t150 === null || s.t150 >= 0.8, s.t150 === null ? 'did not get there in 4 s' : s.t150.toFixed(2) + ' s');
  R('T2 the zombie control turns 150 degrees faster than the Shieldbearer', z.t150 !== null && (s.t150 === null || z.t150 < s.t150), `zombie ${z.t150 && z.t150.toFixed(2)} s`);
  await ask('/expedition leave', 800);
  console.log(fails === 0 ? 'ALL PASS' : 'SOME FAIL ' + fails);
  bot.quit(); setTimeout(() => process.exit(0), 300);
});
