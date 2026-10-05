// What displays exist around a freshly spawned Broodmother? No item/tag filter, so nothing is hidden by my selector.
const mineflayer = require('mineflayer');
const bot = mineflayer.createBot({ host: '127.0.0.1', port: 25565, username: 'EmberTester', version: '1.21.11', auth: 'offline' });
const sleep = ms => new Promise(r => setTimeout(r, ms));
const lines = []; bot.on('message', m => lines.push(m.toString()));
const ask = async (x, w = 350) => { const n = lines.length; bot.chat(x); await sleep(w); return lines.slice(n).join(' | '); };
const cnt = async sel => { await ask(`/execute store result score #n emberfall_t run execute if entity ${sel}`, 200); const r = await ask('/scoreboard players get #n emberfall_t', 250); const m = /has (-?\d+)/.exec(r); return m ? +m[1] : -1; };
bot.once('spawn', async () => {
  await sleep(6000);
  await ask('/scoreboard objectives add emberfall_t dummy', 300);
  await ask('/gamemode survival'); await ask('/effect give @s minecraft:resistance 900 4 true', 200);
  await ask('/kill @e[type=!player]', 700);
  await ask('/emberfall spawnelite broodmother_stalker', 1500);
  console.log('mothers            ', await cnt('@e[type=emberfall:broodmother_stalker]'));
  console.log('item_display (any) ', await cnt('@e[type=minecraft:item_display]'));
  console.log('block_display (any)', await cnt('@e[type=minecraft:block_display]'));
  console.log('any display tagged emberfall_run', await cnt('@e[type=minecraft:item_display,tag=emberfall_run]'));
  console.log('player_head items  ', await cnt('@e[type=minecraft:item_display,nbt={item:{id:"minecraft:player_head"}}]'));
  console.log('within 2 of mother ', await cnt('@e[type=minecraft:item_display,distance=..2]'), '(distance from the player, not her)');
  const r = await ask('/data get entity @e[type=minecraft:item_display,limit=1,sort=nearest] item', 500);
  console.log('nearest display item:', r.replace(/\s+/g, ' ').slice(0, 200));
  await ask('/kill @e[type=!player]', 400);
  bot.quit(); setTimeout(() => process.exit(0), 300);
});
bot.on('error', e => console.log('ERR', e.message));
