// Does the soft push run and does it move the player? Sample server-side X fast after crossing the edge by 1.5.
const mineflayer = require('mineflayer');
const bot = mineflayer.createBot({ host: '127.0.0.1', port: 25565, username: 'EmberTester', version: '1.21.11', auth: 'offline' });
const sleep = ms => new Promise(r => setTimeout(r, ms));
const lines = []; bot.on('message', m => lines.push(m.toString()));
let actionbar = [], vel = [];
bot._client.on('packet', (d, meta) => {
  if (meta.name === 'action_bar' || meta.name === 'system_chat') { const s = JSON.stringify(d); if (/ember wall/i.test(s)) actionbar.push(Date.now()); }
  if (meta.name === 'entity_velocity' && d.entityId === bot.entity.id) { if (vel.length < 2) console.log('RAW velocity packet', JSON.stringify(d)); vel.push({ t: Date.now(), x: 0, z: 0 }); }
});
const ask = async (x, w = 300) => { const n = lines.length; bot.chat(x); await sleep(w); return lines.slice(n).join(' | '); };
bot.once('spawn', async () => {
  await sleep(4000);
  await ask('/gamemode survival'); await ask('/effect give @s minecraft:resistance 999 4 true'); await ask('/effect give @s minecraft:regeneration 999 4 true');
  await ask('/tp @s 7.5 70 -0.5', 1500);
  await ask('/character select juggernaut'); await ask('/expedition', 3000);
  const r = await ask('/emberfall arenabox EmberTester', 700);
  const m = /ARENABOX ([-\d.]+),([-\d.]+),([-\d.]+),([-\d.]+)/.exec(r);
  if (!m) { console.log('no arena', r.slice(0, 120)); process.exit(1); }
  const x1 = +m[3], cz = 0;
  console.log('east edge x =', x1);
  await ask('/kill @e[type=!player]', 400);
  actionbar = []; vel = [];
  await ask(`/tp @s ${x1 + 1.5} 70 ${cz}`, 100);
  const t0 = Date.now(); const xs = [];
  for (let i = 0; i < 14; i++) {
    const p = await ask('/data get entity @s Pos', 130); const q = /\[(-?[\d.]+)d/.exec(p);
    if (q) xs.push(((Date.now() - t0) / 1000).toFixed(1) + 's x=' + (+q[1]).toFixed(2));
  }
  console.log(xs.join('  '));
  console.log('"ember wall" action-bar messages received:', actionbar.length);
  console.log('entity_velocity packets to me:', vel.length, vel.slice(0, 3).map(v => `(${v.x},${v.z})`).join(' '));
  await ask('/expedition leave', 800);
  bot.quit(); setTimeout(() => process.exit(0), 300);
});
bot.on('error', e => console.log('ERR', e.message));
