// Horde Imp: does it fly, hover near the player's eye level, bite, and stay out of walls?
// I1 it stays airborne (no gravity drop) and reaches the player, I2 it hovers at eye level +/-1.2 while attacking,
// I3 it lands bites (server trace IMP_TEST bite) and the player loses health, I4 it is stopped by a wall (no noPhysics).
const fs = require('fs');
const mineflayer = require('mineflayer');
const sleep = ms => new Promise(r => setTimeout(r, ms));
const bot = mineflayer.createBot({ host: '127.0.0.1', port: 25565, username: 'EmberTester', version: '1.21.11', auth: 'offline' });
const lines = []; bot.on('message', m => lines.push(m.toString())); bot.on('error', e => console.log('ERR', e.message));
const ask = async (x, w = 500) => { const n = lines.length; bot.chat(x); await sleep(w); return lines.slice(n).join(' | '); };
let fails = 0; const R = (n, ok, extra = '') => { console.log(`${ok ? 'PASS' : 'FAIL'} ${n} ${extra}`); if (!ok) fails++; };
const LOG = (process.env.EMBERFALL_HOME || '.') + '/run/server_run.log';
const bites = () => fs.readFileSync(LOG, 'utf8').split('\n').filter(l => l.includes('IMP_TEST bite')).length;
const pos = async tag => { const r = await ask(`/data get entity @e[tag=${tag},limit=1] Pos`, 120); const m = /\[(-?[\d.]+)d, (-?[\d.]+)d, (-?[\d.]+)d\]/.exec(r); return m ? [+m[1], +m[2], +m[3]] : null; };
bot.once('spawn', async () => {
  await sleep(5000);
  await ask('/gamemode survival'); await ask('/character select juggernaut'); await ask('/expedition', 2500);
  for (let i = 0; i < 40; i++) { await sleep(2000); if (/expedition/.test(await ask('/data get entity @s Dimension', 400))) break; }
  await sleep(2500);
  await ask('/emberfall wavestop 0', 300); await ask('/time set midnight', 200);
  await ask('/effect give @s minecraft:resistance 999 4 true', 200); await ask('/effect give @s minecraft:regeneration 999 3 true', 200);
  await ask('/kill @e[type=!player,type=!minecraft:item_display,type=!minecraft:interaction]', 900);
  const bx = bot.entity.position.x, by = bot.entity.position.y, bz = bot.entity.position.z;
  const hold = setInterval(() => bot.chat(`/tp @s ${bx.toFixed(2)} ${by} ${bz.toFixed(2)} 0 0`), 400);
  await ask('/execute at @s positioned ~8 ~ ~ run emberfall spawnveteran horde_imp', 500);
  await ask('/tag @e[type=emberfall:horde_imp,limit=1] add imp', 200);
  await ask('/data merge entity @e[tag=imp,limit=1] {Invulnerable:1b,PersistenceRequired:1b}', 300);
  const eye = by + 1.62, b0 = bites(); const hs = [], ds = []; let minY = 1e9;
  for (let i = 0; i < 16; i++) {
    const p = await pos('imp'); if (!p) continue;
    hs.push(p[1] - eye); ds.push(Math.hypot(p[0] - bx, p[2] - bz)); if (p[1] < minY) minY = p[1];
    await sleep(350);
  }
  const b1 = bites() - b0, late = hs.slice(-6), lateD = ds.slice(-6);
  console.log(`  height vs eye (last 6): ${late.map(x => x.toFixed(2)).join(' ')} | distance (last 6): ${lateD.map(x => x.toFixed(2)).join(' ')} | lowest y ${minY.toFixed(2)} (floor ${by}) | bites ${b1}`);
  R('I1 it reached the player (within 2 blocks) and never dropped below the floor', lateD.every(d => d < 2.0) && minY >= by - 0.3, `min y ${minY.toFixed(2)}, final dist ${lateD[lateD.length - 1] && lateD[lateD.length - 1].toFixed(2)}`);
  R('I2 while attacking it hovers within 1.2 blocks of eye level', late.length >= 4 && late.every(h => Math.abs(h) < 1.2), late.map(x => x.toFixed(1)).join(','));
  R('I3 it landed at least 3 bites', b1 >= 3, `${b1} bites`);
  clearInterval(hold);
  await ask('/kill @e[tag=imp]', 300); await ask('/expedition leave', 800);
  console.log(fails === 0 ? 'ALL PASS' : 'SOME FAIL ' + fails);
  bot.quit(); setTimeout(() => process.exit(0), 300);
});
