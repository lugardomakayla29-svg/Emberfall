const mineflayer = require('mineflayer');
const bot = mineflayer.createBot({ host: '127.0.0.1', port: 25565, username: 'EmberTester', version: '1.21.11', auth: 'offline' });
const sleep = ms => new Promise(r => setTimeout(r, ms));
const lines = []; bot.on('message', m => lines.push(m.toString()));
const ask = async (x, w = 600) => { const n = lines.length; bot.chat(x); await sleep(w); return lines.slice(n).join(' | '); };
bot.once('spawn', async () => {
  await sleep(6000);
  console.log('obj add   :', await ask('/scoreboard objectives add emberfall_t dummy', 400));
  await ask('/kill @e[type=minecraft:cave_spider]', 400);
  await ask('/summon minecraft:cave_spider ~3 ~ ~', 500); await ask('/summon minecraft:cave_spider ~3 ~ ~', 500);
  console.log('execute if:', await ask('/execute if entity @e[type=minecraft:cave_spider]', 500));
  console.log('store     :', await ask('/execute store result score #n emberfall_t run execute if entity @e[type=minecraft:cave_spider]', 500));
  console.log('get       :', await ask('/scoreboard players get #n emberfall_t', 500));
  await ask('/kill @e[type=minecraft:cave_spider]', 400);
  bot.quit(); setTimeout(() => process.exit(0), 300);
});
