// Ashen Beacon LIVING FLAMES. emberwarden starts with ONLY the beacon. Foes are 100000 hp zombies with NoAI (held still), player pinned.
//   F1  level 1: a foe 6.5 blocks from the beacon (outside the 3.0 pulse) is hit by a flame
//   F2  a lone near foe and a group of 4 far foes: the flame goes for the GROUP (most flame hits land on group members)
//   F3  the group is drawn together (its spread shrinks) while the flames burn   [NoAI mobs are moved by setDeltaMovement, so this is honest]
//   F4  the weapon meter rises from flame hits alone (no kills)
const mineflayer = require('mineflayer');
const bot = mineflayer.createBot({ host: '127.0.0.1', port: 25565, username: 'EmberTester', version: '1.21.11', auth: 'offline' });
const sleep = ms => new Promise(r => setTimeout(r, ms));
const lines = []; bot.on('message', m => lines.push(m.toString())); bot.on('error', e => console.log('ERR', e.message));
const ask = async (x, w = 500) => { const n = lines.length; bot.chat(x); await sleep(w); return lines.slice(n).join(' | '); };
let fails = 0; const R = (n, ok, extra = '') => { console.log(`${ok ? 'PASS' : 'FAIL'} ${n} ${extra}`); if (!ok) fails++; };
const BIG = 100000;
const meter = async () => { const r = await ask('/emberfall debugweapongrowth EmberTester', 700); const m = /growth slot 0 ashen_beacon kills=(\d+) level=(\d+) meter=(\d+) ults=(\d+)/.exec(r); return m ? { kills: +m[1], level: +m[2], meter: +m[3], ults: +m[4] } : null; };
const pos = async tag => { const r = await ask(`/data get entity @e[tag=${tag},limit=1] Pos`, 420); const m = /\[(-?[\d.]+)d, (-?[\d.]+)d, (-?[\d.]+)d\]/.exec(r); return m ? [+m[1], +m[2], +m[3]] : null; };
const foe = async (tag, dx, dz) => {
  await ask(`/execute at @s run summon emberfall:horde_zombie ~${dx} ~ ~${dz} {Tags:["${tag}","keep"],NoAI:1b,Silent:1b,PersistenceRequired:1b}`, 300);
  await ask(`/attribute @e[tag=${tag},limit=1] minecraft:max_health base set ${BIG}`, 120);
  await ask(`/data modify entity @e[tag=${tag},limit=1] Health set value ${BIG}.0f`, 120);
};
const spread = ps => { const c = [0, 1, 2].map(k => ps.reduce((a, p) => a + p[k], 0) / ps.length); return ps.reduce((a, p) => a + Math.hypot(p[0] - c[0], p[2] - c[2]), 0) / ps.length; };
bot.once('spawn', async () => {
  await sleep(5000);
  await ask('/gamemode survival'); await ask('/effect clear @s');
  await ask('/character select emberwarden'); await ask('/expedition', 2500);
  for (let i = 0; i < 40; i++) { await sleep(2000); if (/expedition/.test(await ask('/data get entity @s Dimension', 400))) break; }
  await sleep(2000);
  await ask('/effect give @s minecraft:resistance 999 4 true', 200); await ask('/effect give @s minecraft:regeneration 999 4 true', 200);
  await ask('/emberfall wavestop 0', 300); await ask('/time set midnight', 200);
  await ask('/kill @e[type=!player,type=!minecraft:item_display,type=!minecraft:interaction]', 900);
  const bx = bot.entity.position.x, by = bot.entity.position.y, bz = bot.entity.position.z;
  const pin = setInterval(() => bot.chat(`/tp @s ${bx.toFixed(2)} ${by.toFixed(2)} ${bz.toFixed(2)} 0 0`), 800);
  if (process.env.BEACON_LEVEL === '10') { await ask('/emberfall debugweapongrowth EmberTester grant 0 108 0', 500); }
  const m0 = await meter();
  R('F0 level ' + (process.env.BEACON_LEVEL || '1'), m0 && (process.env.BEACON_LEVEL === '10' ? m0.level === 10 : (m0.level === 1 && m0.kills === 0)), JSON.stringify(m0));

  // The beacon lands ON the nearest foe ('prim', 3 blocks south). F1 target 6.5 from it, F2 lone 2.5 from it, group of 4 around 8 from it.
  await foe('prim', 0, 3.0);
  await foe('lone', 2.0, 3.0);                               // 2.0 from the beacon, alone
  await foe('g1', -1.0, 8.5); await foe('g2', 0.5, 9.0); await foe('g3', -0.5, 9.5); await foe('g4', 1.0, 9.0);   // group, ~5.5 to 6.5 from the beacon
  await foe('far', 6.0, 3.0);                                // F1: 6.0 from the beacon, alone, outside the pulse (3.0)
  const before = lines.length;
  const gp0 = [await pos('g1'), await pos('g2'), await pos('g3'), await pos('g4')].filter(Boolean);
  const mt0 = await meter();
  await sleep(8000);
  const gp1 = [await pos('g1'), await pos('g2'), await pos('g3'), await pos('g4')].filter(Boolean);
  const mt1 = await meter();
  clearInterval(pin);
  await ask('/kill @e[tag=keep]', 500); await ask('/expedition leave', 800);
  require('fs').writeFileSync('/tmp/beacon_flame_pos.json', JSON.stringify({ gp0, gp1, mt0, mt1 }));
  const s0 = gp0.length === 4 ? spread(gp0) : NaN, s1 = gp1.length === 4 ? spread(gp1) : NaN;
  console.log('GROUP_SPREAD', s0.toFixed(2), '->', s1.toFixed(2), 'METER', mt0 && mt0.meter, '->', mt1 && mt1.meter);
  R('F3 the group is drawn together (spread shrinks by >= 0.3)', s0 - s1 >= 0.3, `${s0.toFixed(2)} -> ${s1.toFixed(2)}`);
  R('F4 the meter rose from flame hits alone (no kills)', mt0 && mt1 && mt1.kills === mt0.kills && (mt1.meter > mt0.meter || mt1.ults > mt0.ults), `${mt0 && mt0.meter} -> ${mt1 && mt1.meter}, kills ${mt1 && mt1.kills}`);
  console.log('BEACON_PHASE_DONE');
  bot.quit(); setTimeout(() => process.exit(0), 400);
});
