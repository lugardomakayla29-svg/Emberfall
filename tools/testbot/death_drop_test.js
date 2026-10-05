// keepInventory OFF: vanilla should drop the player's OWN items at the death spot, and never the run weapon.
// keepInventory ON : the player's own sword must be back in slot 0 after respawn.
const mineflayer = require('mineflayer');
const bot = mineflayer.createBot({ host: '127.0.0.1', port: 25565, username: 'EmberTester', version: '1.21.11', auth: 'offline' });
const sleep = ms => new Promise(r => setTimeout(r, ms));
const lines = []; bot.on('message', m => lines.push(m.toString()));
let fails = 0; const verdict = (n, ok, d) => { if (!ok) fails++; console.log(`${n.padEnd(38)}: ${ok ? 'PASS' : 'FAIL'} ${d || ''}`); };
bot.once('spawn', async () => {
  await sleep(6000);
  const c = async (x, w = 700) => { bot.chat(x); await sleep(w); };
  const ask = async (x, w = 800) => { const n = lines.length; bot.chat(x); await sleep(w); return lines.slice(n).join(' | '); };
  const exists = async (sel) => /Test passed/.test(await ask(`/execute if entity ${sel}`));
  const slot0 = async () => (await ask('/data get entity EmberTester Inventory[{Slot:0b}]')).match(/id: "([a-z_:]+)"/)?.[1] ?? 'empty';
  const setup = async () => { await c('/clear EmberTester', 500); await c('/kill @e[type=item]', 400);
    await c('/item replace entity EmberTester hotbar.0 with minecraft:diamond_sword', 500); };
  await c('/gamemode survival'); await c('/character select juggernaut'); await c('/expedition leave', 900);

  // ---- keepInventory OFF
  await c('/gamerule keep_inventory false', 500);
  await setup(); await c('/expedition', 4000); await sleep(1500);
  await c('/kill EmberTester', 2500);
  // A run player no longer really dies (the lethal hit is cancelled), so the sword is NOT dropped: it stays put.
  verdict('E1 own sword NOT dropped (no real death now)', !(await exists('@e[type=item,nbt={Item:{id:"minecraft:diamond_sword"}}]')));
  verdict('E1 run weapon NOT dropped', !(await exists('@e[type=item,nbt={Item:{id:"emberfall:war_halberd"}}]')));
  await c('/kill @e[type=item]', 400); await sleep(1500);

  // ---- keepInventory ON
  await c('/gamerule keep_inventory true', 500);
  await setup(); await c('/expedition', 4000); await sleep(1500);
  await c('/kill EmberTester', 2500); await sleep(2000);
  verdict('F1 sword kept in slot 0 (keepInv on)', (await slot0()) === 'minecraft:diamond_sword', await slot0());
  verdict('F1 run weapon NOT dropped', !(await exists('@e[type=item,nbt={Item:{id:"emberfall:war_halberd"}}]')));
  verdict('F1 no weapon item left in inventory', !/emberfall:/.test(await ask('/data get entity EmberTester Inventory', 900)));
  await c('/gamerule keep_inventory false', 400);
  console.log(fails === 0 ? 'ALL PASS' : `${fails} FAILED`);
  bot.quit(); setTimeout(() => process.exit(0), 500);
});
