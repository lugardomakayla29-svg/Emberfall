const mineflayer = require('mineflayer');
const sleep = ms => new Promise(r => setTimeout(r, ms));
const bot = mineflayer.createBot({ host: '127.0.0.1', port: 25565, username: 'EmberTester', version: '1.21.11', auth: 'offline' });
const chat = []; bot.on('message', m => chat.push(m.toString())); bot.on('error', e => console.log('ERR', e.message));
const ask = async (c, w = 800) => { chat.length = 0; bot.chat(c); await sleep(w); return chat.join(' | '); };
bot.once('spawn', async () => {
  await sleep(5000); await ask('/op EmberTester'); await ask('/gamemode creative'); await ask('/effect give @s resistance 600 4 true', 400);
  await ask('/tp @s 0 80 0', 500); await ask('/kill @e[type=!player]', 600);
  await ask('/emberfall spawnelite bonecaller_necromancer', 1500);
  for (const t of [0, 1500, 4000]) {
    await sleep(t === 0 ? 300 : t);
    console.log('--- t+', t);
    console.log('N passengers/vehicle:', (await ask('/data get entity @e[type=emberfall:bonecaller_necromancer,limit=1] Pos', 600)).slice(-120));
    console.log('horse Passengers:', (await ask('/data get entity @e[type=minecraft:zombie_horse,limit=1] Passengers', 600)).slice(0, 200));
    console.log('horse Pos:', (await ask('/data get entity @e[type=minecraft:zombie_horse,limit=1] Pos', 600)).slice(-120));
    console.log('count horses:', (await ask('/execute if entity @e[type=minecraft:zombie_horse]', 500)).slice(0, 60), '| necros:', (await ask('/execute if entity @e[type=emberfall:bonecaller_necromancer]', 500)).slice(0, 60));
  }
  bot.quit(); setTimeout(() => process.exit(0), 300);
});
