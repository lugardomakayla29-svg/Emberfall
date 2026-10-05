// Captures the raw hud_state payload and decodes it with the SAME layout as HudStatePayload.STREAM_CODEC.
const mineflayer = require('mineflayer');
const bot = mineflayer.createBot({ host: '127.0.0.1', port: 25565, username: 'EmberTester', version: '1.21.11', auth: 'offline' });
const sleep = ms => new Promise(r => setTimeout(r, ms));
const seen = [];
bot._client.on('packet', (d, meta) => {
  if (meta.name !== 'custom_payload') return;
  const ch = d.channel || (d.payload && d.payload.channel) || '';
  seen.push(String(ch));
  if (!String(ch).includes('hud_state')) return;
  const buf = d.data || d.payload || d;
  console.log('RAW hud_state', Buffer.isBuffer(buf) ? buf.length + ' bytes' : JSON.stringify(buf).slice(0, 200));
  if (Buffer.isBuffer(buf)) decode(buf);
});
function readVarInt(b, o) { let n = 0, s = 0, x; do { x = b[o.i++]; n |= (x & 127) << s; s += 7; } while (x & 128); return n; }
function readUtf(b, o) { const n = readVarInt(b, o); const t = b.toString('utf8', o.i, o.i + n); o.i += n; return t; }
function decode(b) {
  const o = { i: 0 };
  const ws = readVarInt(b, o), ts = readVarInt(b, o), wn = readVarInt(b, o);
  const w = []; for (let i = 0; i < wn; i++) w.push(readUtf(b, o) + '=' + readUtf(b, o) + ' Lv' + readVarInt(b, o) + ' meter' + readVarInt(b, o));   // id, name, level, meterStep
  const tn = readVarInt(b, o);
  const t = []; for (let i = 0; i < tn; i++) t.push(readUtf(b, o) + '=' + readUtf(b, o) + ' ' + readVarInt(b, o) + '/' + readVarInt(b, o));
  console.log('DECODED slots', ws + '/' + ts, 'weapons', w, 'tomes', t, 'consumed', o.i, 'of', b.length, o.i === b.length ? 'EXACT' : 'MISMATCH');
}
bot.once('spawn', async () => {
  await sleep(5000);
  const c = async (x, w = 900) => { bot.chat(x); await sleep(w); };
  await c('/expedition leave', 800); await c('/character select juggernaut', 700); await c('/expedition', 4000);
  await sleep(2500);
  await c('/emberfall granttome EmberTester ember_touch', 1200); await sleep(2500);
  console.log('custom_payload channels seen:', [...new Set(seen)].join(', ').slice(0, 300));
  await c('/expedition leave', 800);
  bot.quit(); setTimeout(() => process.exit(0), 500);
});
