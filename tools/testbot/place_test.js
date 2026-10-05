// Survival player right-clicks the ground with the Hearth item. Does a block appear at the target cell?
const mineflayer = require('mineflayer'); const { Vec3 } = require('vec3');
const bot = mineflayer.createBot({ host: '127.0.0.1', port: 25565, username: 'EmberTester', version: '1.21.11', auth: 'offline' });
const sleep = ms => new Promise(r => setTimeout(r, ms));
const lines = []; bot.on('message', m => lines.push(m.toString()));
bot.once('spawn', async () => {
  await sleep(4000);
  const c = async (x, w = 600) => { bot.chat(x); await sleep(w); };
  await c('/gamemode survival'); await c('/clear @s');
  await c('/give @s emberfall:ember_hearth 1', 300);
  for (let i = 0; i < 20 && bot.inventory.items().length === 0; i++) await sleep(250);
  console.log('inventory:', bot.inventory.items().map(i => i.name + 'x' + i.count).join(','));
  const item = bot.inventory.items().find(i => /hearth/.test(i.name) || /ember/.test(i.name)) || bot.inventory.items()[0];
  if (!item) { console.log('NO ITEM in inventory'); process.exit(1); }
  await c('/fill ~1 ~-1 ~1 ~3 ~-1 ~3 minecraft:stone'); await c('/fill ~1 ~ ~1 ~3 ~3 ~3 minecraft:air', 800);
  await bot.equip(item, 'hand');
  await c('/tp @s ~ ~ ~'); await sleep(500);
  const under = bot.blockAt(bot.entity.position.offset(0, -0.5, 0).floored());
  const base = bot.entity.position.floored(); const ground = bot.blockAt(base.offset(2, -1, 2));
  console.log('placing on', ground.name, ground.position.toString());
  try { await bot.placeBlock(ground, new Vec3(0, 1, 0)); } catch (e) { console.log('placeBlock threw:', e.message); }
  await sleep(1000);
  const above = bot.blockAt(ground.position.offset(0, 1, 0));
  console.log('block above ground now:', above.name);
  const srv = await new Promise(r => { const n = lines.length; bot.chat('/execute if block ' + above.position.x + ' ' + above.position.y + ' ' + above.position.z + ' emberfall:ember_hearth'); setTimeout(() => r(lines.slice(n).join(' | ')), 900); });
  console.log('SERVER says hearth present?:', srv);
  console.log('chat:', lines.slice(-3).join(' | '));
  bot.quit(); setTimeout(() => process.exit(0), 500);
});
bot.on('error', e => console.log('ERR', e.message));
