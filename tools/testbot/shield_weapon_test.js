// Real weapon, real path: the run player's own auto-weapon (Juggernaut halberd) attacks a frozen Shieldbearer, once with the
// player standing in FRONT of its shield and once BEHIND it. Same 8 s, same mob, same weapon. The SHIELD_TEST trace lists
// every landed hit, so we can count blocked vs unblocked hits and compare damage taken.
const fs = require('fs');
const mineflayer = require('mineflayer');
const sleep = ms => new Promise(r => setTimeout(r, ms));
const bot = mineflayer.createBot({ host: '127.0.0.1', port: 25565, username: 'EmberTester', version: '1.21.11', auth: 'offline' });
const lines = []; bot.on('message', m => lines.push(m.toString())); bot.on('error', e => console.log('ERR', e.message));
const ask = async (x, w = 500) => { const n = lines.length; bot.chat(x); await sleep(w); return lines.slice(n).join(' | '); };
let fails = 0; const R = (n, ok, extra = '') => { console.log(`${ok ? 'PASS' : 'FAIL'} ${n} ${extra}`); if (!ok) fails++; };
const LOG = (process.env.EMBERFALL_HOME || '.') + '/run/server_run.log';
const trace = () => fs.readFileSync(LOG, 'utf8').split('\n').filter(l => l.includes('SHIELD_TEST hit')).map(l => l.slice(l.indexOf('SHIELD_TEST') + 12));
const hp = async () => { const r = await ask('/data get entity @e[tag=sb,limit=1] Health', 150); const m = /: ([\d.]+)f/.exec(r); return m ? +m[1] : NaN; };
async function round(label, dz) {
  await ask('/kill @e[tag=sb]', 300);
  const bx = bot.entity.position.x, by = bot.entity.position.y, bz = bot.entity.position.z;
  // mob frozen, facing SOUTH (+Z). dz > 0 puts the player south of it (front); dz < 0 north of it (behind).
  await ask(`/execute at @s positioned ~0 ~ ~${-dz} run emberfall spawnveteran horde_shieldbearer`, 500);
  await ask('/tag @e[type=emberfall:horde_shieldbearer,limit=1] add sb', 200);
  await ask('/data merge entity @e[tag=sb,limit=1] {NoAI:1b,PersistenceRequired:1b,Rotation:[0.0f,0.0f],Health:1000.0f,attributes:[{id:"minecraft:max_health",base:1000.0d}]}', 400);
  await ask(`/tp @e[tag=sb,limit=1] ${bx.toFixed(2)} ${by} ${(bz - dz).toFixed(2)} 0 0`, 300);
  const pin = setInterval(() => bot.chat(`/tp @s ${bx.toFixed(2)} ${by} ${bz.toFixed(2)} 0 0`), 350);
  const mpos = await ask('/data get entity @e[tag=sb,limit=1] Pos', 200), ppos = await ask('/data get entity @s Pos', 200);
  console.log(`  ${label}: mob ${mpos.slice(-48)} | player ${ppos.slice(-48)} | me ${bx.toFixed(1)},${by},${bz.toFixed(1)}`);
  const base = trace().length, h0 = await hp(); await sleep(8000);
  const h1 = await hp(); clearInterval(pin);
  const t = trace().slice(base), blocked = t.filter(x => /blocked=true/.test(x)).length, open = t.length - blocked;
  const dealt = t.reduce((a, x) => a + +(/amount=([\d.]+)/.exec(x) || [0, 0])[1], 0);
  console.log(`  ${label}: hits ${t.length} (blocked ${blocked}, open ${open}), weapon damage offered ${dealt.toFixed(1)}, mob lost ${(h0 - h1).toFixed(1)} hp`);
  return { hits: t.length, blocked, open, lost: h0 - h1, offered: dealt };
}
bot.once('spawn', async () => {
  await sleep(5000);
  await ask('/gamemode survival'); await ask('/character select juggernaut'); await ask('/expedition', 2500);
  for (let i = 0; i < 40; i++) { await sleep(2000); if (/expedition/.test(await ask('/data get entity @s Dimension', 400))) break; }
  await sleep(2000);
  await ask('/emberfall wavestop 0', 300); await ask('/time set midnight', 200);
  await ask('/effect give @s minecraft:resistance 999 4 true', 200);
  await ask('/kill @e[type=!player,type=!minecraft:item_display,type=!minecraft:interaction]', 900);
  const F = await round('FRONT (player south of a south-facing mob)', 1.8);
  const B = await round('BACK  (player north of it)', -1.8);
  R('W1 the weapon landed hits in both rounds', F.hits > 0 && B.hits > 0, `front ${F.hits}, back ${B.hits}`);
  R('W2 every front hit was blocked', F.hits > 0 && F.blocked === F.hits, `${F.blocked}/${F.hits}`);
  R('W3 no back hit was blocked', B.hits > 0 && B.blocked === 0, `${B.blocked}/${B.hits}`);
  const ratio = (F.lost / Math.max(1, F.offered)), ratioB = (B.lost / Math.max(1, B.offered));
  console.log(`  share of offered damage that landed: front ${(ratio * 100).toFixed(0)}%, back ${(ratioB * 100).toFixed(0)}%`);
  R('W4 damage that got through from the front is near 20 percent of what was offered', ratio > 0.12 && ratio < 0.3, (ratio * 100).toFixed(0) + '%');
  await ask('/kill @e[tag=sb]', 300); await ask('/expedition leave', 800);
  console.log(fails === 0 ? 'ALL PASS' : 'SOME FAIL ' + fails);
  bot.quit(); setTimeout(() => process.exit(0), 300);
});
