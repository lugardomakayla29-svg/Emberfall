// Chest reveal wiring, end to end on the real server. The screen is only a SHOW: the server grants the relic first, then sends open_chest_reveal
// (varint id, utf tier, utf item, long seed) and remembers it. A close (one varint) is honoured only for the id the player was shown, once.
// The bot reads the REAL payload off the wire and sends REAL close packets; the server log (REVEAL_TEST lines, graded by reveal_grade.py) is the record.
const mineflayer = require('mineflayer');
const { Vec3 } = require('vec3');
const bot = mineflayer.createBot({ host: '127.0.0.1', port: 25565, username: 'EmberTester', version: '1.21.11', auth: 'offline' });
const sleep = ms => new Promise(r => setTimeout(r, ms));
const lines = []; bot.on('message', m => lines.push(m.toString())); bot.on('error', e => console.log('ERR', e.message));
const seen = [];
const POOL_SRC = require('fs').readFileSync(process.env.RELICPOOL || require('path').join(__dirname, '..', '..', 'src', 'main', 'java', 'com', 'solme', 'emberfall', 'relic', 'RelicPool.java'), 'utf8');
const NAMES = {}; for (const m of POOL_SRC.matchAll(/\b(?:add|gated)\("([a-z_]+)",\s*"([^"]+)"/g)) NAMES[m[1]] = m[2];
function varint(b, o) { let v = 0, s = 0, x; do { x = b[o.i++]; v |= (x & 0x7f) << s; s += 7; } while (x & 0x80); return v; }
function utf(b, o) { const n = varint(b, o); const t = b.slice(o.i, o.i + n).toString('utf8'); o.i += n; return t; }
bot._client.on('packet', (d, meta) => {
  if (meta.name !== 'custom_payload') return;
  const ch = d.channel || (d.payload && d.payload.channel); if (ch !== 'emberfall:open_chest_reveal') return;
  const buf = Buffer.isBuffer(d.data) ? d.data : Buffer.from((d.payload && d.payload.data) || d.data || []); const o = { i: 0 };
  const rec = { id: varint(buf, o), tier: utf(buf, o), item: utf(buf, o) };
  rec.seed = buf.readBigInt64BE(o.i); o.i += 8;   // the long seed
  rec.len = buf.length; rec.used = o.i; seen.push(rec);
});
const sendClose = id => { const a = []; let v = id >>> 0; do { let x = v & 0x7f; v >>>= 7; if (v) x |= 0x80; a.push(x); } while (v); bot._client.write('custom_payload', { channel: 'emberfall:close_chest_reveal', data: Buffer.from(a) }); };
let fails = 0; const check = (l, ok, e = '') => { console.log((ok ? 'PASS ' : 'FAIL ') + l + (e ? '  ' + e : '')); if (!ok) fails++; };
bot.once('spawn', async () => {
  await sleep(6000);
  const c = async (x, w = 600) => { bot.chat(x); await sleep(w); };
  const ask = async (x, w = 700) => { const n = lines.length; bot.chat(x); await sleep(w); return lines.slice(n).join(' | '); };
  const chests = async () => { const t = await ask('/emberfall relic chests EmberTester', 700); const m = t.match(/total=(\d+) closed=(\d+) free=(\d+) nearest=(-?\d+) (-?\d+) (-?\d+) kind=(\w+)/); return m ? { total: +m[1], closed: +m[2], x: +m[4], y: +m[5], z: +m[6], kind: m[7] } : null; };
  const state = async () => { const t = await ask('/emberfall relic state EmberTester', 700); return { opened: +(t.match(/opened=(\d+)/)?.[1] ?? NaN), price: +(t.match(/price=(\d+)/)?.[1] ?? NaN), raw: t }; };
  const click = async (x, y, z) => { const blk = bot.blockAt(new Vec3(x, y, z)); if (!blk) return 'noblock'; try { await bot.activateBlock(blk); } catch (e) { return 'err ' + e.message; } await sleep(1200); return 'ok'; };
  await c('/gamemode survival'); await c('/character select vanguard', 500);
  await c('/expedition leave', 900); await c('/expedition', 4000);
  for (let i = 0; i < 30; i++) { await sleep(1500); if (/expedition/.test(await ask('/data get entity @s Dimension', 400))) break; } await sleep(2500);
  await c('/emberfall wavestop 0', 300); await c('/effect give @s minecraft:resistance 999 4 true', 200);
  let ch = await chests(); if (!ch) { console.log('FAIL no chests'); process.exit(1); }
  await c(`/tp @s ${ch.x + 0.5} ${ch.y} ${ch.z + 2.5} 180 0`, 1500);
  // R1 a REFUSED open (no gold) sends no reveal
  await c('/emberfall relic gold EmberTester 0', 400);
  const n0 = seen.length; await click(ch.x, ch.y, ch.z); await sleep(800);
  check('R1 a refused open (no gold) sends NO reveal', seen.length === n0, `seen=${seen.length}`);
  // R2 a real open sends exactly one reveal
  await c('/emberfall relic gold EmberTester 500', 400);
  const before = await state(); const n1 = seen.length;
  await click(ch.x, ch.y, ch.z); await sleep(1200);
  const after = await state();
  check('R2 a real open sends exactly ONE reveal', seen.length === n1 + 1, `sent ${seen.length - n1}`);
  const r = seen[seen.length - 1] || {};
  check('R3 the payload is fully read (no trailing or missing bytes)', r.used === r.len, `used ${r.used} of ${r.len}`);
  check('R4 the reveal names a real tier', ['Common', 'Uncommon', 'Rare', 'Legendary'].includes(r.tier), `tier=${r.tier}`);
  check('R5 the relic was GRANTED regardless of the screen (opened counter rose by 1)', after.opened === before.opened + 1, `${before.opened}->${after.opened}`);
  // R6 the relic named in the reveal is the one the player now owns
  const owned = await ask('/emberfall relic state EmberTester', 800);
  // The state lists the relic ID (gold_nugget), the reveal carries its NAME (Golden Nugget). The bot cannot map one to the other, so it asserts only what it
  // knows: the player owns exactly ONE relic and the reveal carried a non-empty item. The id-to-name LINK is asserted by reveal_grade.py from the server log
  // (OPEN_TEST relic=<id> and REVEAL_TEST item=<name> are printed from the same Relic object in the same open).
  const ownedId = (owned.match(/owned=\{([a-z_]+)=1\}/) || [])[1] || '';
  check('R6 the player owns exactly one relic and the reveal carried an item name', !!ownedId && /total=1/.test(owned) && !!r.item && r.item.length > 0, `owned=${ownedId} item=${r.item}`);
  console.log('OWNED_ID ' + ownedId);
  check('R6b the name ON THE WIRE is the real name of the relic the player owns', !!NAMES[ownedId] && r.item === NAMES[ownedId], `wire=${r.item} expected=${NAMES[ownedId]} (${Object.keys(NAMES).length} relics known)`);
  // R7..R10 close handling, graded from the server log too
  sendClose((r.id || 0) + 7); await sleep(500);        // forged: a wrong id
  sendClose(0); await sleep(500);                       // forged: an old id
  sendClose(r.id); await sleep(500);                    // the real one
  sendClose(r.id); await sleep(500);                    // a repeat
  console.log('SENT closes: forged ' + ((r.id || 0) + 7) + ', 0, real ' + r.id + ', repeat ' + r.id);
  console.log('REVEAL_ID ' + r.id);
  console.log(fails === 0 ? 'WIRE PASS' : `WIRE FAILED ${fails}`);
  await sleep(1500); bot.quit(); process.exit(fails ? 1 : 0);
});
