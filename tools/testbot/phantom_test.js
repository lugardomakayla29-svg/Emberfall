// Phantom Blades at level 10 (Duelist, Twin Daggers): 6 NoAI 1024 hp foes spread 5 to 12 blocks away, outside the dagger swing. Meter filled LAST
// with one trigger foe beside the player. Reads each foe's hp after the ultimate; the server trace PHANTOM_TEST reports cuts and darts.
// Run twice: normally, and with -Demberfall.noPhantom=true as the control (same scene, no ultimate).
const mineflayer = require('mineflayer');
const bot = mineflayer.createBot({ host: '127.0.0.1', port: 25565, username: 'EmberTester', version: '1.21.11', auth: 'offline' });
const sleep = ms => new Promise(r => setTimeout(r, ms));
const lines = []; bot.on('message', m => lines.push(m.toString())); bot.on('error', e => console.log('ERR', e.message));
const ask = async (x, w = 450) => { const n = lines.length; bot.chat(x); await sleep(w); return lines.slice(n).join(' | '); };
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
  await ask('/character select duelist'); await ask('/expedition', 2500);
  for (let i = 0; i < 40; i++) { await sleep(2000); if (/expedition/.test(await ask('/data get entity @s Dimension', 400))) break; }
  await sleep(2000);
  await ask('/effect give @s minecraft:resistance 999 4 true', 200); await ask('/effect give @s minecraft:regeneration 999 4 true', 200);
  await ask('/emberfall wavestop 0', 300); await ask('/time set midnight', 200);
  await ask('/kill @e[type=!player,type=!minecraft:item_display,type=!minecraft:interaction]', 900);
  const bx = bot.entity.position.x, by = bot.entity.position.y, bz = bot.entity.position.z;
  const pin = setInterval(() => bot.chat(`/tp @s ${bx.toFixed(2)} ${by.toFixed(2)} ${bz.toFixed(2)} 0 0`), 700);
  await ask('/emberfall debugloadout EmberTester', 600);
  await g(108, 0);                                                     // level 10, meter EMPTY while the scene is built
  const spots = { a: [0, -5], b: [5, -5], c: [-6, -6], d: [0, -9], e: [7, -8], f: [-3, -12] };
  for (const [t, [dx, dz]] of Object.entries(spots)) await foe(t, dx, dz);
  const tags = Object.keys(spots);
  const before = {}; for (const t of tags) before[t] = await hp(t);
  console.log('BEFORE', JSON.stringify(before));
  await sleep(2500);
  await foe('trig', 0, 1.5);                                            // the foe the daggers actually swing at
  await g(0, 1000);                                                     // meter FULL as the last step: the next landed hit opens the ultimate
  await sleep(9000);                                                    // 5 s ultimate at level 10 plus settle
  const after = {}; for (const t of tags) after[t] = await hp(t);
  console.log('AFTER', JSON.stringify(after));
  const lost = tags.map(t => BIG - after[t]);
  console.log('LOST', JSON.stringify(lost.map(x => +x.toFixed(1))), 'total', lost.reduce((a, b) => a + b, 0).toFixed(1), 'foes hit', lost.filter(x => x > 0.5).length);
  clearInterval(pin); await ask('/expedition leave', 800);
  console.log('PHANTOM_DONE'); bot.quit(); setTimeout(() => process.exit(0), 300);
});
