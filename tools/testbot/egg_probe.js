const mineflayer = require('mineflayer');
const { Vec3 } = require('vec3');
const bot = mineflayer.createBot({ host: '127.0.0.1', port: 25565, username: 'EmberTester', version: '1.21.11', auth: 'offline' });
const sleep = ms => new Promise(r => setTimeout(r, ms));
const lines = []; bot.on('message', m => lines.push(m.toString()));
const ask = async (x, w = 600) => { const n = lines.length; bot.chat(x); await sleep(w); return lines.slice(n).join(' | '); };
bot.once('spawn', async () => {
  await sleep(6500);
  await ask('/gamemode creative', 400); await ask('/kill @e[type=!player]', 500);
  await ask('/fill 94 199 94 106 199 106 minecraft:stone', 700);
  await ask('/tp @s 100 200 100', 700); await sleep(1200);
  console.log('bot y =', bot.entity.position.y.toFixed(2));
  await ask('/item replace entity @s hotbar.0 with emberfall:horde_zombie_egg', 500);
  bot.setQuickBarSlot(0); await sleep(400);
  const block = bot.blockAt(new Vec3(100, 199, 102));
  console.log('BLOCK', block && block.name, '| pos', bot.entity.position.toString(), '| held', bot.heldItem && bot.heldItem.name);
  let err = 'none';
  try { await Promise.race([bot.activateBlock(block, new Vec3(0, 1, 0)), sleep(3000)]); } catch (e) { err = e.message; }
  await sleep(1200);
  console.log('ERR', err);
  console.log('COUNT', (await ask('/execute if entity @e[type=emberfall:horde_zombie,distance=..20]', 600)).slice(0, 120));
  console.log('HELD AFTER', bot.heldItem ? bot.heldItem.name + ' x' + bot.heldItem.count : 'empty');
  bot.quit(); setTimeout(() => process.exit(0), 300);
});
bot.on('error', e => console.log('ERR', e.message));
