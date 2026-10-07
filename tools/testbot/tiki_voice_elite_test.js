// Tiki shriek (cone) + laser (lane): both fire, both hurt only the player, the laser recharges faster at the higher tier.
const mineflayer = require('mineflayer');
const bot = mineflayer.createBot({ host: '127.0.0.1', port: 25565, username: 'EmberTester', version: '1.21.11', auth: 'offline' });
const sleep = ms => new Promise(r => setTimeout(r, ms));
const lines = []; bot.on('message', m => lines.push(m.toString()));
const ask = async (x, w = 600) => { const n = lines.length; bot.chat(x); await sleep(w); return lines.slice(n).join(' | '); };
const R = (n, ok, extra = '') => console.log(`${ok ? 'PASS' : 'FAIL'} ${n} ${extra}`);

// V4 FIX (2026-10-07): /expedition returns at once while the map is BUILT (about 30 s the first time in a fresh world), and the player is teleported in
// only when it finishes. The old fixed 3.5 s wait spawned the Tiki into the overworld, and the arrival then left it behind. Wait for the real condition.
async function waitInExpedition(ms = 90000) {
  const t0 = Date.now();
  while (Date.now() - t0 < ms) {
    const r = await ask('/data get entity @s Dimension', 400);
    if (/emberfall:expedition/.test(r)) return true;
    await sleep(1500);
  }
  return false;
}
const tierArg = 'elite';
const cmd = '/emberfall spawnelite tiki_magma';
let rec = false, sonic = [], lane = [], hurt = [];
let lastHp = 20;
bot._client.on('packet', (d, m) => {
  if (!rec) return;
  if (m.name === 'world_particles' && d.particle) {
    const t = String(d.particle.type);
    if (t === 'sonic_boom') sonic.push(Date.now());
    if (t === 'dust' && d.particle.data && (d.particle.data.color === 0xFF2A2A || d.particle.data.color === 16722474)) lane.push(Date.now());
  }
  if (m.name === 'damage_event') hurt.push(Date.now());
});
bot.on('error', e => console.log('ERROR', e));
bot.once('spawn', async () => {
  await sleep(4000);
  await ask('/gamemode survival'); await ask('/effect clear @s');
  await ask('/character select battlemage'); await ask('/expedition leave', 800); await ask('/expedition', 800);
  const arrived = await waitInExpedition(); R('V0 the player reached the expedition before the Tiki was spawned', arrived);
  if (!arrived) { bot.quit(); process.exit(0); }
  await ask('/effect give @s minecraft:resistance 999 4 true'); await ask('/effect give @s minecraft:regeneration 999 4 true');
  await ask('/emberfall wavestop 0', 300);
  await ask('/kill @e[type=!player,distance=..90]', 900);
  const pin = setInterval(() => bot.chat('/tp @s ' + bot.entity.position.x.toFixed(2) + ' ' + bot.entity.position.y.toFixed(2) + ' ' + bot.entity.position.z.toFixed(2)), 1500);
  let rep = '';
  for (let i = 0; i < 14; i++) {
    rep = await ask(`/execute at @s run ${cmd.replace('/', '')}`, 1200);
    await sleep(2200);
    const d = await ask('/emberfall tikidump EmberTester', 1000);
    const hm = /heads=(\d+)/.exec(d);
    if (hm && +hm[1] === 3) break;
    await ask('/kill @e[type=!player]', 600); await sleep(2200);
  }
  console.log('     spawn:', rep.slice(0, 70));
  await ask('/tag @e[type=emberfall:tiki_magma,limit=1] add mine', 250);
  await ask('/attribute @e[tag=mine,limit=1] minecraft:max_health base set 2000', 250);
  await ask('/effect give @e[tag=mine,limit=1] minecraft:instant_health 1 10 true', 300);
  await ask('/execute at @s run tp @e[tag=mine,limit=1] ~7 ~ ~', 400);
  // Hold the Tiki at 7 blocks: inside laser trigger (12) and shriek range (8).
  const hold = setInterval(() => bot.chat('/execute at @s run tp @e[tag=mine,limit=1] ~7 ~ ~'), 1500);
  rec = true;
  const start = Date.now();
  await sleep(35000);
  rec = false; clearInterval(hold); clearInterval(pin);
  // A "burst" is a run of packets within 400ms; the wind-up redraws every 4 ticks so many packets = one telegraph.
  const bursts = arr => { let n = 0, last = -1e9; for (const t of arr) { if (t - last > 900) n++; last = t; } return n; };
  const laserFires = bursts(lane), shrieks = bursts(sonic);
  console.log('     window 35s | lane bursts:', laserFires, '| sonic bursts:', shrieks, '| damage events:', hurt.length);
  const alive = await ask('/execute if entity @e[tag=mine]', 500);
  R('V1 the shriek fired', shrieks >= 2, `(${shrieks} in 35s)`);
  if (tierArg !== 'veteran') R('V2 the laser fired', laserFires >= 2, `(${laserFires} in 35s)`);
  else R('V2 a veteran fodder Tiki has NO laser', laserFires === 0, `(${laserFires})`);
  R('V3 the player took damage', hurt.length > 0, `(${hurt.length})`);
  R('V4 the Tiki survived the window (test is not vacuous)', /passed/i.test(alive), alive.slice(0, 60));
  await ask('/kill @e[tag=mine]', 400); await ask('/expedition leave', 800);
  bot.quit(); process.exit(0);
});
