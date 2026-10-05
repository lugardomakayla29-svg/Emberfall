// Character Table end to end: place the block, right-click it, decode the real open payload, then send the REAL
// C2S choose_character packet (bytes as the client would write them) and read what the server selected.
const mineflayer = require('mineflayer');
const bot = mineflayer.createBot({ host: '127.0.0.1', port: 25565, username: 'EmberTester', version: '1.21.11', auth: 'offline' });
const sleep = ms => new Promise(r => setTimeout(r, ms));
const lines = []; bot.on('message', m => lines.push(m.toString()));
const ask = async (x, w = 500) => { const n = lines.length; bot.chat(x); await sleep(w); return lines.slice(n).join(' | '); };
const res = []; const check = (n, ok, note) => { res.push(ok); console.log((ok ? 'PASS ' : 'FAIL ') + n + ' ' + note); };
let opened = null;
function rdVar(b, o) { let v = 0, s = 0, x; do { x = b[o.i++]; v |= (x & 0x7f) << s; s += 7; } while (x & 0x80); return v; }
function rdUtf(b, o) { const n = rdVar(b, o); const t = b.slice(o.i, o.i + n).toString('utf8'); o.i += n; return t; }
function wrVar(v) { const a = []; do { let x = v & 0x7f; v >>>= 7; if (v) x |= 0x80; a.push(x); } while (v); return Buffer.from(a); }
function wrUtf(t) { const b = Buffer.from(t, 'utf8'); return Buffer.concat([wrVar(b.length), b]); }
bot._client.on('packet', (d, meta) => {
  if (meta.name !== 'custom_payload') return;
  const ch = d.channel || (d.payload && d.payload.channel) || '';
  if (!String(ch).includes('open_character_select')) return;
  const buf = Buffer.isBuffer(d.data) ? d.data : Buffer.from(d.data || d.payload || []);
  const o = { i: 0 }; const cur = rdUtf(buf, o); const n = rdVar(buf, o); const list = [];
  for (let k = 0; k < n; k++) list.push({ id: rdUtf(buf, o), name: rdUtf(buf, o), lore: rdUtf(buf, o), weapon: rdUtf(buf, o), stats: rdUtf(buf, o) });
  opened = { cur, list, consumed: o.i, total: buf.length };
});
const choose = id => bot._client.write('custom_payload', { channel: 'emberfall:choose_character', data: wrUtf(id) });
const selected = async () => { const r = await ask('/character list', 900); const m = /> ([^(]*?) \S*\((\w+)\)/.exec(lines.slice(-12).join('\n')); return m ? m[2] : (/\u00a7a> .*?\((\w+)\)/.exec(r) || [])[1]; };
bot.once('spawn', async () => {
  await sleep(6500);
  await ask('/gamemode survival'); await ask('/kill @e[type=!player]', 500);
  await ask('/setblock ~ ~ ~2 minecraft:air', 500);
  const placed = await ask('/setblock ~ ~ ~2 emberfall:character_table', 700);
  const there = await ask('/execute if block ~ ~ ~2 emberfall:character_table', 700);
  check('B1 the block places and exists on the server', /Changed the block/.test(placed) && /Test passed/.test(there), placed.slice(0, 60) + ' / ' + there.slice(0, 40));
  // mineflayer has no registry entry for a modded block, so address it by position, not by name
  const blk = bot.blockAt(bot.entity.position.offset(0, 0, 2).floored()) || { position: bot.entity.position.offset(0, 0, 2).floored() };
  blk.position = bot.entity.position.offset(0, 0, 2).floored();
  await bot.activateBlock(blk); await sleep(1500);
  check('B2 right-click opened the screen payload', !!opened, opened ? `entries=${opened.list.length}` : 'nothing received');
  if (opened) {
    check('B3 payload fully consumed (no stray bytes)', opened.consumed === opened.total, `consumed ${opened.consumed} of ${opened.total}`);
    const ids = opened.list.map(e => e.id).sort().join(',');
    check('B4 all 8 characters, real ids', opened.list.length === 8 && ids === 'battlemage,duelist,emberwarden,gravedigger,juggernaut,ranger,reaper,vanguard', ids);
    check('B5 every entry has name, lore, weapon and stats text', opened.list.every(e => e.name && e.lore.length > 10 && e.weapon && /Health .*%/.test(e.stats)), '');
    console.log('   sample:', JSON.stringify(opened.list.find(e => e.id === 'ranger')).slice(0, 300));
    console.log('   current pick reported:', opened.cur);
  }
  // real wire packet
  choose('reaper'); await sleep(900);
  check('B6 a REAL choose_character packet selected the Reaper', lines.slice(-6).some(l => /Selected The Reaper/.test(l)), lines.slice(-3).join(' | ').slice(0, 160));
  opened = null; await bot.activateBlock(blk); await sleep(1200);
  check('B7 reopening reports the new pick', opened && opened.cur === 'reaper', `cur=${opened && opened.cur}`);
  const n0 = lines.length; choose('not_a_character'); await sleep(800);
  check('B8 a forged id is refused with a message', lines.slice(n0).some(l => /Unknown or locked/.test(l)), lines.slice(n0).join(' | ').slice(0, 140));
  opened = null; await bot.activateBlock(blk); await sleep(1200);
  check('B9 the forged id changed nothing', opened && opened.cur === 'reaper', `cur=${opened && opened.cur}`);
  // mid-run: refused
  await ask('/expedition', 1500); await sleep(1500);
  const n1 = lines.length; choose('ranger'); await sleep(800);
  check('B10 picking mid-expedition is refused', lines.slice(n1).some(l => /mid-expedition/.test(l)), lines.slice(n1).join(' | ').slice(0, 150));
  await ask('/expedition leave', 800);
  console.log(res.every(Boolean) ? 'ALL PASS' : 'SOME FAIL');
  bot.quit(); setTimeout(() => process.exit(0), 300);
});
bot.on('error', e => console.log('ERR', e.message));
