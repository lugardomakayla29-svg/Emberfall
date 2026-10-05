const mineflayer = require('mineflayer');
const bot = mineflayer.createBot({ host: '127.0.0.1', port: 25565, username: 'EmberTester', version: '1.21.11', auth: 'offline' });
const sleep = ms => new Promise(r => setTimeout(r, ms));
const lines = []; bot.on('message', m => lines.push(m.toString()));
const ask = async (x, w = 900) => { const n = lines.length; bot.chat(x); await sleep(w); return lines.slice(n).join(' | '); };
const R = (n, ok, extra = '') => console.log(`${ok ? 'PASS' : 'FAIL'} ${n} ${extra}`);
// decode the raw run_hud custom payload exactly as RunHudPayload.STREAM_CODEC does
function varint(b, o) { let v = 0, s = 0, x; do { x = b[o.i++]; v |= (x & 0x7f) << s; s += 7; } while (x & 0x80); return v; }
function varlong(b, o) { let v = 0n, s = 0n, x; do { x = b[o.i++]; v |= BigInt(x & 0x7f) << s; s += 7n; } while (x & 0x80); return Number(v); }
const pk = [];
bot._client.on('packet', (d, meta) => {
  if (meta.name !== 'custom_payload') return;
  const ch = d.channel || (d.payload && d.payload.channel);
  if (ch !== 'emberfall:run_hud') return;
  const buf = Buffer.isBuffer(d.data) ? d.data : Buffer.from((d.payload && d.payload.data) || d.data || []);
  const o = { i: 0 };
  const active = buf[o.i++] === 1;
  const p = { active, elapsed: varint(buf, o), level: varint(buf, o), xp: varint(buf, o), gold: varint(buf, o), silver: varlong(buf, o), kills: varint(buf, o), price: varint(buf, o), relics: [], len: buf.length, used: 0 };
  const pairs = varint(buf, o); for (let k = 0; k < pairs * 2; k++) p.relics.push(varint(buf, o));
  p.used = o.i; p.t = Date.now(); pk.push(p);
});
const last = () => pk[pk.length - 1];
bot.once('spawn', async () => {
  await sleep(4000);
  await ask('/gamemode survival'); await ask('/effect clear @s'); await ask('/effect give @s minecraft:resistance 999 4 true'); await ask('/effect give @s minecraft:regeneration 999 4 true');   // without these the wave kills the bot at ~27s and R8/R9 test a run that no longer exists
  R('R0 nothing sent before a run', pk.length === 0, `(${pk.length})`);
  await ask('/emberfall debuggold EmberTester', 300);
  await ask('/character select juggernaut'); await ask('/expedition', 4000);
  for (let i = 0; i < 30; i++) { await sleep(1500); if (/expedition/.test(await ask('/data get entity @s Dimension', 400))) break; }
  await sleep(2500);
  R('R1 exactly one packet on entry', pk.length === 1, `(${pk.length})`);
  const a = last();
  R('R2 payload decoded, every byte consumed', a && a.used === a.len, a ? `(${a.used}/${a.len})` : '');
  R('R3 active, level>=0, start elapsed under 5s', a && a.active && a.elapsed < 5, a ? JSON.stringify({ e: a.elapsed, l: a.level, g: a.gold, s: a.silver, k: a.kills }) : '');
  R('R2b the packet carries the next chest price (30 at run start)', a && a.price === 30, a ? `(price ${a.price})` : '');
  const n1 = pk.length; await sleep(8000);
  R('R4 no packets over 8s idle (clock is client side)', pk.length === n1, `(${pk.length - n1} extra)`);
  await ask('/emberfall debugpickup EmberTester gold 7', 500); await sleep(2500);
  R('R5 a gold change sends a fresh packet with the new gold', last().gold >= 7 && pk.length > n1, `(gold ${last().gold}, +${pk.length - n1})`);
  const n2 = pk.length;
  await ask('/emberfall debugpickup EmberTester xp 30', 500); await sleep(2500);
  R('R6 an XP change sends a packet', pk.length > n2 && (last().xp > 0 || last().level > 0), `(lvl ${last().level} xp ${last().xp}%)`);
  // The live run earns stray kills between a packet and the read, so the server view can be one step ahead of the last packet received.
  // Retry: read the server view, let the packet arrive, and pass as soon as the two agree on the same sample.
  let srv = '', agree = false, tries = 0;
  for (; tries < 6 && !agree; tries++) {
    srv = await ask('/emberfall runhud EmberTester', 500); await sleep(1400);
    const m = /RUNHUD (\d+)\|(\d+)\|(\d+)\|(\d+)\|(\d+)\|(\d+)/.exec(srv);
    const L = last();
    agree = !!m && +m[2] === L.level && +m[3] === L.xp && +m[4] === L.gold && +m[5] === L.silver && +m[6] === L.kills;
  }
  R('R7 server view matches the last packet', agree, `(${(/RUNHUD [^ ]+/.exec(srv) || [''])[0]}, after ${tries} read${tries === 1 ? '' : 's'})`);
  console.log('     waiting ~32s for the drift-correction resync ...');
  const n3 = pk.length; const t0 = last().t; await sleep(33000);
  const re = pk.slice(n3);
  console.log('     packets during the wait:', re.map(p => `(+${((p.t - t0) / 1000).toFixed(1)}s e${p.elapsed} L${p.level} xp${p.xp} g${p.gold} s${p.silver} k${p.kills})`).join(' '), '| baseline elapsed', last() && pk[n3 - 1].elapsed);
  // The guarantee is that the client clock is corrected at least every 30s. Any packet (a change or a pure resync) carries the elapsed time and
  // resets the timer, so a stray kill mid-wait legitimately postpones the resync. Judge the longest silent gap, and that elapsed advanced.
  const seq = [pk[n3 - 1], ...re];
  let maxGap = 0; for (let i = 1; i < seq.length; i++) maxGap = Math.max(maxGap, (seq[i].t - seq[i - 1].t) / 1000);
  maxGap = Math.max(maxGap, (t0 + 33000 - seq[seq.length - 1].t) / 1000);   // silence from the last packet to the end of the wait
  const lastP = seq[seq.length - 1];
  R('R8 the clock is corrected at least every ~30s (longest silent gap <= 31.5s and at least one packet carried a later elapsed)', maxGap <= 31.5 && re.length >= 1 && lastP.elapsed > seq[0].elapsed + 5, `(longest gap ${maxGap.toFixed(1)}s, ${re.length} packets, elapsed ${seq[0].elapsed} -> ${lastP.elapsed})`);
  const before = pk.length;
  await ask('/expedition leave', 1500);
  R('R9 leaving sends the HIDDEN packet (inactive) as the final packet', pk.length >= before + 1 && last().active === false, `(+${pk.length - before} packets, final active=${last().active})`);
  const after = pk.length; await sleep(4000);
  R('R10 nothing sent after leaving', pk.length === after);
  R('R11 server view is none after leaving', /RUNHUD none/.test(await ask('/emberfall runhud EmberTester', 700)));
  bot.quit(); setTimeout(() => process.exit(0), 400);
});
bot.on('error', e => console.log('ERR', e.message));
