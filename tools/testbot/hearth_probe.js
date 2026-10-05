const mineflayer = require('mineflayer');
const bot = mineflayer.createBot({ host: '127.0.0.1', port: 25565, username: 'EmberProbe', version: '1.21.11', auth: 'offline' });
const sleep = ms => new Promise(r => setTimeout(r, ms));
const chat = [];
bot.on('message', m => { const t = m.toString(); if (t.trim()) chat.push(t); });
const q = async (id, dx, dy, dz) => { chat.length = 0; bot.chat(`/execute if block ${-29+dx} ${75+dy} ${-2+dz} ${id}`); await sleep(1100); const r = chat.join(' '); if (id==='minecraft:air') console.log('   raw air reply:', r); return r.includes('passed'); };
bot.once('spawn', async () => {
  await sleep(5000);
  bot.chat('/gamemode creative'); await sleep(400);
  bot.chat('/forceload add -40 -15 -20 10'); await sleep(1500); bot.chat('/tp @s -28.5 76 4.5'); await sleep(3000);
  for (const [name, dx, dz] of [['bustN',0,-3],['keeper',0,1],['plate',0,-1],['hearthcell',0,0]]) {
    const res = []; chat.length = 0;
    for (const id of ['minecraft:air','#minecraft:replaceable','#minecraft:flowers','#minecraft:small_flowers','#minecraft:replaceable_by_trees','minecraft:polished_blackstone','minecraft:gilded_blackstone','emberfall:ember_hearth'])
      if (await q(id, dx, 1, dz)) res.push(id);
    console.log(name, 'y+1 =', res.join(',') || '(other)');
  }
  bot.quit(); process.exit(0);
});
