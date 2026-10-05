const mineflayer = require('mineflayer');
const sleep = ms => new Promise(r => setTimeout(r, ms));
const bot = mineflayer.createBot({ host: '127.0.0.1', port: 25565, username: 'EmberTester', version: '1.21.11', auth: 'offline' });
const lines = []; bot.on('message', m => lines.push(m.toString()));
const ask = async (x, w = 500) => { const n = lines.length; bot.chat(x); await sleep(w); return lines.slice(n).join(' | '); };
bot.once('spawn', async () => {
  await sleep(5000);
  await ask('/gamemode survival'); await ask('/character select juggernaut'); await ask('/expedition', 2500);
  for (let i = 0; i < 40; i++) { await sleep(2000); if (/expedition/.test(await ask('/data get entity @s Dimension', 400))) break; }
  await sleep(2500); await ask('/emberfall wavestop 0', 300);
  console.log('SPAWN', await ask('/execute at @s positioned ~5 ~ ~ run emberfall spawnelite liquid_probe', 700));
  console.log('COUNT', await ask('/execute store result score #c emberfall_t if entity @e[type=minecraft:block_display,nbt={block_state:{Name:"minecraft:pink_stained_glass"}}]', 300));
  console.log('DATA1', (await ask('/data get entity @e[type=minecraft:block_display,nbt={block_state:{Name:"minecraft:pink_stained_glass"}},limit=1]', 700)).slice(0, 1000));
  await sleep(2500);
  console.log('DATA2', (await ask('/data get entity @e[type=minecraft:block_display,nbt={block_state:{Name:"minecraft:pink_stained_glass"}},limit=1] transformation', 700)).slice(0, 400));
  console.log('TAGS', (await ask('/data get entity @e[type=minecraft:block_display,nbt={block_state:{Name:"minecraft:pink_stained_glass"}},limit=1] Tags', 500)).slice(0, 200));
  console.log('POS', (await ask('/data get entity @e[type=minecraft:block_display,nbt={block_state:{Name:"minecraft:pink_stained_glass"}},limit=1] Pos', 500)).slice(0, 200));
  console.log('ME', (await ask('/data get entity @s Pos', 500)).slice(0, 200));
  await ask('/kill @e[type=minecraft:block_display,nbt={block_state:{Name:"minecraft:pink_stained_glass"}}]', 300); await ask('/expedition leave', 600);
  bot.quit(); setTimeout(() => process.exit(0), 300);
});
