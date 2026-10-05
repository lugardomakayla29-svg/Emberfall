// Do the Tiki heads face the player? Heads use FIXED billboards, so they follow the display yaw the rig writes from yBodyRot.
// Measure: angle between the head display's facing and the bearing from the Tiki to the player, over 10 s of a live chase.
const mineflayer = require('mineflayer');
const sleep = ms => new Promise(r => setTimeout(r, ms));
const bot = mineflayer.createBot({ host: '127.0.0.1', port: 25565, username: 'EmberTester', version: '1.21.11', auth: 'offline' });
const chat = []; bot.on('message', m => chat.push(m.toString())); bot.on('error', e => console.log('ERR', e.message));
const ask = async (c, w = 450) => { chat.length = 0; bot.chat(c); await sleep(w); return chat.join(' | '); };
let fails = 0; const check = (n, ok, d = '') => { console.log((ok ? 'PASS ' : 'FAIL ') + n + (d ? '  ' + d : '')); if (!ok) fails++; };
const nums = r => [...r.matchAll(/(-?\d+\.?\d*(?:E-?\d+)?)[df]/g)].map(m => parseFloat(m[1]));
const angDiff = (a, b) => { let d = ((a - b) % 360 + 540) % 360 - 180; return Math.abs(d); };
bot.once('spawn', async () => {
  await sleep(5000); await ask('/op EmberTester'); await ask('/gamemode survival');
  await ask('/fill -14 199 -14 14 199 14 minecraft:stone', 1500); await ask('/tp @s 0 201 0', 1500);
  await ask('/effect give @s minecraft:resistance 999 4 true'); await ask('/effect give @s minecraft:regeneration 999 4 true');
  await ask('/kill @e[type=!player]', 700); await sleep(1500);
  await ask('/execute at @s run emberfall spawnveteran tiki_magma', 1500);
  await ask('/tag @e[type=emberfall:tiki_magma,limit=1] add mine', 300);
  await ask('/execute at @s run tp @e[tag=mine,limit=1] 8 200 0', 500);
  const diffs = [];
  await ask('/attribute @e[tag=mine,limit=1] minecraft:movement_speed base set 0', 300);     // hold the Tiki still; the player moves
  await ask('/execute at @s run tp @e[tag=mine,limit=1] 0 200 0', 500);
  const spots = [[8, 0], [0, 8], [-8, 0], [0, -8], [6, 6], [-6, 6], [-6, -6], [6, -6]];
  for (let round = 0; round < 2; round++) for (const [dx, dz] of spots) {
    await ask(`/tp @s ${dx + 0.5} 201 ${dz + 0.5}`, 300); await sleep(1600);        // give the mob time to turn
    const me = bot.entity.position;
    const tp = nums(await ask('/data get entity @e[tag=mine,limit=1] Pos', 350));
    const hr = nums(await ask('/data get entity @e[type=minecraft:item_display,limit=1,sort=nearest,x=0,y=200,z=0,distance=..30] Rotation', 350));
    if (tp.length >= 3 && hr.length >= 1) {
      const bearing = Math.atan2(-(me.x - tp[0]), me.z - tp[2]) * 180 / Math.PI;
      const d = angDiff(hr[0], bearing);
      console.log(`   player at (${dx},${dz}): head yaw ${hr[0].toFixed(0)} bearing ${bearing.toFixed(0)} off ${d.toFixed(0)}`);
      diffs.push({ d, dist: Math.hypot(me.x - tp[0], me.z - tp[2]) });
    }
  }
  const ds = diffs.map(x => x.d).sort((a, b) => a - b);
  const median = ds.length ? ds[Math.floor(ds.length / 2)] : NaN;
  console.log('   samples', diffs.length, 'median angle off', median.toFixed(0), 'deg; max', ds.length ? ds[ds.length - 1].toFixed(0) : 'n/a', '; distances', diffs.map(x => x.dist.toFixed(0)).join(','));
  check('F1 heads face the player from every side (median within 25 deg, worst within 45)', diffs.length >= 12 && median <= 25 && ds[ds.length - 1] <= 45, `median ${median.toFixed(0)} deg over ${diffs.length} samples`);
  console.log(fails === 0 ? 'ALL PASS' : 'SOME FAIL ' + fails);
  bot.quit(); setTimeout(() => process.exit(0), 300);
});
