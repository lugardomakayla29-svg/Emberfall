const mineflayer = require('mineflayer');
const sleep = ms => new Promise(r => setTimeout(r, ms));
const bot = mineflayer.createBot({ host: '127.0.0.1', port: 25565, username: 'EmberTester', version: '1.21.11', auth: 'offline' });
const chat = []; bot.on('message', m => chat.push(m.toString())); bot.on('error', e => console.log('ERR', e.message));
const ask = async (c, w = 350) => { chat.length = 0; bot.chat(c); await sleep(w); return chat.join(' | '); };
const q = async c => /passed/i.test(await ask(c, 300));
bot.once('spawn', async () => {
  await sleep(5000); await ask('/op EmberTester'); await ask('/gamemode creative'); await ask('/effect give @s resistance 600 4 true', 400);
  await ask('/tp @s 0 80 0', 500); await ask('/kill @e[type=!player]', 600);
  await ask('/emberfall spawnelite bonecaller_necromancer', 1500);
  let prev = null; const t0 = Date.now(); const ev = [];
  for (let i = 0; i < 70; i++) {
    const mounted = await q('/execute if entity @e[type=minecraft:zombie_horse,nbt={Passengers:[{}]}]');
    const horses = (await ask('/execute store result score #c x if entity @e[type=minecraft:zombie_horse]', 300), null);
    const foals = await q('/execute if entity @e[type=minecraft:zombie_horse,nbt={Age:-1}]');
    const t = ((Date.now() - t0) / 1000).toFixed(1);
    if (prev === null || mounted !== prev) ev.push(`${t}s mounted=${mounted} foalAlive=${foals}`);
    prev = mounted; await sleep(100);
  }
  console.log(ev.join('\n'));
  bot.quit(); setTimeout(() => process.exit(0), 300);
});
