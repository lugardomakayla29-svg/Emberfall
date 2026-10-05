// How fast does this bot really walk? Flat stone slab, walk 8 blocks with forward only, timing the first 4 blocks.
const mineflayer = require('mineflayer');
const bot = mineflayer.createBot({ host: '127.0.0.1', port: 25565, username: 'EmberTester', version: '1.21.11', auth: 'offline' });
const sleep = ms => new Promise(r => setTimeout(r, ms));
const lines = []; bot.on('message', m => lines.push(m.toString()));
const ask = async (x, w = 500) => { const n = lines.length; bot.chat(x); await sleep(w); return lines.slice(n).join(' | '); };
bot.once('spawn', async () => {
  await sleep(6500);
  await ask('/gamemode survival'); await ask('/effect give @s minecraft:resistance 999 4 true', 250);
  const p = bot.entity.position; const x = Math.floor(p.x), y = Math.floor(p.y) - 1, z = Math.floor(p.z);
  await ask(`/fill ${x - 2} ${y} ${z - 2} ${x + 20} ${y} ${z + 2} minecraft:stone`, 800);
  await ask(`/fill ${x - 2} ${y + 1} ${z - 2} ${x + 20} ${y + 4} ${z + 2} minecraft:air`, 800);
  for (const sprint of [false, true]) {
    await ask(`/tp EmberTester ${x + 0.5} ${y + 1} ${z + 0.5}`, 700);
    await bot.lookAt(bot.entity.position.offset(10, 0, 0), true);
    bot.setControlState('sprint', sprint);
    const start = bot.entity.position.clone(); const t0 = Date.now(); let t4 = null;
    bot.setControlState('forward', true);
    while (Date.now() - t0 < 4000) { await sleep(20); if (t4 === null && bot.entity.position.distanceTo(start) >= 4) t4 = Date.now() - t0; }
    bot.setControlState('forward', false);
    const d = bot.entity.position.distanceTo(start);
    console.log(`sprint=${sprint}: 4 blocks in ${t4 === null ? 'never' : t4 + ' ms'}; total ${d.toFixed(2)} blocks in 4 s = ${(d / 4).toFixed(2)} blocks/s`);
  }
  bot.quit(); setTimeout(() => process.exit(0), 300);
});
bot.on('error', e => console.log('ERR', e.message));
