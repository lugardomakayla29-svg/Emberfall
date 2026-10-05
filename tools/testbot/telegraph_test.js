const mineflayer = require('mineflayer');
const bot = mineflayer.createBot({ host: '127.0.0.1', port: 25565, username: 'EmberTester', version: '1.21.11', auth: 'offline' });
const sleep = ms => new Promise(r => setTimeout(r, ms));
let fails = 0;
const check = (label, ok, extra = '') => { console.log((ok ? 'PASS ' : 'FAIL ') + label + (extra ? '  ' + extra : '')); if (!ok) fails++; };
const dust = []; let capture = false;
bot._client.on('packet', (d, m) => { if (capture && m.name === 'world_particles' && d.particle && d.particle.type === 'dust') dust.push(d); });
bot.on('error', e => console.log('ERROR', e));
const c = async (x, w = 800) => { bot.chat(x); await sleep(w); };
// The Sentinel stands at SX,SZ; radii are measured from there.
const SX = 40.5, SY = 72, SZ = 40.5;
bot.once('spawn', async () => {
  await sleep(6000);
  await c('/gamemode survival', 700);
  await c('/effect give @s minecraft:resistance 900 4 true', 400);
  await c('/effect give @s minecraft:regeneration 900 4 true', 400);
  await c(`/tp @s ${SX + 3} ${SY} ${SZ}`, 2500);
  await c('/kill @e[type=!player,distance=..60]', 800);
  await c(`/summon emberfall:corrupted_sentinel ${SX} ${SY} ${SZ} {Tags:["fxs"]}`, 1200);
  await c('/attribute @e[tag=fxs,limit=1] minecraft:movement_speed base set 0', 600);
  await c('/attribute @e[tag=fxs,limit=1] minecraft:knockback_resistance base set 1', 600);
  // The stomp fires when a target is within STOMP_RANGE (10) and its cooldown allows; sample particles for a while.
  const chat = []; bot.on('message', m => chat.push(m.toString()));
  const ys = [], xz = [];
  capture = true;
  for (let i = 0; i < 14; i++) {
    chat.length = 0; bot.chat('/data get entity @e[tag=fxs,limit=1] Pos'); await sleep(650);
    const m = chat.join(' ').match(/\[([\d.\-]+)d, ([\d.\-]+)d, ([\d.\-]+)d\]/);
    if (m) { ys.push(parseFloat(m[2])); xz.push([parseFloat(m[1]), parseFloat(m[3])]); }
  }
  capture = false;
  console.log('mob true Y samples during capture:', JSON.stringify([...new Set(ys)]));
  await c('/kill @e[type=!player,distance=..60]', 500);
  console.log('dust packets captured:', dust.length);
  const by = {};
  for (const d of dust) { const k = d.y.toFixed(2); by[k] = (by[k] || 0) + 1; }
  console.log('dust count by y:', JSON.stringify(by));
  check('the Sentinel produced dust telegraph particles', dust.length > 0, 'n=' + dust.length);
  if (dust.length) {
    const forced = dust.filter(d => d.alwaysShow && d.longDistance).length;
    check('every telegraph particle is forced past the client limiter (alwaysShow + longDistance)', forced === dust.length, `${forced}/${dust.length}`);
    // Radius of each particle from the Sentinel. Ring points sit exactly on a circle around it.
    // Measure from where the mob REALLY stood: take the centre that makes the most particles land on one radius, among
    // the sampled true positions (a leaping Sentinel drifts, so the spawn point is not its centre).
    const centres = xz.length ? xz : [[SX, SZ]];
    const score = (cx, cz) => dust.filter(d => Math.abs(Math.hypot(d.x - cx, d.z - cz) - 8.0) < 0.05).length;
    const best = centres.reduce((a, b) => (score(b[0], b[1]) > score(a[0], a[1]) ? b : a));
    console.log('true centres sampled:', JSON.stringify(centres.slice(0, 4)), 'best:', JSON.stringify(best));
    const r = dust.map(d => Math.hypot(d.x - best[0], d.z - best[1]));
    const at = (want) => r.filter(v => Math.abs(v - want) < 0.05).length;
    check('some particles sit on the true crash radius (8.0)', at(8.0) >= 16, 'on 8.0: ' + at(8.0));
    const beyond = r.filter(v => v > 8.05).length;
    check('no telegraph particle is drawn beyond the true crash radius', beyond === 0, 'beyond: ' + beyond);
    // The ring is drawn at the mob's own feet + 0.1. The mob may be mid-leap, so compare each packet's y
    // with the set of true server Y values sampled during capture rather than an assumed ground height.
    const feet = [...new Set(ys)];
    const ringY = [...new Set(dust.map(d => +d.y.toFixed(2)))];
    check('each ring height equals a sampled mob feet Y + 0.1', ringY.every(y => feet.some(f => Math.abs(y - (f + 0.1)) < 0.02)),
      'ring y=' + JSON.stringify(ringY) + ' feet=' + JSON.stringify(feet.slice(0, 2)));
  }
  console.log(fails ? `RESULT: ${fails} FAILED` : 'RESULT: ALL PASSED');
  bot.quit(); process.exit(fails ? 1 : 0);
});
