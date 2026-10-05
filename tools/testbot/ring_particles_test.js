// Counts the particles the server sends per tick for the ordinary sickle ring at level 1, 5 and 10 (nobody to fight, no ultimate). Packets are
// counted per server tick with the world_particles event; a full tick (even game time) carries the rim and fill, a half tick only blades and tails.
// Prints mean and max per tick for each level. The budget is 40 a tick (SickleSystem.RING_PARTICLE_BUDGET).
const mineflayer = require('mineflayer');
const bot = mineflayer.createBot({ host: '127.0.0.1', port: 25565, username: 'EmberTester', version: '1.21.11', auth: 'offline' });
const sleep = ms => new Promise(r => setTimeout(r, ms));
const lines = []; bot.on('message', m => lines.push(m.toString())); bot.on('error', e => console.log('ERR', e.message));
const ask = async (x, w = 450) => { const n = lines.length; bot.chat(x); await sleep(w); return lines.slice(n).join(' | '); };
let fails = 0; const R = (n, ok, extra = '') => { console.log(`${ok ? 'PASS' : 'FAIL'} ${n} ${extra}`); if (!ok) fails++; };
const g = async (kills, m) => ask(`/emberfall debugweapongrowth EmberTester grant 0 ${kills} ${m}`, 400);
let count = 0; const perTick = [];
bot._client.on('world_particles', () => { count++; });
bot._client.on('update_time', () => { perTick.push(count); count = 0; });     // one per server second in vanilla; see below
async function sample(ms) { perTick.length = 0; count = 0; const t0 = Date.now(); const start = count; const seen = []; let last = 0;
  const h = setInterval(() => { seen.push(count - last); last = count; }, 50);             // 50 ms = one server tick
  await sleep(ms); clearInterval(h); return seen; }
bot.once('spawn', async () => {
  await sleep(5000);
  await ask('/gamemode survival'); await ask('/effect clear @s');
  await ask('/character select reaper'); await ask('/expedition', 2500);
  for (let i = 0; i < 40; i++) { await sleep(2000); if (/expedition/.test(await ask('/data get entity @s Dimension', 400))) break; }
  await sleep(2000);
  await ask('/effect give @s minecraft:resistance 999 4 true', 200);
  await ask('/emberfall wavestop 0', 300); await ask('/time set midnight', 200);
  await ask('/kill @e[type=!player,type=!minecraft:item_display,type=!minecraft:interaction]', 900);
  await ask('/emberfall debugloadout EmberTester', 600);
  const out = {};
  for (const [lvl, kills] of [[1, 0], [5, 28], [10, 108]]) {
    await g(kills, 0); await sleep(1500);
    const seen = await sample(4000);
    const nz = seen.filter(x => x >= 0);
    const total = nz.reduce((a, b) => a + b, 0);
    out[lvl] = { meanPerTick: +(total / nz.length).toFixed(1), maxBurst: Math.max(...nz), ticks: nz.length };
    console.log('LEVEL', lvl, JSON.stringify(out[lvl]));
  }
  R('P1 level 10 draws clearly more than level 1 (the ring fills in as it grows)', out[10].meanPerTick > out[1].meanPerTick * 1.5, `${out[1].meanPerTick} -> ${out[10].meanPerTick}`);
  R('P2 level 10 stays within the budget on average (40 a tick)', out[10].meanPerTick <= 40, `mean ${out[10].meanPerTick}`);
  await ask('/expedition leave', 800);
  console.log(fails === 0 ? 'ALL PASS' : 'SOME FAIL ' + fails); bot.quit(); setTimeout(() => process.exit(0), 300);
});
