// HUD level and ultimate meter. Captures the raw hud_state payloads and decodes them with the SAME layout as HudStatePayload.STREAM_CODEC
// (weapon = id, name, level, meterStep). Juggernaut holds the War Halberd. No foes, so nothing moves the meter but the test.
//   W1  every payload decodes with every byte consumed
//   W2  fresh: the halberd reads level 1, step 0
//   W3  a half meter (500 of 1000) reads step 10, and exactly ONE new payload was sent for it
//   W4  6 s idle sends nothing more
//   W5  granting kills to level 10 reads level 10
//   W6  999 of 1000 reads step 19 (one short of ready is not a full bar)
//   W7  1000 of 1000 reads step 20
const mineflayer = require('mineflayer');
const bot = mineflayer.createBot({ host: '127.0.0.1', port: 25565, username: 'EmberTester', version: '1.21.11', auth: 'offline' });
const sleep = ms => new Promise(r => setTimeout(r, ms));
const lines = []; bot.on('message', m => lines.push(m.toString())); bot.on('error', e => console.log('ERR', e.message));
const ask = async (x, w = 500) => { const n = lines.length; bot.chat(x); await sleep(w); return lines.slice(n).join(' | '); };
let fails = 0; const R = (n, ok, extra = '') => { console.log(`${ok ? 'PASS' : 'FAIL'} ${n} ${extra}`); if (!ok) fails++; };
const payloads = [];   // decoded hud_state payloads, in order
function readVarInt(b, o) { let n = 0, s = 0, x; do { x = b[o.i++]; n |= (x & 127) << s; s += 7; } while (x & 128); return n; }
function readUtf(b, o) { const n = readVarInt(b, o); const t = b.toString('utf8', o.i, o.i + n); o.i += n; return t; }
function decode(b) {
  const o = { i: 0 };
  const ws = readVarInt(b, o), ts = readVarInt(b, o), wn = readVarInt(b, o);
  const weapons = []; for (let i = 0; i < wn; i++) weapons.push({ id: readUtf(b, o), name: readUtf(b, o), level: readVarInt(b, o), step: readVarInt(b, o) });
  const tn = readVarInt(b, o);
  const tomes = []; for (let i = 0; i < tn; i++) tomes.push({ id: readUtf(b, o), name: readUtf(b, o), stacks: readVarInt(b, o), max: readVarInt(b, o) });
  return { ws, ts, weapons, tomes, exact: o.i === b.length, len: b.length };
}
bot._client.on('packet', (d, meta) => {
  if (meta.name !== 'custom_payload') return;
  const ch = String(d.channel || (d.payload && d.payload.channel) || '');
  if (!ch.includes('hud_state')) return;
  const buf = d.data || d.payload || d;
  if (Buffer.isBuffer(buf)) payloads.push(decode(buf));
});
const last = () => payloads[payloads.length - 1];
const g = async (kills, m) => ask(`/emberfall debugweapongrowth EmberTester grant 0 ${kills} ${m}`, 400);
bot.once('spawn', async () => {
  await sleep(5000);
  await ask('/gamemode survival'); await ask('/effect clear @s');
  await ask('/expedition leave', 800);
  await ask('/character select juggernaut', 700); await ask('/expedition', 2500);
  for (let i = 0; i < 40; i++) { await sleep(2000); if (/expedition/.test(await ask('/data get entity @s Dimension', 400))) break; }
  await sleep(2000);
  await ask('/effect give @s minecraft:resistance 999 4 true', 200); await ask('/effect give @s minecraft:regeneration 999 4 true', 200);
  await ask('/emberfall wavestop 0', 300); await ask('/time set midnight', 200);
  await ask('/kill @e[type=!player,type=!minecraft:item_display,type=!minecraft:interaction]', 900);
  await sleep(2500);                                   // let the once-a-second sync deliver the settled state

  R('W1 every payload decodes with every byte consumed', payloads.length > 0 && payloads.every(p => p.exact), `${payloads.length} payloads, sizes ${payloads.map(p => p.len).join(',')}`);
  const p0 = last();
  R('W2 fresh: the halberd reads level 1, step 0', p0 && p0.weapons.length === 1 && p0.weapons[0].id === 'war_halberd' && p0.weapons[0].level === 1 && p0.weapons[0].step === 0, JSON.stringify(p0 && p0.weapons));

  const n0 = payloads.length;
  await g(0, 500); await sleep(2600);
  const p1 = last();
  R('W3 a half meter reads step 10, and exactly one new payload was sent for it', p1.weapons[0].step === 10 && payloads.length === n0 + 1, `step ${p1.weapons[0].step}, payloads ${n0} -> ${payloads.length}`);

  const n1 = payloads.length; await sleep(6000);
  R('W4 6 s idle sends nothing more', payloads.length === n1, `payloads ${n1} -> ${payloads.length}`);

  await g(108, 0); await sleep(2600);
  const p2 = last();
  R('W5 granting kills to level 10 reads level 10', p2.weapons[0].level === 10, JSON.stringify(p2.weapons));

  await g(0, 999); await sleep(2600);
  const p3 = last();
  R('W6 999 of 1000 reads step 19: one short of ready must not promise a full bar', p3.weapons[0].step === 19, JSON.stringify(p3.weapons));
  await g(0, 1000); await sleep(2600);                 // the grant SETS the meter (kills are added, the meter is set), so ask for exactly 1000
  const p4 = last();
  R('W7 1000 of 1000 reads step 20 (no foe is present, so nothing can fire the ultimate)', p4.weapons[0].step === 20, JSON.stringify(p4.weapons));
  console.log('all payload weapon rows:', payloads.map(p => p.weapons.map(w => `${w.level}:${w.step}`).join('+')).join(' '));

  await ask('/expedition leave', 800);
  console.log(fails === 0 ? 'ALL PASS' : 'SOME FAIL ' + fails);
  bot.quit(); setTimeout(() => process.exit(0), 400);
});
