const mineflayer = require('mineflayer');
const bot = mineflayer.createBot({ host: '127.0.0.1', port: 25565, username: 'EmberTester', version: '1.21.11', auth: 'offline' });
const sleep = ms => new Promise(r => setTimeout(r, ms));
let fails = 0;
const check = (label, ok, extra = '') => { console.log((ok ? 'PASS ' : 'FAIL ') + label + (extra ? '  ' + extra : '')); if (!ok) fails++; };
const chat = []; bot.on('message', m => chat.push(m.toString()));
bot.on('error', e => console.log('ERROR', e));
const dust = []; let on = false;
bot._client.on('packet', (d, m) => { if (on && m.name === 'world_particles' && d.particle && d.particle.type === 'dust') dust.push(d); });
const SX = 40.5, SZ = 40.5;
const read = async (cmd, re) => { chat.length = 0; bot.chat(cmd); await sleep(900); const m = chat.join(' ').match(re); return m ? m[1] : null; };
bot.once('spawn', async () => {
  await sleep(6000);
  const c = async (x, w = 800) => { bot.chat(x); await sleep(w); };
  await c('/gamemode survival', 700);
  await c('/effect give @s minecraft:resistance 900 4 true', 400);
  await c('/effect give @s minecraft:regeneration 900 4 true', 400);
  await c('/tp @s 60 72 40.5', 2500);            // far away: the instant burst (range 4) must not fire
  await c('/kill @e[type=!player,distance=..80]', 800);
  await c(`/summon emberfall:plague_colossus ${SX} 72 ${SZ} {Tags:["pc"]}`, 1200);
  await c('/attribute @e[tag=pc,limit=1] minecraft:movement_speed base set 0', 600);
  await c('/attribute @e[tag=pc,limit=1] minecraft:scale base set 2.0', 600);   // MAX_SCALE: growth stops, so reach is constant. bloat=1.0 => radius 7.0

  // 1. healthy: no outline
  on = true; await sleep(3000); on = false;
  check('no hazard outline while the Colossus is healthy', dust.length === 0, 'dust=' + dust.length);
  dust.length = 0;

  // 2. nearly dead: outline appears
  await c('/data merge entity @e[tag=pc,limit=1] {Health:4f}', 900);
  const hp = await read('/data get entity @e[tag=pc,limit=1] Health', /: ([\d.]+)f/);
  console.log('Colossus health now:', hp);
  on = true; await sleep(4000); on = false;
  check('a hazard outline appears once health is under 25%', dust.length > 0, 'dust=' + dust.length);
  if (dust.length) {
    const wire = dust.filter(d => d.alwaysShow && d.longDistance).length;
    check('outline particles are forced past the client limiter', wire === dust.length, wire + '/' + dust.length);
    // Expected reach, computed from the mob's OWN state exactly as the code does (growBloat rewrites Scale every
    // second, so a scale set from outside is overwritten). scale -> bloat fraction -> radius, plus half body width.
    const px = +(await read('/data get entity @e[tag=pc,limit=1] Pos[0]', /: ([\d.\-]+)d/));
    const pz = +(await read('/data get entity @e[tag=pc,limit=1] Pos[2]', /: ([\d.\-]+)d/));
    const scale = +(await read('/attribute @e[tag=pc,limit=1] minecraft:scale get', /is ([\d.]+)/));
    const START = 1.3, MAX = 2.0;
    const frac = (scale - START) / (MAX - START);
    const half = 0.6 * scale / 2;
    const reach = half + 3.5 * (1.0 + frac);
    console.log('scale', scale, 'bloat', frac.toFixed(3), 'expected reach', reach.toFixed(3));
    const cheb = dust.map(d => Math.max(Math.abs(d.x - px), Math.abs(d.z - pz)));
    const distinct = [...new Set(cheb.map(v => +v.toFixed(2)))].sort((a, b) => a - b);
    console.log('distinct Chebyshev distances of the packets:', JSON.stringify(distinct));
    const onEdge = cheb.filter(v => Math.abs(v - reach) < 0.3).length;
    check('every outline point lies on the square edge at reach ' + reach.toFixed(2), onEdge === dust.length, `${onEdge}/${dust.length} (server pos ${px.toFixed(2)},${pz.toFixed(2)})`);
    const inside = cheb.filter(v => v < reach - 0.3).length, outside = cheb.filter(v => v > reach + 0.3).length;
    check('no point inside or outside the edge', inside === 0 && outside === 0, `inside=${inside} outside=${outside}`);
    // it is a SQUARE, so the four corners must be present: points with both |dx| and |dz| == reach
    const corners = dust.filter(d => Math.abs(Math.abs(d.x - px) - reach) < 0.3 && Math.abs(Math.abs(d.z - pz) - reach) < 0.3);
    const quadrants = new Set(corners.map(d => Math.sign(d.x - px) + ',' + Math.sign(d.z - pz)));
    check('all four corners of the square are drawn', quadrants.size === 4, 'corner quadrants: ' + [...quadrants].join(' | '));
  }
  await c('/kill @e[tag=pc]', 500);
  console.log(fails ? `RESULT: ${fails} FAILED` : 'RESULT: ALL PASSED');
  bot.quit(); process.exit(fails ? 1 : 0);
});
