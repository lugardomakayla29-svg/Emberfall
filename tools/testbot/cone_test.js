const mineflayer = require('mineflayer');
const bot = mineflayer.createBot({ host: '127.0.0.1', port: 25565, username: 'EmberTester', version: '1.21.11', auth: 'offline' });
const sleep = ms => new Promise(r => setTimeout(r, ms));
let fails = 0;
const check = (label, ok, extra = '') => { console.log((ok ? 'PASS ' : 'FAIL ') + label + (extra ? '  ' + extra : '')); if (!ok) fails++; };
const chat = []; bot.on('message', m => chat.push(m.toString()));
bot.on('error', e => console.log('ERROR', e));
// each frame: the dust packets that arrived, tagged with the mob's server position/yaw sampled just before
const frames = []; let cur = null;
bot._client.on('packet', (d, m) => { if (cur && m.name === 'world_particles' && d.particle && d.particle.type === 'dust') cur.pk.push(d); });
const num = s => parseFloat(s);
bot.once('spawn', async () => {
  await sleep(6000);
  const c = async (x, w = 800) => { bot.chat(x); await sleep(w); };
  await c('/gamemode survival', 700);
  await c('/effect give @s minecraft:resistance 900 4 true', 400);
  await c('/effect give @s minecraft:regeneration 900 4 true', 400);
  await c('/tp @s 43 72 40.5', 2500);
  await c('/kill @e[type=!player,distance=..60]', 800);
  await c('/summon emberfall:corrupted_sentinel 40.5 72 40.5 {Tags:["fxs"]}', 1200);
  await c('/attribute @e[tag=fxs,limit=1] minecraft:movement_speed base set 0', 600);
  await c('/attribute @e[tag=fxs,limit=1] minecraft:knockback_resistance base set 1', 600);
  // Sample yaw + pos right before each 400ms window of packets. The sentinel faces its target, which is the bot.
  for (let i = 0; i < 24; i++) {
    chat.length = 0; bot.chat('/data get entity @e[tag=fxs,limit=1] Pos'); await sleep(300);
    const p = chat.join(' ').match(/Pos|\[([\d.\-]+)d, ([\d.\-]+)d, ([\d.\-]+)d\]/); const pm = chat.join(' ').match(/\[([\d.\-]+)d, ([\d.\-]+)d, ([\d.\-]+)d\]/);
    chat.length = 0; bot.chat('/data get entity @e[tag=fxs,limit=1] Rotation'); await sleep(300);
    const rm = chat.join(' ').match(/\[([\d.\-]+)f, ([\d.\-]+)f\]/);
    cur = { pos: pm ? [num(pm[1]), num(pm[2]), num(pm[3])] : null, yaw: rm ? num(rm[1]) : null, pk: [] };
    await sleep(500);
    frames.push(cur); cur = null;
  }
  await c('/kill @e[tag=fxs]', 500);
  // A cone frame: many packets NOT all on one circle around the mob. A ring frame has all points at a single radius.
  let cones = 0, coneOk = 0, edgeAngles = [], maxR = 0;
  for (const f of frames) {
    if (!f.pos || f.yaw == null || f.pk.length === 0) continue;
    const rs = f.pk.map(d => Math.hypot(d.x - f.pos[0], d.z - f.pos[2]));
    const spread = Math.max(...rs) - Math.min(...rs);
    if (spread < 1.0) continue; // ring-like, skip
    cones++;
    maxR = Math.max(maxR, ...rs);
    // Minecraft yaw 0 faces +Z; look vector = (-sin(yaw), 0, cos(yaw))
    const yaw = f.yaw * Math.PI / 180, lx = -Math.sin(yaw), lz = Math.cos(yaw);
    const far = f.pk.filter((d, i) => Math.abs(rs[i] - 8.0) < 0.05);
    for (const d of far) {
      const dx = d.x - f.pos[0], dz = d.z - f.pos[2], len = Math.hypot(dx, dz);
      edgeAngles.push(Math.acos(Math.max(-1, Math.min(1, (dx * lx + dz * lz) / len))) * 180 / Math.PI);
    }
    if (far.length >= 7) coneOk++;
  }
  console.log('frames:', frames.length, ' cone-like frames:', cones, ' max radius seen:', maxR.toFixed(2));
  check('the Roar produced cone-shaped telegraphs', cones > 0, 'cones=' + cones);
  check('no cone particle is drawn beyond the roar range (8.0)', maxR <= 8.05, 'max=' + maxR.toFixed(2));
  check('the far arc holds its full 7 points on radius 8.0', coneOk > 0, 'frames with arc: ' + coneOk);
  const maxAng = edgeAngles.length ? Math.max(...edgeAngles) : -1;
  check('no arc point lies outside +-45 degrees of the facing direction', edgeAngles.length > 0 && maxAng <= 45.5, 'max angle=' + maxAng.toFixed(2) + ' n=' + edgeAngles.length);
  const hitsEdge = edgeAngles.filter(a => a > 44.0).length;
  check('the arc reaches the +-45 degree edges (cone is not narrower than the damage)', hitsEdge >= 2, 'points near edge: ' + hitsEdge);
  console.log(fails ? `RESULT: ${fails} FAILED` : 'RESULT: ALL PASSED');
  bot.quit(); process.exit(fails ? 1 : 0);
});
