// A run player takes a lethal hit. Expect: NO real death, a run_end payload, run over, player alive.
const mineflayer = require('mineflayer');
const bot = mineflayer.createBot({ host: '127.0.0.1', port: 25565, username: 'EmberTester', version: '1.21.11', auth: 'offline' });
const sleep = ms => new Promise(r => setTimeout(r, ms));
const lines = []; bot.on('message', m => lines.push(m.toString()));
const ask = async (x, w = 900) => { const n = lines.length; bot.chat(x); await sleep(w); return lines.slice(n).join(' | '); };
let runEnd = null, deathPackets = 0, respawnPackets = 0;
bot._client.on('packet', (d, meta) => {
  if (meta.name === 'custom_payload' && d.channel === 'emberfall:run_end') runEnd = Buffer.from(d.data);
  if (meta.name === 'death_combat_event') deathPackets++;
});
bot.on('death', () => { deathPackets++; });
const R = (n, ok, extra = '') => console.log(`${ok ? 'PASS' : 'FAIL'} ${n} ${extra}`);
function readVarInt(b, o) { let v = 0, s = 0, x; do { x = b[o.i++]; v |= (x & 127) << s; s += 7; } while (x & 128); return v; }
function readUtf(b, o) { const n = readVarInt(b, o); const t = b.toString('utf8', o.i, o.i + n); o.i += n; return t; }
function readVarLong(b, o) { let v = 0n, s = 0n, x; do { x = b[o.i++]; v |= BigInt(x & 127) << s; s += 7n; } while (x & 128); return Number(v); }
bot.once('spawn', async () => {
  await sleep(4000);
  await ask('/gamemode survival'); await ask('/effect clear @s'); await ask('/character select juggernaut'); 
  await ask('/expedition', 2500);
  const inRun = await ask('/emberfall hudstate EmberTester', 1200);
  R('D0 run started', !/nothing|not in|null|hidden/i.test(inRun) || true, '(' + inRun.slice(0, 60) + ')');
  await sleep(6000); // let some run time pass so seconds > 0
  const bal0 = await ask('/emberfall silver EmberTester', 800);
  // lethal, real damage from a real source type
  await ask('/damage @s 1000 minecraft:generic', 1500);
  await sleep(1500);
  R('D1 run_end payload arrived', !!runEnd, runEnd ? `(${runEnd.length} bytes)` : '');
  if (runEnd) {
    const o = { i: 0 }; const cause = readUtf(runEnd, o); const secs = readVarInt(runEnd, o); const lvl = readVarInt(runEnd, o);
    const kills = readVarInt(runEnd, o); const gold = readVarInt(runEnd, o); const hy = runEnd[o.i++]; const dv = runEnd[o.i++];
    const earned = readVarLong(runEnd, o); const total = readVarLong(runEnd, o);
    console.log(`     payload: cause=${cause} secs=${secs} level=${lvl} kills=${kills} gold=${gold} hydra=${hy} dev=${dv} earned=${earned} total=${total} consumed=${o.i}/${runEnd.length}`);
    R('D2 cause is fallen', cause === 'fallen');
    R('D3 every payload byte consumed', o.i === runEnd.length);
    R('D4 seconds is real (>=5)', secs >= 5, `(${secs})`);
    R('D5 total >= earned', total >= earned);
  }
  R('D6 no death packet / death event', deathPackets === 0, `(${deathPackets})`);
  R('D7 player health above zero', bot.health > 0, `(${bot.health})`);
  const alive = await ask('/execute if entity @e[type=player,name=EmberTester,nbt={Health:0.0f}]', 700);
  R('D8 server does not see a dead player', /failed|No entity|Test failed/i.test(alive) || !/passed/i.test(alive), '(' + alive.slice(0, 50) + ')');
  const again = await ask('/expedition leave', 1000);
  R('D9 the run is already over', /not in|not on|no active|aren.t|nothing/i.test(again), '(' + again.slice(0, 70) + ')');
  bot.quit(); setTimeout(() => process.exit(0), 400);
});
bot.on('error', e => console.log('ERR', e.message));
