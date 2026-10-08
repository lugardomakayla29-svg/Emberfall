// Blood: colour follows the body, and the cost per landed hit stays small.
const mineflayer = require('mineflayer');
const bot = mineflayer.createBot({ host: '127.0.0.1', port: 25565, username: 'EmberTester', version: '1.21.11', auth: 'offline' });
const sleep = ms => new Promise(r => setTimeout(r, ms));
const lines = []; bot.on('message', m => lines.push(m.toString()));
const ask = async (x, w = 600) => { const n = lines.length; bot.chat(x); await sleep(w); return lines.slice(n).join(' | '); };
const R = (n, ok, extra = '') => console.log(`${ok ? 'PASS' : 'FAIL'} ${n} ${extra}`);
// The colours Blood.java uses (main, dark), as 0xRRGGBB. Packets may carry them with or without alpha, so compare the low 24 bits.
const PALETTE = { RED: [0xB01010, 0x780A0A], GREEN: [0x6FD21E, 0x3F8A10], MAGMA: [0xFF7A1A, 0xC04A0A], BONE: [0xD8D2BC, 0xA8A38E] };
let rec = false, tally = {};
const colourName = c => { const v = c & 0xFFFFFF; for (const [k, arr] of Object.entries(PALETTE)) if (arr.includes(v)) return k; return null; };
bot._client.on('packet', (d, m) => {
  if (!rec || m.name !== 'world_particles' || !d.particle) return;
  if (String(d.particle.type) !== 'dust') return;
  const col = d.particle.data && (d.particle.data.color !== undefined ? d.particle.data.color : d.particle.data);
  const k = colourName(typeof col === 'number' ? col : 0);
  if (k) tally[k] = (tally[k] || 0) + (d.numberOfParticles || d.count || 1);
});
bot.on('error', e => console.log('ERROR', e));
bot.once('spawn', async () => {
  await sleep(4000);
  await ask('/gamemode survival'); await ask('/effect clear @s');
  await ask('/character select juggernaut', 500); await ask('/expedition leave', 800); await ask('/expedition', 3500);
  // The map builds async (about 32 s). Poll the dimension so the checks below run in the RUN, not in the hub, and assert it (R0).
  let inRun = false; for (let i = 0; i < 80 && !inRun; i++) { await sleep(1500); inRun = /expedition/.test(await ask('/data get entity @s Dimension', 400)); }
  R('R0 the bot is inside a run (nothing below means anything in the hub)', inRun, `inRun=${inRun}`);
  await ask('/effect give @s minecraft:resistance 999 4 true'); await ask('/effect give @s minecraft:regeneration 999 4 true');
  await ask('/emberfall wavestop 0', 300);
  await ask('/kill @e[type=!player,distance=..90]', 900);
  const pin = setInterval(() => bot.chat('/tp @s ' + bot.entity.position.x.toFixed(2) + ' ' + bot.entity.position.y.toFixed(2) + ' ' + bot.entity.position.z.toFixed(2)), 1500);
  const cases = [
    ['RED', 'emberfall:horde_zombie'],
    ['GREEN', 'emberfall:horde_spider'],
    ['BONE', 'emberfall:horde_skeleton'],
  ];
  const got = {};
  for (const [want, type] of cases) {
    await ask('/kill @e[type=!player,distance=..90]', 700); await sleep(1500);
    await ask(`/execute at @s run summon ${type} ~2.2 ~ ~ {Tags:["bt"],PersistenceRequired:1b,Attributes:[{id:"minecraft:max_health",base:3000},{id:"minecraft:movement_speed",base:0.0}]}`, 500);
    await ask('/effect give @e[tag=bt,limit=1] minecraft:instant_health 1 10 true', 300);
    tally = {}; rec = true;
    const hold = setInterval(() => bot.chat('/execute at @s run tp @e[tag=bt,limit=1] ~2.2 ~ ~'), 700);
    await sleep(9000);
    rec = false; clearInterval(hold);
    got[want] = { ...tally };
    console.log(`     ${type}: blood dust by palette ->`, JSON.stringify(tally));
  }
  clearInterval(pin);
  for (const [want] of cases) {
    const t = got[want], own = t[want] || 0, others = Object.entries(t).filter(([k]) => k !== want).reduce((a, [, v]) => a + v, 0);
    R(`B_${want} a ${want} mob bleeds ${want}`, own > 0 && own > others * 3, `(${own} own vs ${others} other)`);
  }
  await ask('/kill @e[tag=bt]', 300); await ask('/expedition leave', 800);
  bot.quit(); process.exit(0);
});
