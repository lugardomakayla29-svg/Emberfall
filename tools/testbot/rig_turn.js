const mineflayer = require('mineflayer');
const bot = mineflayer.createBot({ host: '127.0.0.1', port: 25565, username: 'EmberTester', version: '1.21.11', auth: 'offline' });
const sleep = ms => new Promise(r => setTimeout(r, ms));
bot.on('error', e => console.log('ERROR', e));
const c = async (x, w=700) => { bot.chat(x); await sleep(w); };
bot.once('spawn', async () => {
  await sleep(6000);
  await c('/gamemode creative'); await c('/tp @s 0 200 0', 1500);
  await c('/kill @e[type=!player,distance=..40]', 600);
  await c('/emberfall spawnelite broodmother_stalker', 1500);
  await c('/attribute @e[type=emberfall:broodmother_stalker,limit=1] minecraft:movement_speed base set 0', 300);
  // Rotate the spider to 4 headings and check the head stays at the same BODY-FRAME offset each time.
  for (const yaw of [0, 90, 180, 270]) {
    await c(`/tp @e[type=emberfall:broodmother_stalker,limit=1] 0 200 0 ${yaw} 0`, 900);
    await c('/emberfall rigreport', 300);
  }
  bot.quit(); process.exit(0);
});
