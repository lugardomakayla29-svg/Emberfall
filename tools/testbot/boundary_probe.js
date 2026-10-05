const mineflayer = require('mineflayer');
const bot = mineflayer.createBot({ host: '127.0.0.1', port: 25565, username: 'EmberTester', version: '1.21.11', auth: 'offline' });
const sleep = ms => new Promise(r => setTimeout(r, ms));
const lines = []; bot.on('message', m => lines.push(m.toString()));
const ask = async (x, w = 900) => { const n = lines.length; bot.chat(x); await sleep(w); return lines.slice(n).join(' | '); };
const pkts = [];
bot._client.on('packet', (d, meta) => { if (['entity_velocity', 'position', 'entity_teleport', 'teleport_entity', 'player_position'].includes(meta.name)) pkts.push(meta.name + ' ' + JSON.stringify(d).slice(0, 120)); });
const pos = async () => { const r = await ask('/data get entity EmberTester Pos', 500); const m = /\[([-\d.]+)d, ([-\d.]+)d, ([-\d.]+)d\]/.exec(r); return m ? { x: +m[1], y: +m[2], z: +m[3] } : null; };
bot.once('spawn', async () => {
  await sleep(4000);
  await ask('/tp EmberTester 70.5 72 -1.5', 1500); await ask('/gamemode survival'); await ask('/effect give @s minecraft:resistance 999 4 true'); await sleep(4000);
  await ask('/character select juggernaut'); await ask('/expedition', 3000);
  const r = await ask('/emberfall arenabox EmberTester', 700); const m = /ARENABOX ([-\d.]+),([-\d.]+),([-\d.]+),([-\d.]+)/.exec(r);
  const x1 = +m[3], z0 = +m[2], z1 = +m[4]; const cz = (z0 + z1) / 2; const p0 = await pos();
  await ask(`/tp EmberTester ${x1 + 1.5} ${p0.y + 1} ${cz}`, 100);
  pkts.length = 0; const trace = [];
  for (let i = 0; i < 8; i++) { const p = await pos(); trace.push(p ? +(p.x - x1).toFixed(2) : null); }
  console.log('x minus edge over 8 samples (~0.6s apart):', trace.join(', '));
  console.log('movement packets seen:', pkts.length); pkts.slice(0, 6).forEach(x => console.log('  ', x));
  bot.quit(); setTimeout(() => process.exit(0), 400);
});
bot.on('error', e => console.log('ERR', e.message));
