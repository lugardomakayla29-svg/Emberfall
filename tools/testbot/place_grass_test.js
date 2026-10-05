// Same as place_test but the target cell holds replaceable plants (short_grass), like most natural ground.
const mineflayer = require('mineflayer'); const { Vec3 } = require('vec3');
const bot = mineflayer.createBot({ host: '127.0.0.1', port: 25565, username: 'EmberTester', version: '1.21.11', auth: 'offline' });
const sleep = ms => new Promise(r => setTimeout(r, ms));
const lines = []; bot.on('message', m => lines.push(m.toString()));
const ask = async (x, w = 900) => { const n = lines.length; bot.chat(x); await sleep(w); return lines.slice(n).join(' | '); };
bot.once('spawn', async () => {
  await sleep(4000);
  await ask('/gamemode survival'); await ask('/clear @s'); await ask('/give @s emberfall:ember_hearth 3', 300);
  for (let i = 0; i < 20 && bot.inventory.items().length === 0; i++) await sleep(250);
  const b = bot.entity.position.floored();
  await ask(`/fill ${b.x+1} ${b.y-1} ${b.z+1} ${b.x+6} ${b.y-1} ${b.z+3} minecraft:grass_block`);
  await ask(`/fill ${b.x+1} ${b.y} ${b.z+1} ${b.x+6} ${b.y+3} ${b.z+3} minecraft:air`);
  const item = bot.inventory.items()[0]; await bot.equip(item, 'hand');
  const cases = { short_grass: [2, 2], tall_grass: [4, 2], plain_air: [6, 2] };
  await ask(`/setblock ${b.x+2} ${b.y} ${b.z+2} minecraft:short_grass`);
  await ask(`/setblock ${b.x+4} ${b.y} ${b.z+2} minecraft:tall_grass[half=lower]`);
  await ask(`/setblock ${b.x+4} ${b.y+1} ${b.z+2} minecraft:tall_grass[half=upper]`);
  for (const [name, [dx, dz]] of Object.entries(cases)) {
    const ground = bot.blockAt(new Vec3(b.x + dx, b.y - 1, b.z + dz));
    let err = 'none';
    try { await bot.placeBlock(ground, new Vec3(0, 1, 0)); } catch (e) { err = e.message.slice(0, 70); }
    await sleep(600);
    const r = await ask(`/execute if block ${b.x + dx} ${b.y} ${b.z + dz} emberfall:ember_hearth`);
    console.log(`${name.padEnd(12)} client-err=${err} | server hearth: ${/passed/.test(r) ? 'YES' : 'NO'}`);
  }
  bot.quit(); setTimeout(() => process.exit(0), 500);
});
bot.on('error', e => console.log('ERR', e.message));
