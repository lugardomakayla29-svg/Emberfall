const mineflayer = require('mineflayer');
const bot = mineflayer.createBot({ host: '127.0.0.1', port: 25565, username: 'EmberTester', version: '1.21.11', auth: 'offline' });
const sleep = ms => new Promise(r => setTimeout(r, ms));
const chat = []; bot.on('message', m => chat.push(m.toString()));
let on = false; const pk = [];
bot._client.on('packet', (d, m) => { if (on && m.name === 'world_particles' && d.particle && d.particle.type === 'dust') pk.push({ t: Date.now(), d }); });
bot.once('spawn', async () => {
  await sleep(6000);
  const c = async (x, w = 700) => { bot.chat(x); await sleep(w); };
  await c('/gamemode survival'); await c('/effect give @s minecraft:resistance 900 4 true', 400); await c('/effect give @s minecraft:regeneration 900 4 true', 400);
  await c('/tp @s 43 72 40.5', 2500);
  await c('/kill @e[type=!player,distance=..60]', 700);
  await c('/summon emberfall:bonecaller_necromancer 40.5 72 40.5 {Tags:["bn"]}', 600);
  await c('/attribute @e[tag=bn,limit=1] minecraft:movement_speed base set 0', 500);
  on = true; await sleep(30000); on = false;
  // group into bursts (packets within 120ms of each other) and describe each burst
  pk.sort((a, b) => a.t - b.t);
  const bursts = []; let cur = null;
  for (const p of pk) { if (!cur || p.t - cur.last > 120) { cur = { first: p.t, last: p.t, n: 0, color: {}, ys: new Set() }; bursts.push(cur); } cur.last = p.t; cur.n++; cur.color[p.d.particle.data.color] = 1; cur.ys.add(p.d.y.toFixed(1)); }
  const sizes = {}; for (const b of bursts) sizes[b.n] = (sizes[b.n] || 0) + 1;
  console.log('dust packets:', pk.length, ' bursts:', bursts.length);
  console.log('burst size histogram (packets per burst : count):', JSON.stringify(sizes));
  const colors = {}; for (const p of pk) { const k = (p.d.particle.data.color >>> 0).toString(16); colors[k] = (colors[k] || 0) + 1; }
  console.log('dust by colour (argb hex):', JSON.stringify(colors));
  await c('/kill @e[tag=bn]', 400);
  bot.quit(); process.exit(0);
});
