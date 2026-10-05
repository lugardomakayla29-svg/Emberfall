const mineflayer = require('mineflayer');
const bot = mineflayer.createBot({ host: '127.0.0.1', port: 25565, username: 'EmberTester', version: '1.21.11', auth: 'offline' });
const sleep = ms => new Promise(r => setTimeout(r, ms));
const lines = []; bot.on('message', m => lines.push(m.toString()));
const ask = async (x, w = 900) => { const n = lines.length; bot.chat(x); await sleep(w); return lines.slice(n).join(' | '); };
bot.once('spawn', async () => {
  await sleep(4000);
  await ask('/gamemode survival'); await ask('/effect clear @s'); await ask('/clear @s'); await ask('/gamerule keep_inventory false');
  await ask('/give @s minecraft:diamond_sword 1', 500); await ask('/give @s minecraft:apple 5', 500);
  await ask('/character select juggernaut'); await ask('/expedition', 2500);
  await ask('/damage @s 1000 minecraft:generic', 1500); await sleep(1500);
  const inv = bot.inventory.items().map(i => i.name + 'x' + i.count).join(', ');
  console.log('inventory after the run ended:', inv);
  const items = await ask('/execute as @e[type=item] run data get entity @s Item.id', 900);
  console.log('dropped item entities:', items.slice(0, 120) || 'none');
  console.log('has sword:', bot.inventory.items().some(i => i.name === 'diamond_sword'), '| has apples:', bot.inventory.items().some(i => i.name === 'apple'), '| any emberfall weapon:', bot.inventory.items().some(i => /halberd|emberfall/.test(i.name)));
  bot.quit(); setTimeout(() => process.exit(0), 400);
});
