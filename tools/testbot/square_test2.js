// Per-window check: growBloat rewrites Scale once a second, so sample Scale+Pos then collect packets for that window only.
const mineflayer = require('mineflayer');
const bot = mineflayer.createBot({ host: '127.0.0.1', port: 25565, username: 'EmberTester', version: '1.21.11', auth: 'offline' });
const sleep = ms => new Promise(r => setTimeout(r, ms));
let fails = 0;
const check = (label, ok, extra = '') => { console.log((ok ? 'PASS ' : 'FAIL ') + label + (extra ? '  ' + extra : '')); if (!ok) fails++; };
const chat = []; bot.on('message', m => chat.push(m.toString()));
bot.on('error', e => console.log('ERROR', e));
let win = null;
bot._client.on('packet', (d, m) => { if (win && m.name === 'world_particles' && d.particle && d.particle.type === 'dust') win.pk.push(d); });
const read = async (cmd, re) => { chat.length = 0; bot.chat(cmd); await sleep(450); const m = chat.join(' ').match(re); return m ? +m[1] : null; };
bot.once('spawn', async () => {
  await sleep(6000);
  const c = async (x, w = 800) => { bot.chat(x); await sleep(w); };
  await c('/gamemode survival', 700);
  await c('/effect give @s minecraft:resistance 900 4 true', 400);
  await c('/effect give @s minecraft:regeneration 900 4 true', 400);
  await c('/tp @s 60 72 40.5', 2500);
  await c('/kill @e[type=!player,distance=..80]', 800);
  await c('/summon emberfall:plague_colossus 40.5 72 40.5 {Tags:["pc"]}', 1200);
  await c('/attribute @e[tag=pc,limit=1] minecraft:movement_speed base set 0', 600);
  await c('/data merge entity @e[tag=pc,limit=1] {Health:4f}', 900);
  const results = [];
  for (let i = 0; i < 8; i++) {
    const px = await read('/data get entity @e[tag=pc,limit=1] Pos[0]', /: ([\d.\-]+)d/);
    const pz = await read('/data get entity @e[tag=pc,limit=1] Pos[2]', /: ([\d.\-]+)d/);
    const scale = await read('/attribute @e[tag=pc,limit=1] minecraft:scale get', /is ([\d.]+)/);
    win = { pk: [] }; await sleep(450); const w = win; win = null;      // one 8-tick draw lands inside this window
    if (px == null || pz == null || scale == null || w.pk.length === 0) continue;
    const frac = Math.min(1, (scale - 1.3) / 0.7);
    const reach = 0.6 * scale / 2 + 3.5 * (1 + frac);
    const cheb = w.pk.map(d => Math.max(Math.abs(d.x - px), Math.abs(d.z - pz)));
    results.push({ scale, reach, n: w.pk.length, err: Math.max(...cheb.map(v => Math.abs(v - reach))) });
  }
  console.log('per-window results:'); for (const r of results) console.log(`  scale ${r.scale} reach ${r.reach.toFixed(3)} packets ${r.n} max error ${r.err.toFixed(3)}`);
  check('sampled at least 3 windows', results.length >= 3, 'n=' + results.length);
  const worst = Math.max(...results.map(r => r.err));
  // Scale changes at most 0.0117/s, which moves reach by ~0.02 in a second, so a correct outline is within ~0.1 of the reading.
  check('every window matches its own reach within 0.1 blocks', worst <= 0.1, 'worst error ' + worst.toFixed(3));
  await c('/kill @e[tag=pc]', 500);
  console.log(fails ? `RESULT: ${fails} FAILED` : 'RESULT: ALL PASSED');
  bot.quit(); process.exit(fails ? 1 : 0);
});
