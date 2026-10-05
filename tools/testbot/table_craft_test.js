// Character Table recipe in a REAL crafting table: read the RESULT slot for several layouts.
const mineflayer = require('mineflayer');
const bot = mineflayer.createBot({ host: '127.0.0.1', port: 25565, username: 'EmberTester', version: '1.21.11', auth: 'offline' });
const sleep = ms => new Promise(r => setTimeout(r, ms));
const lines = []; bot.on('message', m => lines.push(m.toString()));
const res = []; const check = (n, ok, note) => { res.push(ok); console.log((ok ? 'PASS ' : 'FAIL ') + n + ' ' + note); };
const LAYOUTS = {
  exact:       { grid: ['lectern', 'iron_ingot', 'iron_ingot', 'emerald', 'book', null, null, null, null], expect: 'character_table' },
  scrambled:   { grid: [null, 'book', null, 'iron_ingot', null, 'lectern', 'emerald', null, 'iron_ingot'], expect: 'character_table' },
  one_iron:    { grid: ['lectern', 'iron_ingot', 'emerald', 'book', null, null, null, null, null], expect: null },
  extra_item:  { grid: ['lectern', 'iron_ingot', 'iron_ingot', 'emerald', 'book', 'stick', null, null, null], expect: null },
  no_lectern:  { grid: ['iron_ingot', 'iron_ingot', 'emerald', 'book', null, null, null, null, null], expect: null },
};
bot.once('spawn', async () => {
  await sleep(5000);
  const c = async (x, w = 500) => { bot.chat(x); await sleep(w); };
  await c('/gamemode survival'); await c('/clear @s');
  await c('/setblock ~ ~ ~2 minecraft:crafting_table');
  for (const g of ['lectern 3', 'iron_ingot 8', 'emerald 3', 'book 3', 'stick 2']) await c('/give @s minecraft:' + g, 300);
  await sleep(500);
  const table = bot.findBlock({ matching: b => b.name === 'crafting_table', maxDistance: 6 });
  if (!table) { console.log('NO TABLE'); process.exit(1); }
  const win = await bot.openBlock(table);
  for (const [name, L] of Object.entries(LAYOUTS)) {
    for (let s = 1; s <= 9; s++) { if (win.slots[s]) { await bot.clickWindow(s, 0, 1); await sleep(80); } }
    await sleep(300);
    for (let i = 0; i < 9; i++) {
      const want = L.grid[i]; if (!want) continue;
      const item = bot.inventory.items().find(it => it.name === want);
      if (!item) { console.log(name, 'missing item', want); continue; }
      const slot = win.slots.findIndex((it, idx) => idx >= 10 && it && it.type === item.type);
      await bot.clickWindow(slot, 1, 0); await sleep(120); await bot.clickWindow(i + 1, 0, 0); await sleep(150);
      if (bot.currentWindow.selectedItem) { await bot.clickWindow(slot, 0, 0); await sleep(100); }
    }
    await sleep(500);
    const r = win.slots[0];
    const got = r ? r.name : null;
    let ok = got === L.expect, note = '';
    if (L.expect) {   // modded item: mineflayer prints 'unknown', so take it and let the SERVER name it
      await bot.clickWindow(0, 0, 1); await sleep(700);
      const n = lines.length; bot.chat('/clear @s emberfall:character_table 0'); await sleep(900);
      const reply = lines.slice(n).join(' | ');
      const m = /(\d+) matching item/.exec(reply);
      ok = !!r && !!m && +m[1] >= 1; note = ` SERVER: ${reply.slice(0, 90)}`;
      bot.chat('/clear @s emberfall:character_table'); await sleep(500);
    }
    check('C-' + name, ok, `grid=${win.slots.slice(1, 10).map(s => s ? s.name : '-').join(',')} RESULT=${got}${r ? ' x' + r.count : ''}${note}`);
  }
  console.log(res.every(Boolean) ? 'ALL PASS' : 'SOME FAIL');
  bot.quit(); setTimeout(() => process.exit(0), 300);
});
bot.on('error', e => console.log('ERR', e.message));
