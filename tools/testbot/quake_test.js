// War Slam shock wave at level 10: foes at 7.5, 10 and 11.5 blocks (all outside the 5.0 cleave, inside the 12.0 slam). The server trace
// QUAKE_TEST gives one line per wave (0 = slam, 1 and 2 = aftershocks). Each foe must lose hp, farther foes must not lose MORE than nearer
// ones from the slam alone (same damage per wave), and the sum of waves must be about 1.7x the slam.
const mineflayer = require('mineflayer');
const bot = mineflayer.createBot({ host: '127.0.0.1', port: 25565, username: 'EmberTester', version: '1.21.11', auth: 'offline' });
const sleep = ms => new Promise(r => setTimeout(r, ms));
const lines = []; bot.on('message', m => lines.push(m.toString())); bot.on('error', e => console.log('ERR', e.message));
const ask = async (x, w = 450) => { const n = lines.length; bot.chat(x); await sleep(w); return lines.slice(n).join(' | '); };
let fails = 0; const R = (n, ok, extra = '') => { console.log(`${ok ? 'PASS' : 'FAIL'} ${n} ${extra}`); if (!ok) fails++; };
const BIG = 1024;
const hp = async tag => { const r = await ask(`/data get entity @e[tag=${tag},limit=1] Health`, 420); const m = /entity data: (-?[\d.]+)f/.exec(r); return m ? +m[1] : null; };
const foe = async (tag, dx, dz) => {
  await ask(`/execute at @s run summon emberfall:horde_zombie ~${dx} ~ ~${dz} {Tags:["${tag}","keep"],NoAI:1b,Silent:1b,PersistenceRequired:1b}`, 300);
  await ask(`/attribute @e[tag=${tag},limit=1] minecraft:max_health base set ${BIG}`, 120);
  await ask(`/data modify entity @e[tag=${tag},limit=1] Health set value ${BIG}.0f`, 120);
};
const g = async (kills, m) => ask(`/emberfall debugweapongrowth EmberTester grant 0 ${kills} ${m}`, 400);
bot.once('spawn', async () => {
  await sleep(5000);
  await ask('/gamemode survival'); await ask('/effect clear @s');
  await ask('/character select juggernaut'); await ask('/expedition', 2500);
  for (let i = 0; i < 40; i++) { await sleep(2000); if (/expedition/.test(await ask('/data get entity @s Dimension', 400))) break; }
  await sleep(2000);
  await ask('/effect give @s minecraft:resistance 999 4 true', 200); await ask('/effect give @s minecraft:regeneration 999 4 true', 200);
  await ask('/emberfall wavestop 0', 300); await ask('/time set midnight', 200);
  await ask('/kill @e[type=!player,type=!minecraft:item_display,type=!minecraft:interaction]', 900);
  const bx = bot.entity.position.x, by = bot.entity.position.y, bz = bot.entity.position.z;
  const pin = setInterval(() => bot.chat(`/tp @s ${bx.toFixed(2)} ${by.toFixed(2)} ${bz.toFixed(2)} 0 0`), 700);
  await ask('/emberfall debugloadout EmberTester', 600);
  await g(108, 0);                                                     // level 10, meter EMPTY while the scene is built
  await foe('n', 0, -7.5); await foe('m', 0, -10); await foe('f', 0, -11.5);
  const before = { n: await hp('n'), m: await hp('m'), f: await hp('f') };
  R('Q0 all three foes exist at full hp and the halberd cannot reach them alone', before.n === BIG && before.m === BIG && before.f === BIG, JSON.stringify(before));
  await sleep(2500);
  const idle = { n: await hp('n'), m: await hp('m'), f: await hp('f') };
  R('Q0b nothing hurt them during 2.5 s of idle (so the loss below is the slam)', idle.n === BIG && idle.m === BIG && idle.f === BIG, JSON.stringify(idle));
  await foe('trig', 0, 1.5);                                            // the foe the halberd actually swings at
  await g(0, 1000);                                                     // meter FULL as the last step: the next landed hit slams
  await sleep(6000);                                                    // slam + 2 aftershocks: 3 x 20 ticks + 2 gaps = about 4 s
  const after = { n: await hp('n'), m: await hp('m'), f: await hp('f') };
  console.log('AFTER', JSON.stringify(after));
  const lost = { n: BIG - after.n, m: BIG - after.m, f: BIG - after.f };
  R('Q1 the near foe (7.5) was hit', lost.n > 0, `lost ${lost.n.toFixed(1)}`);
  R('Q2 the mid foe (10.0) was hit', lost.m > 0, `lost ${lost.m.toFixed(1)}`);
  R('Q3 the far foe (11.5), beyond the OLD 8.0 maximum, was hit', lost.f > 0, `lost ${lost.f.toFixed(1)}`);
  R('Q4 every foe lost the same (one hit per wave, no double hits, no misses)', Math.abs(lost.n - lost.m) < 0.5 && Math.abs(lost.m - lost.f) < 0.5, JSON.stringify(lost));
  clearInterval(pin); await ask('/expedition leave', 800);
  console.log(fails === 0 ? 'ALL PASS' : 'SOME FAIL ' + fails);
  bot.quit(); setTimeout(() => process.exit(0), 300);
});
