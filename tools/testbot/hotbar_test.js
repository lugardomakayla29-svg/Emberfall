// A run must never destroy an item the player owns. Fresh world. Cases: leave, scroll mid-run, full inventory, death.
const mineflayer = require('mineflayer');
const bot = mineflayer.createBot({ host: '127.0.0.1', port: 25565, username: 'EmberTester', version: '1.21.11', auth: 'offline' });
const sleep = ms => new Promise(r => setTimeout(r, ms));
const lines = []; bot.on('message', m => lines.push(m.toString()));
let fails = 0; const verdict = (name, ok, detail) => { if (!ok) fails++; console.log(`${name.padEnd(34)}: ${ok ? 'PASS' : 'FAIL'} ${detail || ''}`); };
bot.once('spawn', async () => {
  await sleep(6000);
  const c = async (x, w = 700) => { bot.chat(x); await sleep(w); };
  const ask = async (x, w = 800) => { const n = lines.length; bot.chat(x); await sleep(w); return lines.slice(n).join(' | '); };
  const id = async (n) => (await ask(`/data get entity EmberTester Inventory[{Slot:${n}b}]`)).match(/id: "([a-z_:]+)"/)?.[1] ?? 'empty';
  const count = async (n) => Number((await ask(`/data get entity EmberTester Inventory[{Slot:${n}b}]`)).match(/count: (\d+)/)?.[1] ?? 0);
  const weaponsInInv = async () => (await ask('/data get entity EmberTester Inventory', 900)).match(/emberfall:[a-z_]+/g)?.length ?? 0;
  const setup = async () => {
    await c('/clear EmberTester', 500);
    await c('/item replace entity EmberTester hotbar.0 with minecraft:diamond_sword', 500);
    await c('/item replace entity EmberTester hotbar.1 with minecraft:golden_apple 5', 500);
    await c('/item replace entity EmberTester hotbar.2 with minecraft:iron_pickaxe', 500);
  };
  await c('/gamemode survival'); await c('/character select juggernaut'); await c('/expedition leave', 900);

  // ---- case A: plain leave
  await setup();
  verdict('A0 sword before', (await id(0)) === 'minecraft:diamond_sword');
  await c('/expedition', 4000); await sleep(1500);
  verdict('A1 slot0 is the weapon in run', (await id(0)) === 'emberfall:war_halberd', await id(0));
  verdict('A1 apples untouched', (await id(1)) === 'minecraft:golden_apple' && (await count(1)) === 5);
  await c('/expedition leave', 1500);
  verdict('A2 sword back after leave', (await id(0)) === 'minecraft:diamond_sword', await id(0));
  verdict('A2 no weapon items left', (await weaponsInInv()) === 0);
  verdict('A2 apples + pickaxe intact', (await id(1)) === 'minecraft:golden_apple' && (await id(2)) === 'minecraft:iron_pickaxe');

  // ---- case B: scroll away mid-run, then a weapon is gained (a second sync write)
  await setup();
  await c('/expedition', 4000); await sleep(1500);
  bot.setQuickBarSlot(2); await sleep(500);
  await c('/emberfall givecurrency EmberTester 5000', 600);
  await c('/emberfall shopbuy EmberTester upgrade slot_weapon', 800);
  await c('/emberfall buyweapon EmberTester hunting_bow', 800);
  await c('/emberfall weaponoffer EmberTester', 1200);
  await c('/emberfall weaponanswer EmberTester hunting_bow', 1200);
  verdict('B1 pickaxe untouched after scroll', (await id(2)) === 'minecraft:iron_pickaxe', await id(2));
  verdict('B1 apples untouched', (await id(1)) === 'minecraft:golden_apple');
  await c('/expedition leave', 1500);
  verdict('B2 sword back', (await id(0)) === 'minecraft:diamond_sword', await id(0));
  verdict('B2 no weapon items left', (await weaponsInInv()) === 0);
  bot.setQuickBarSlot(0);

  // ---- case C: slot 0 was occupied by a run weapon-shaped nothing (empty hand) -> nothing to restore, weapon still removed
  await c('/clear EmberTester', 500);
  await c('/expedition', 4000); await sleep(1500);
  verdict('C1 weapon shown with empty hotbar', (await id(0)) === 'emberfall:war_halberd', await id(0));
  await c('/expedition leave', 1500);
  verdict('C2 slot0 empty again', (await id(0)) === 'empty', await id(0));
  verdict('C2 no weapon items left', (await weaponsInInv()) === 0);

  // ---- case D: death (not a clean leave). keepInventory OFF so vanilla really drops the inventory.
  await c('/gamerule keep_inventory false', 500);
  await c('/kill @e[type=item]', 400);
  await setup();
  await c('/expedition', 4000); await sleep(1500);
  await c('/kill EmberTester', 2500);
  const dropped = await ask('/execute if entity @e[type=item,nbt={Item:{id:"emberfall:war_halberd"}}]', 800);
  verdict('D0 no weapon item dropped on death', /Test failed/.test(dropped), dropped.slice(0, 60));
  await c('/expedition leave', 800);
  await sleep(2500);
  // keepInventory is off here, so vanilla drops the player's own sword at the death spot (see death_drop_test.js).
  // No real death any more: the run ends, the player lives and the sword is back in the inventory (never erased, never dropped).
  verdict('D1 own sword kept in the inventory, not erased', bot.inventory.items().some(i => i.name === 'diamond_sword'));
  verdict('D1 own sword not dropped on the ground', !/Test passed/.test(await ask('/execute if entity @e[type=item,nbt={Item:{id:"minecraft:diamond_sword"}}]')));
  verdict('D1 no weapon items left', (await weaponsInInv()) === 0);
  console.log(fails === 0 ? 'ALL PASS' : `${fails} FAILED`);
  bot.quit(); setTimeout(() => process.exit(0), 500);
});
