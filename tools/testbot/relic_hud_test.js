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
  await ask('/gamemode survival'); await ask('/effect give @s minecraft:resistance 999 4 true'); await ask('/effect give @s minecraft:regeneration 999 4 true');
  await ask('/character select juggernaut'); await ask('/expedition', 4000);
  for (let i = 0; i < 30; i++) { await sleep(1500); if (/expedition/.test(await ask('/data get entity @s Dimension', 400))) break; }
  await sleep(2500);
  // pool order = RelicPool.all() index; read it from the server rather than assume
  const list = await ask('/emberfall relic list', 900);
  const a = last();
  R('H1 payload decodes with every byte consumed', a && a.used === a.len, a ? `(${a.used}/${a.len})` : '');
  R('H2 no relics at run start', a && a.relics.length === 0, JSON.stringify(a && a.relics));
  const n0 = pk.length;
  await ask('/emberfall relic give EmberTester oat_loaf 1', 400); await sleep(2500);
  R('H3 giving a relic sends a fresh packet', pk.length > n0, `(+${pk.length - n0})`);
  const b = last();
  R('H4 it carries exactly one (index, stacks=1) pair', b.relics.length === 2 && b.relics[1] === 1, JSON.stringify(b.relics));
  const idxOat = b.relics[0];
  const n1 = pk.length;
  await ask('/emberfall relic give EmberTester oat_loaf 2', 400); await sleep(2500);
  const c = last();
  R('H5 stacking updates the count to 3 on the same index', pk.length > n1 && c.relics.length === 2 && c.relics[0] === idxOat && c.relics[1] === 3, JSON.stringify(c.relics));
  const n2 = pk.length;
  await ask('/emberfall relic give EmberTester clover 1', 400); await ask('/emberfall relic give EmberTester iron_boots 2', 400); await sleep(2500);
  const d = last();
  const idxs = d.relics.filter((_, k) => k % 2 === 0);
  R('H6 three relics listed in ascending pool order with the right stacks', d.relics.length === 6 && idxs.every((v, k) => k === 0 || v > idxs[k - 1]) && d.relics.filter((_, k) => k % 2 === 1).sort().join() === '1,2,3', JSON.stringify(d.relics));
  const n3 = pk.length; await sleep(8000);
  R('H7 no packets while the relics do not change (8s idle)', pk.length === n3, `(+${pk.length - n3})`);
  await ask('/emberfall relic take EmberTester oat_loaf', 400); await sleep(2500);
  const e = last();
  R('H8 taking one stack lowers the count to 2', e.relics.length === 6 && e.relics[e.relics.indexOf(idxOat) + 1] === 2, JSON.stringify(e.relics));
  const srv = await ask('/emberfall runhud EmberTester', 700);
  R('H9 the server view carries the same relic list', srv.includes(JSON.stringify(e.relics).replace(/,/g, ', ')), srv.slice(0, 120));
  await ask('/expedition leave', 1500); await sleep(1500);
  const f = last();
  R('H10 leaving sends the hidden packet with no relics', f && !f.active && f.relics.length === 0, JSON.stringify(f && f.relics));
  await ask('/expedition', 4000);
  for (let i = 0; i < 30; i++) { await sleep(1500); if (/expedition/.test(await ask('/data get entity @s Dimension', 400))) break; } await sleep(2500);
  R('H11 a second run starts with no relics (no leftover)', last().active && last().relics.length === 0, JSON.stringify(last().relics));
  bot.quit(); setTimeout(() => process.exit(0), 400);
});
