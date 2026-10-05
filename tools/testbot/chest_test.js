// Chests, end to end on the real server: run start registers them as real blocks; a real right click (activateBlock) opens one only
// with enough gold, charges exactly the price, hands over a relic, flips the block to opened, raises the next price, and a chest
// that is NOT in the run's registry (placed by setblock) does nothing. State is read from /emberfall relic state and chests.
const mineflayer = require('mineflayer');
const { Vec3 } = require('vec3');
const bot = mineflayer.createBot({ host: '127.0.0.1', port: 25565, username: 'EmberTester', version: '1.21.11', auth: 'offline' });
const sleep = ms => new Promise(r => setTimeout(r, ms));
const lines = []; bot.on('message', m => lines.push(m.toString()));
function varint(b, o) { let v = 0, s = 0, x; do { x = b[o.i++]; v |= (x & 0x7f) << s; s += 7; } while (x & 0x80); return v; }
function varlong(b, o) { let v = 0n, s = 0n, x; do { x = b[o.i++]; v |= BigInt(x & 0x7f) << s; s += 7n; } while (x & 0x80); return Number(v); }
const hud = [];
bot._client.on('packet', (d, meta) => {
  if (meta.name !== 'custom_payload') return;
  const ch = d.channel || (d.payload && d.payload.channel); if (ch !== 'emberfall:run_hud') return;
  const buf = Buffer.isBuffer(d.data) ? d.data : Buffer.from((d.payload && d.payload.data) || d.data || []); const o = { i: 0 };
  if (buf[o.i++] !== 1) { hud.push({ active: false }); return; }
  hud.push({ active: true, elapsed: varint(buf, o), level: varint(buf, o), xp: varint(buf, o), gold: varint(buf, o), silver: varlong(buf, o), kills: varint(buf, o), price: varint(buf, o), relics: [] });
  { const h = hud[hud.length - 1]; const pairs = varint(buf, o); for (let k = 0; k < pairs * 2; k++) h.relics.push(varint(buf, o)); h.used = o.i; h.len = buf.length; }
});
let fails = 0; const check = (l, ok, e = '') => { console.log((ok ? 'PASS ' : 'FAIL ') + l + (e ? '  ' + e : '')); if (!ok) fails++; };
bot.once('spawn', async () => {
  await sleep(6000);
  const c = async (x, w = 600) => { bot.chat(x); await sleep(w); };
  const ask = async (x, w = 700) => { const n = lines.length; bot.chat(x); await sleep(w); return lines.slice(n).join(' | '); };
  const state = async () => { const t = await ask('/emberfall relic state EmberTester', 700); return { price: +(t.match(/price=(\d+)/)?.[1] ?? NaN), opened: +(t.match(/opened=(\d+)/)?.[1] ?? NaN), owned: t.match(/owned=(\{[^}]*\})/)?.[1] ?? '?', raw: t }; };
  const gold = async () => +((await ask('/emberfall relic state EmberTester', 700)).match(/wallet=(\d+)/)?.[1] ?? NaN);
  const setGold = async n => { await c(`/emberfall relic gold EmberTester ${n}`, 400); };
  const chests = async () => { const t = await ask('/emberfall relic chests EmberTester', 700); const m = t.match(/total=(\d+) closed=(\d+) free=(\d+) nearest=(-?\d+) (-?\d+) (-?\d+) kind=(\w+)/); return m ? { total: +m[1], closed: +m[2], free: +m[3], x: +m[4], y: +m[5], z: +m[6], kind: m[7] } : { raw: t }; };
  const blockAt = async (x, y, z) => (await ask(`/execute if block ${x} ${y} ${z} emberfall:ember_chest_paid run say PAIDBLOCK`, 400)).includes('PAIDBLOCK') ? 'paid' : (await ask(`/execute if block ${x} ${y} ${z} emberfall:ember_chest_gold run say GOLDBLOCK`, 400)).includes('GOLDBLOCK') ? 'gold' : 'other';
  const isOpen = async (x, y, z) => (await ask(`/execute if block ${x} ${y} ${z} emberfall:ember_chest_paid[opened=true] run say OPENYES`, 400)).includes('OPENYES') || (await ask(`/execute if block ${x} ${y} ${z} emberfall:ember_chest_gold[opened=true] run say OPENYES`, 400)).includes('OPENYES');
  await c('/gamemode survival'); await c('/character select vanguard', 500);
  await c('/expedition leave', 900); await c('/expedition', 4000);
  for (let i = 0; i < 30; i++) { await sleep(1500); if (/expedition/.test(await ask('/data get entity @s Dimension', 400))) break; } await sleep(2500);
  await c('/emberfall wavestop 0', 300); await c('/effect give @s minecraft:resistance 999 4 true', 200);
  // 1 registry + world
  let ch = await chests();
  check('run start registers 16 chests, all closed', ch.total === 16 && ch.closed === 16, JSON.stringify(ch));
  let st = await state(); check('relic layer is active and the first price is 30', st.price === 30 && st.opened === 0, st.raw.slice(0, 160));
  // walk to the nearest chest: teleport 2 blocks beside it, face it
  const goTo = async () => { ch = await chests(); await c(`/tp @s ${ch.x + 0.5} ${ch.y} ${ch.z + 2.5} 180 0`, 1500); return ch; };
  ch = await goTo();
  const b = await blockAt(ch.x, ch.y, ch.z);
  check('the registered spot really holds a chest block in the world', b === 'paid' || b === 'gold', b);
  const click = async (x, y, z) => { const blk = bot.blockAt(new Vec3(x, y, z)); if (!blk) return 'noblock'; try { await bot.activateBlock(blk); } catch (e) { return 'err ' + e.message; } await sleep(900); return 'ok'; };
  const price0 = st.price;
  // 2 short on gold: refused, nothing changes
  await setGold(price0 - 1);
  const before = await state(); const g0 = await gold();
  const r2 = await click(ch.x, ch.y, ch.z);
  const after2 = await state(); const g2 = await gold();
  check('click delivered (' + r2 + ')', r2 === 'ok');
  const chestIsGold = ch.kind === 'GOLD';
  if (!chestIsGold) {
    check('one gold short: gold unchanged', g2 === g0, `${g0} -> ${g2}`);
    check('one gold short: no relic, counter and price unchanged', after2.owned === before.owned && after2.opened === before.opened && after2.price === before.price, `${before.owned} -> ${after2.owned}`);
    check('one gold short: chest still closed', !(await isOpen(ch.x, ch.y, ch.z)));
  }
  // 3 enough gold: pays exactly the price
  await setGold(price0 + 50);
  const g3a = await gold(); const s3a = await state();
  await click(ch.x, ch.y, ch.z);
  const g3b = await gold(); const s3b = await state();
  check('enough gold: charged exactly the price', g3a - g3b === price0 || (chestIsGold && g3a - g3b === price0), `${g3a} -> ${g3b} (price ${price0})`);
  check('a relic was handed over', s3b.owned !== s3a.owned, `${s3a.owned} -> ${s3b.owned}`);
  // The relic that arrives with the opening decides which rule applies: Ember Ledger freezes the price on purpose
  // (PlayerRelics.addChestOpened returns early), so neither the counter nor the price may rise. Decide from s3b, not s3a.
  const ledger = /ember_ledger/.test(s3b.owned);
  console.log('BRANCH: ' + (ledger ? 'ember_ledger (price frozen by design)' : 'normal (price rises)'));
  if (ledger) {
    check('Ember Ledger: the opened counter and the next price are unchanged', s3b.opened === s3a.opened && s3b.price === s3a.price, `${s3a.opened}->${s3b.opened}, ${s3a.price}->${s3b.price}`);
  } else {
    check('the opened counter rose by 1 and the next price is higher', s3b.opened === s3a.opened + 1 && s3b.price > s3a.price, `${s3a.opened}->${s3b.opened}, ${s3a.price}->${s3b.price}`);
  }
  check('the block flipped to opened=true', await isOpen(ch.x, ch.y, ch.z));
  const ch2 = await chests(); check('registry now shows 15 closed', ch2.closed === 15, JSON.stringify(ch2));
  // 3b the HUD price follows the opening: hold gold constant at the value the opening left, so only the price can have changed
  await sleep(2500);
  const h = hud[hud.length - 1];
  if (ledger) {
    check(`Ember Ledger: HUD packet carries the unchanged price (${s3b.price}) after one opening`, h && h.active && h.price === s3b.price && h.used === h.len, h ? `price ${h.price}, bytes ${h.used}/${h.len}` : 'no packet');
  } else {
    check('HUD packet carries the new price (38) after one opening', h && h.active && h.price === 38 && h.used === h.len, h ? `price ${h.price}, bytes ${h.used}/${h.len}` : 'no packet');
  }
  // 4 second click on the open chest does nothing
  await setGold(500); const g4a = await gold(); const s4a = await state();
  await click(ch.x, ch.y, ch.z);
  const g4b = await gold(); const s4b = await state();
  check('clicking an opened chest again changes nothing', g4a === g4b && s4a.owned === s4b.owned && s4a.opened === s4b.opened, `${g4a}->${g4b} ${s4a.owned}->${s4b.owned}`);
  // 5 stray chest control: not registered, gold plenty
  await setGold(100000); const sx = Math.floor(bot.entity.position.x) + 3, sy = Math.floor(bot.entity.position.y), sz = Math.floor(bot.entity.position.z);
  await c(`/setblock ${sx} ${sy} ${sz} emberfall:ember_chest_paid[facing=north,opened=false]`, 500);
  const g5a = await gold(); const s5a = await state();
  await click(sx, sy, sz);
  const g5b = await gold(); const s5b = await state();
  check('a stray chest (not in the run registry) does nothing', g5a === g5b && s5a.owned === s5b.owned && s5a.opened === s5b.opened, `${g5a}->${g5b} ${s5a.owned}->${s5b.owned}`);
  check('the stray chest stays closed', !(await isOpen(sx, sy, sz)));
  await c(`/setblock ${sx} ${sy} ${sz} minecraft:air`, 300);
  // 6 too far: the server must refuse by its own measurement. Teleport away, CONFIRM the server-side position, then click.
  ch = await chests(); const target = new Vec3(ch.x, ch.y, ch.z); const far = bot.blockAt(target);
  const sx6 = ch.x > 0 ? ch.x - 30 : ch.x + 30; await c(`/tp @s ${sx6 + 0.5} ${ch.y} ${ch.z + 0.5} 180 0`, 1500);
  const posTxt = await ask('/data get entity @s Pos', 600); const pm = posTxt.match(/\[(-?[\d.]+)d, (-?[\d.]+)d, (-?[\d.]+)d\]/);
  const serverDist = pm ? Math.hypot(+pm[1] - (ch.x + 0.5), +pm[2] - (ch.y + 0.5), +pm[3] - (ch.z + 0.5)) : NaN;
  check('the server places the player about 30 blocks from the chest before the click', serverDist > 25 && serverDist < 35, 'dist ' + serverDist.toFixed(1));
  await setGold(100000);
  const g6a = await gold(); const s6a = await state(); const c6a = await chests();
  try { await bot.activateBlock(far); } catch (e) {} await sleep(900);
  const g6b = await gold(); const s6b = await state(); const c6b = await chests();
  check('too far away: gold, relics and the closed count are all unchanged', g6a === g6b && s6a.opened === s6b.opened && s6a.owned === s6b.owned && c6a.closed === c6b.closed, `${g6a}->${g6b} closed ${c6a.closed}->${c6b.closed}`);
  // control for the control: the same chest from 2 blocks away DOES open
  await c(`/tp @s ${ch.x + 0.5} ${ch.y} ${ch.z + 2.5} 180 0`, 1500);
  await click(ch.x, ch.y, ch.z);
  const c6c = await chests(); const g6c = await gold();
  check('control: the same chest opens from 2 blocks away', c6c.closed === c6a.closed - 1 && g6c < g6b, `closed ${c6a.closed}->${c6c.closed}, gold ${g6b}->${g6c}`);
  await c('/expedition leave', 800);
  console.log(fails === 0 ? 'RESULT: ALL PASS' : 'RESULT: ' + fails + ' FAILED'); bot.quit(); setTimeout(() => process.exit(0), 400);
});
