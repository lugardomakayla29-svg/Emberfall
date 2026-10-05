// Places the Hearth ingredients in a real crafting table and reads the RESULT slot. LAYOUT=user|recipe
const mineflayer = require('mineflayer');
const bot = mineflayer.createBot({ host: '127.0.0.1', port: 25565, username: 'EmberTester', version: '1.21.11', auth: 'offline' });
const sleep = ms => new Promise(r => setTimeout(r, ms));
const lines = []; bot.on('message', m => lines.push(m.toString()));
const LAYOUTS = {
  your_old_grid: ['gold_ingot', null, 'gold_ingot', 'blaze_powder', 'magma_block', 'blaze_powder', 'obsidian', 'obsidian', 'obsidian'],
  scrambled:     ['blaze_powder', 'obsidian', null, null, 'magma_block', null, 'gold_ingot', null, 'blaze_powder'],
  exact_5_items: ['magma_block', 'blaze_powder', 'blaze_powder', 'gold_ingot', 'obsidian', null, null, null, null],
  one_blaze_only: ['magma_block', 'blaze_powder', null, 'gold_ingot', 'obsidian', null, null, null, null],
};
bot.once('spawn', async () => {
  await sleep(4000);
  const c = async (x, w = 500) => { bot.chat(x); await sleep(w); };
  await c('/gamemode survival'); await c('/clear @s');
  await c('/setblock ~ ~ ~2 minecraft:crafting_table'); await c('/give @s minecraft:gold_ingot 4'); await c('/give @s minecraft:blaze_powder 6');
  await c('/give @s minecraft:magma_block 2'); await c('/give @s minecraft:obsidian 6', 800);
  const table = bot.findBlock({ matching: b => b.name === 'crafting_table', maxDistance: 6 });
  if (!table) { console.log('NO TABLE'); process.exit(1); }
  const win = await bot.openBlock(table);
  for (const name of Object.keys(LAYOUTS)) {
    // clear grid slots 1..9 back to inventory
    for (let s = 1; s <= 9; s++) { if (win.slots[s]) { await bot.clickWindow(s, 0, 1); await sleep(80); } }
    await sleep(300);
    const grid = LAYOUTS[name];
    for (let i = 0; i < 9; i++) {
      if (!grid[i]) continue;
      const item = bot.inventory.items().find(it => it.name === grid[i]);
      if (!item) { console.log(name, 'missing item', grid[i]); continue; }
      const slot = win.slots.findIndex((it, idx) => idx >= 10 && it && it.type === item.type);
      await bot.clickWindow(slot, 1, 0); await sleep(120); await bot.clickWindow(i + 1, 0, 0); await sleep(150);
      // right-click placed 1 item then put the cursor back
      if (bot.currentWindow.selectedItem) { await bot.clickWindow(slot, 0, 0); await sleep(100); }
    }
    await sleep(500);
    const res = win.slots[0]; const rid = res ? res.type : -1;
    console.log(`LAYOUT ${name}: grid=${win.slots.slice(1, 10).map(s => s ? s.name : '-').join(',')} | RESULT=${res ? res.name + ' x' + res.count : 'NONE'}`);
  }
  // take the last craftable result and identify it on the server
  for (let s2 = 1; s2 <= 9; s2++) { if (win.slots[s2]) { await bot.clickWindow(s2, 0, 1); await sleep(80); } }
  const grid = LAYOUTS.exact_5_items;
  for (let i = 0; i < 9; i++) {
    if (!grid[i]) continue;
    const item = bot.inventory.items().find(it => it.name === grid[i]);
    const slot = win.slots.findIndex((it, idx) => idx >= 10 && it && it.type === item.type);
    await bot.clickWindow(slot, 1, 0); await sleep(120); await bot.clickWindow(i + 1, 0, 0); await sleep(150);
    if (bot.currentWindow.selectedItem) { await bot.clickWindow(slot, 0, 0); await sleep(100); }
  }
  await sleep(500);
  await bot.clickWindow(0, 0, 1); await sleep(700);   // shift-click the result into the inventory
  bot.closeWindow(win); await sleep(400);
  const n = lines.length; bot.chat('/clear @s emberfall:ember_hearth 0'); await sleep(900);
  console.log('SERVER on crafted item:', lines.slice(n).join(' | '));
  bot.quit(); setTimeout(() => process.exit(0), 500);
});
bot.on('error', e => console.log('ERR', e.message));
