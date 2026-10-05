const mineflayer = require('mineflayer');
const bot = mineflayer.createBot({ host: '127.0.0.1', port: 25565, username: 'EmberTester', version: '1.21.11', auth: 'offline' });
const sleep = ms => new Promise(r => setTimeout(r, ms));
bot.on('error', e => console.log('ERROR', e));
bot.on('message', m => console.log('MSG:', m.toString().slice(0,170)));
const c = async (x, w=700) => { bot.chat(x); await sleep(w); };
bot.once('spawn', async () => {
  await sleep(6000);
  await c('/gamemode creative'); await c('/tp @s 0 200 4', 1500);
  await c('/kill @e[type=!player,distance=..40]', 600);
  await c('/summon emberfall:broodmother_stalker 0 200 0', 300);
  for (const t of [0, 1000, 3000]) { await sleep(t ? t : 100);
    await c('/execute store result score @s dummy run execute if entity @e[type=minecraft:item_display]', 100);
    await c('/execute if entity @e[type=minecraft:item_display]', 400);
    await c('/execute if entity @e[type=emberfall:broodmother_stalker]', 400); console.log('--- after', t, 'ms'); }
  bot.quit(); process.exit(0);
});
