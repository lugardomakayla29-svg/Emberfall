// Horde Charger claims, read from the TEST_MODE server trace (tick exact), not from sampled positions:
//  C1 wind-up lasts 20 ticks   C2 a sidestep AFTER the line locks is missed   C3 a player left in the line IS hit
//  C4 a wall ends the rush and stuns it (stun trace, and the brute stays put for the stun window)
const fs = require('fs');
const mineflayer = require('mineflayer');
const sleep = ms => new Promise(r => setTimeout(r, ms));
const bot = mineflayer.createBot({ host: '127.0.0.1', port: 25565, username: 'EmberTester', version: '1.21.11', auth: 'offline' });
const lines = []; bot.on('message', m => lines.push(m.toString())); bot.on('error', e => console.log('ERR', e.message));
const ask = async (x, w = 500) => { const n = lines.length; bot.chat(x); await sleep(w); return lines.slice(n).join(' | '); };
let fails = 0; const R = (n, ok, extra = '') => { console.log(`${ok ? 'PASS' : 'FAIL'} ${n} ${extra}`); if (!ok) fails++; };
const LOG = (process.env.EMBERFALL_HOME || '.') + '/run/server_run.log';
const trace = () => fs.readFileSync(LOG, 'utf8').split('\n').filter(l => l.includes('CHARGER_TEST')).map(l => l.slice(l.indexOf('CHARGER_TEST') + 13));
const pos = async () => { const r = await ask('/data get entity @e[tag=ch,limit=1] Pos', 120); const m = /\[(-?[\d.]+)d, (-?[\d.]+)d, (-?[\d.]+)d\]/.exec(r); return m ? [+m[1], +m[2], +m[3]] : null; };
const spawn = async dx => {
  await ask('/kill @e[type=emberfall:horde_charger]', 300);
  await ask(`/execute at @s positioned ~${dx} ~ ~ run emberfall spawnveteran horde_charger`, 500);
  await ask('/tag @e[type=emberfall:horde_charger,limit=1] add ch', 200);
  await ask('/data merge entity @e[tag=ch,limit=1] {Invulnerable:1b,PersistenceRequired:1b}', 300);
};
bot.once('spawn', async () => {
  await sleep(5000);
  await ask('/gamemode survival'); await ask('/effect clear @s'); await ask('/character select juggernaut'); await ask('/expedition', 2500);
  for (let i = 0; i < 40; i++) { await sleep(2000); if (/expedition/.test(await ask('/data get entity @s Dimension', 400))) break; }
  await sleep(2000);
  await ask('/emberfall wavestop 0', 300); await ask('/time set midnight', 200);
  await ask('/effect give @s minecraft:regeneration 999 3 true', 200);
  await ask('/kill @e[type=!player,type=!minecraft:item_display,type=!minecraft:interaction]', 900);
  const bx = Math.floor(bot.entity.position.x), by = Math.floor(bot.entity.position.y), bz = Math.floor(bot.entity.position.z);
  const tpTo = (x, z) => bot.chat(`/tp @s ${x + 0.5} ${by} ${z + 0.5} 0 0`);
  // ---- C1 + C3: stand still in the line
  let pin = setInterval(() => tpTo(bx, bz), 400);
  // ServerPlayer.isInvulnerableTo is true until the client reports its chunks loaded (hasClientLoaded), so the FIRST hit after
  // entering the run can return landed=false. Retry scenario A (up to 3 times) and say so, rather than blame the Charger.
  let base = trace().length, t = '', tries = 0;
  for (; tries < 3; tries++) {
    await ask('/kill @e[type=emberfall:horde_charger]', 300);
    base = trace().length;
    await spawn(9); await sleep(4000);
    t = trace().slice(base).join(' | ');
    if (/landed=true/.test(t)) break;
  }
  console.log(`  trace A (attempt ${tries + 1}):`, t);
  const w = /windup tick=(\d+)/.exec(t), r = /rush tick=(\d+)/.exec(t);
  R('C1 wind-up lasts exactly 20 ticks', !!(w && r && +r[1] - +w[1] === 20), w && r ? String(+r[1] - +w[1]) : 'no trace');
  R('C3 a player standing in the line is hit', /hit tick=\d+ dmg=[\d.]+ landed=true/.test(t));
  clearInterval(pin);
  // ---- C2: step 3 blocks aside the moment the line is locked (the rush trace line appears)
  await ask('/kill @e[type=emberfall:horde_charger]', 300);
  base = trace().length;
  pin = setInterval(() => tpTo(bx, bz), 400);
  await spawn(9);
  const t0 = Date.now();
  while (Date.now() - t0 < 6000) { await sleep(60); if (trace().slice(base).some(x => x.startsWith('rush'))) break; }
  clearInterval(pin); tpTo(bx, bz + 3);
  const pin2 = setInterval(() => tpTo(bx, bz + 3), 300);
  await sleep(2500); clearInterval(pin2);
  t = trace().slice(base).join(' | '); console.log('  trace B:', t);
  R('C2 a sidestep after the line locked is missed (rush ran, no hit landed)', /rush tick/.test(t) && !/landed=true/.test(t));
  // ---- C4: wall. Player stands IN the line (so the line points at the wall), wall 7 blocks behind the player.
  await ask('/kill @e[type=emberfall:horde_charger]', 300);
  const wx = bx - 7;
  await ask(`/fill ${wx} ${by} ${bz - 3} ${wx} ${by + 3} ${bz + 3} minecraft:stone`, 500);
  base = trace().length;
  pin = setInterval(() => tpTo(bx, bz), 400);
  await spawn(9);
  const t1 = Date.now(); let stunTick = null;
  while (Date.now() - t1 < 7000) { await sleep(60); const s = trace().slice(base).find(x => x.startsWith('stun')); if (s) { stunTick = s; break; } }
  t = trace().slice(base).join(' | '); console.log('  trace C:', t);
  R('C4a the wall ended the rush and stunned it', !!stunTick);
  if (stunTick) {
    const a = await pos(); await sleep(1200); const b = await pos();
    const moved = a && b ? Math.hypot(a[0] - b[0], a[2] - b[2]) : NaN;
    console.log(`  stunned position ${a && a.map(v => v.toFixed(2))} -> ${b && b.map(v => v.toFixed(2))} moved ${moved.toFixed(2)}`);
    R('C4b it stays put during the stun (moved under 0.4 blocks in 1.2 s)', moved < 0.4, moved.toFixed(2));
  }
  clearInterval(pin);
  await ask(`/fill ${wx} ${by} ${bz - 3} ${wx} ${by + 3} ${bz + 3} minecraft:air`, 500);
  await ask('/kill @e[type=emberfall:horde_charger]', 300); await ask('/expedition leave', 800);
  console.log(fails === 0 ? 'ALL PASS' : 'SOME FAIL ' + fails);
  bot.quit(); setTimeout(() => process.exit(0), 300);
});
