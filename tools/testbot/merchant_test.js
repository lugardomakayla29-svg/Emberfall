// Testificate merchant, server side, on the real paths: arrive, browse (raw open_merchant packet), buy (raw buy packet), leave.
const mineflayer = require('mineflayer');
const sleep = ms => new Promise(r => setTimeout(r, ms));
const bot = mineflayer.createBot({ host: '127.0.0.1', port: 25565, username: 'EmberTester', version: '1.21.11', auth: 'offline' });
const lines = []; bot.on('message', m => lines.push(m.toString())); bot.on('error', e => console.log('ERR', e.message));
const ask = async (x, w = 600) => { const n = lines.length; bot.chat(x); await sleep(w); return lines.slice(n).join(' | '); };
let fails = 0; const check = (n, ok, note = '') => { console.log((ok ? 'PASS ' : 'FAIL ') + n + ' ' + note); if (!ok) fails++; };
// raw packet capture
const stalls = [];
const varint = (b, o) => { let r = 0, s = 0, x; do { x = b[o.i++]; r |= (x & 127) << s; s += 7; } while (x & 128); return r; };
const utf = (b, o) => { const n = varint(b, o); const t = b.toString('utf8', o.i, o.i + n); o.i += n; return t; };
bot._client.on('packet', (d, meta) => {
  if (meta.name !== 'custom_payload' || !d || d.channel !== 'emberfall:open_merchant') return;
  const b = Buffer.isBuffer(d.data) ? d.data : Buffer.from((d.payload && d.payload.data) || d.data || []); const o = { i: 0 };
  const open = b[o.i++] === 1; const tier = varint(b, o), secs = varint(b, o), gold = varint(b, o), n = varint(b, o); const items = [];
  for (let i = 0; i < n; i++) items.push({ id: utf(b, o), price: varint(b, o), ok: b[o.i++] === 1 });
  stalls.push({ open, tier, secs, gold, items, used: o.i, len: b.length });
});
const sendBuy = i => { const b = []; let v = i < 0 ? (i >>> 0) : i; do { let x = v & 127; v >>>= 7; if (v) x |= 128; b.push(x); } while (v); bot._client.write('custom_payload', { channel: 'emberfall:buy_merchant_item', data: Buffer.from(b) }); };
const state = async () => (await ask('/emberfall relic merchantstate EmberTester', 700)).match(/merchantstate (.*)/)?.[1] ?? '?';
const wallet = async () => { const t = await ask('/emberfall relic state EmberTester', 700); return { gold: +(t.match(/wallet=(\d+)/)?.[1] ?? NaN), total: +(t.match(/total=(\d+)/)?.[1] ?? NaN) }; };
const count = async () => { const t = await ask('/execute if entity @e[type=emberfall:testificate]', 600); return /Count: (\d+)/.exec(t) ? +/Count: (\d+)/.exec(t)[1] : /passed/.test(t) ? 1 : 0; };
const startRun = async () => {
  await ask('/expedition', 1500);
  for (let i = 0; i < 60; i++) { await sleep(2000); if (/expedition/.test(await ask('/data get entity @s Dimension', 400))) break; }
  await sleep(1500); await ask('/emberfall wavestop 0', 500); await ask('/kill @e[type=!player,type=!minecraft:item_display,type=!minecraft:interaction,type=!emberfall:testificate]', 600);
};
const clickMerchant = async () => {
  const pos = (await ask('/data get entity @e[type=emberfall:testificate,limit=1] Pos', 700)).match(/\[(-?[\d.]+)d, (-?[\d.]+)d, (-?[\d.]+)d\]/);
  if (!pos) return null;
  await ask(`/tp @s ${+pos[1]} ${+pos[2]} ${+pos[3] + 2.5} 180 0`, 1500);
  const r = await ask('/emberfall relic merchantclick EmberTester', 800); await sleep(500);
  return /ok/.test(r) ? r : null;
};
bot.once('spawn', async () => {
  await sleep(6500);
  await ask('/gamemode survival'); await ask('/effect give @s minecraft:resistance 999 4 true', 300);
  await ask('/character select juggernaut', 600); await startRun();
  check('M0 no merchant before one is summoned', (await count()) === 0, 'state ' + await state());
  let r = await ask('/emberfall relic merchant EmberTester 0', 1500);
  check('M1 a Common merchant arrives', /arrived/.test(r) && (await count()) === 1, (await state()));
  await ask('/emberfall relic gold EmberTester 1', 400);
  let e = await clickMerchant(); const g0 = (await wallet());
  check('M2 clicking him opens a stall of 3 items for this player', stalls.length >= 1 && stalls[stalls.length - 1].open && stalls[stalls.length - 1].items.length === 3 && stalls[stalls.length - 1].used === stalls[stalls.length - 1].len, JSON.stringify(stalls[stalls.length - 1] || null).slice(0, 220));
  const st = stalls[stalls.length - 1] || { items: [] };
  check('M2b Common merchant: every item costs at least the chest price and is listed as unaffordable with 1 gold', st.items.length === 3 && st.items.every(i => i.price >= 30 && !i.ok) && st.tier === 0, JSON.stringify(st.items));
  // control: cannot afford
  const have0 = await ask('/emberfall relic state EmberTester', 700);
  sendBuy(0); await sleep(800);
  const have1 = await ask('/emberfall relic state EmberTester', 700);
  check('M3 control: too little gold, nothing bought, he stays', have0.match(/total=(\d+)/)[1] === have1.match(/total=(\d+)/)[1] && /phase=STANDING/.test(await state()), (await state()));
  // out-of-range index is ignored
  sendBuy(7); await sleep(500); sendBuy(-1); await sleep(600);
  check('M3b bad index 7 ignored and -1 (closed without buying) keeps him standing', /phase=STANDING/.test(await state()), await state());
  // buy for real
  await ask('/emberfall relic gold EmberTester 1000', 400);
  const before = await wallet(); const chosen = st.items[1];
  const relicsBefore = (await ask('/emberfall relic state EmberTester', 700)).match(/total=(\d+)/)[1];
  sendBuy(1); await sleep(250);
  const phaseNow = await state();
  const after = await wallet(); const relicsAfter = (await ask('/emberfall relic state EmberTester', 700)).match(/total=(\d+)/)[1];
  check('M4 buying slot 1 charges exactly its price', before.gold - after.gold === chosen.price, `${before.gold} -> ${after.gold}, price ${chosen.price}`);
  check('M4b and hands over exactly one relic', +relicsAfter === +relicsBefore + 1, `${relicsBefore} -> ${relicsAfter}`);
  const closed = stalls[stalls.length - 1];
  check('M4c the stall window is closed by a CLOSE packet', closed && closed.open === false, JSON.stringify(closed).slice(0, 120));
  check('M4d right after the buy he is leaving happy (read 250 ms after the packet)', /phase=LEAVING_HAPPY/.test(phaseNow), phaseNow);
  const said = lines.join(' | ');
  check('M4e the player is told what was bought', /Bought: /.test(said), '');
  sendBuy(0); await sleep(400);
  const after2 = await wallet();
  check('M4f control: a second purchase while he is leaving charges nothing', after2.gold === after.gold, `${after.gold} -> ${after2.gold}`);
  await sleep(3500);
  check('M5 he is gone after the exit, state reset', (await count()) === 0 && /phase=NONE/.test(await state()), await state());
  const fw = await ask('/execute if entity @e[type=minecraft:firework_rocket]', 600);
  check('M5b no firework rocket entity is left behind', /failed|Test failed/.test(fw), fw.slice(0, 80));
  console.log(fails === 0 ? 'ALL PASS' : 'SOME FAIL ' + fails); bot.quit(); setTimeout(() => process.exit(0), 400);
});
