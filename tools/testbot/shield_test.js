// Horde Shieldbearer, frozen (NoAI) and facing +Z (yaw 0, i.e. SOUTH). Damage comes from a marker attacker placed at known
// compass points via /damage ... by <attacker>. Expect: front (south) and the 55 degree shoulders are cut to 20 percent,
// the sides (east/west) and the back (north) pass in full. The server trace SHIELD_TEST gives amount/passed/blocked.
const fs = require('fs');
const mineflayer = require('mineflayer');
const sleep = ms => new Promise(r => setTimeout(r, ms));
const bot = mineflayer.createBot({ host: '127.0.0.1', port: 25565, username: 'EmberTester', version: '1.21.11', auth: 'offline' });
const lines = []; bot.on('message', m => lines.push(m.toString())); bot.on('error', e => console.log('ERR', e.message));
const ask = async (x, w = 500) => { const n = lines.length; bot.chat(x); await sleep(w); return lines.slice(n).join(' | '); };
let fails = 0; const R = (n, ok, extra = '') => { console.log(`${ok ? 'PASS' : 'FAIL'} ${n} ${extra}`); if (!ok) fails++; };
const LOG = (process.env.EMBERFALL_HOME || '.') + '/run/server_run.log';
const trace = () => fs.readFileSync(LOG, 'utf8').split('\n').filter(l => l.includes('SHIELD_TEST')).map(l => l.slice(l.indexOf('SHIELD_TEST') + 12));
bot.once('spawn', async () => {
  await sleep(5000);
  await ask('/gamemode survival'); await ask('/character select juggernaut'); await ask('/expedition', 2500);
  for (let i = 0; i < 40; i++) { await sleep(2000); if (/expedition/.test(await ask('/data get entity @s Dimension', 400))) break; }
  await sleep(2000);
  await ask('/emberfall wavestop 0', 300); await ask('/time set midnight', 200);
  await ask('/effect give @s minecraft:resistance 999 4 true', 200);
  await ask('/kill @e[type=!player,type=!minecraft:item_display,type=!minecraft:interaction]', 900);
  // the Shieldbearer stands 8 blocks east of the player, frozen, facing SOUTH (+Z, yaw 0)
  await ask('/execute at @s positioned ~8 ~ ~ run emberfall spawnveteran horde_shieldbearer', 500);
  await ask('/tag @e[type=emberfall:horde_shieldbearer,limit=1] add sb', 200);
  await ask('/data merge entity @e[tag=sb,limit=1] {NoAI:1b,PersistenceRequired:1b,Rotation:[0.0f,0.0f],Health:1000.0f,attributes:[{id:"minecraft:max_health",base:1000.0d}]}', 400);
  const r = await ask('/data get entity @e[tag=sb,limit=1] Rotation', 300); console.log('  facing:', r.slice(-40));
  const atk = async (name, dx, dz) => {
    await ask('/kill @e[tag=atk]', 200);
    await ask(`/execute at @e[tag=sb,limit=1] run summon minecraft:armor_stand ~${dx} ~ ~${dz} {Tags:["atk"],Invisible:1b,NoGravity:1b,Marker:1b}`, 350);
    const base = trace().length;
    await ask('/damage @e[tag=sb,limit=1] 10 minecraft:player_attack by @e[tag=atk,limit=1]', 450);
    const t = trace().slice(base).pop() || 'NO TRACE';
    console.log(`  ${name.padEnd(12)} ${t}`); return t;
  };
  const S = await atk('south front', 0, 4), N = await atk('north back', 0, -4);
  const E = await atk('east side', 4, 0), W = await atk('west side', -4, 0);
  const SE = await atk('SE 40 deg', 2.6, 3.1), SW = await atk('SW 40 deg', -2.6, 3.1);
  const SE70 = await atk('SE 70 deg', 3.8, 1.4);
  const blocked = t => /blocked=true/.test(t), pass = t => +(/passed=([\d.]+)/.exec(t) || [0, NaN])[1];
  R('S1 a hit from the front (south) is blocked and cut to 2.0', blocked(S) && Math.abs(pass(S) - 2) < 0.01, S);
  R('S2 a hit from behind (north) is NOT blocked and passes 10.0', !blocked(N) && Math.abs(pass(N) - 10) < 0.01, N);
  R('S3 a hit from the east side is NOT blocked', !blocked(E), E);
  R('S4 a hit from the west side is NOT blocked', !blocked(W), W);
  R('S5 both front shoulders (40 degrees) are blocked', blocked(SE) && blocked(SW));
  R('S6 a hit at 70 degrees (outside the 55 degree cone) is NOT blocked', !blocked(SE70), SE70);
  await ask('/kill @e[tag=atk]', 200); await ask('/kill @e[tag=sb]', 200); await ask('/expedition leave', 800);
  console.log(fails === 0 ? 'ALL PASS' : 'SOME FAIL ' + fails);
  bot.quit(); setTimeout(() => process.exit(0), 300);
});
