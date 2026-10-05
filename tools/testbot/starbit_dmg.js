// Damage check: player pinned once 9 blocks from the Magus, NO resistance, regeneration off.
// Reads Health every 250ms and reports the biggest single drop after each "lobbed" moment.
const mineflayer = require('mineflayer');
const bot = mineflayer.createBot({ host: '127.0.0.1', port: 25565, username: 'EmberTester', version: '1.21.11', auth: 'offline' });
const sleep = ms => new Promise(r => setTimeout(r, ms));
const lines = []; bot.on('message', m => lines.push(m.toString()));
const ask = async (x, w = 300) => { const n = lines.length; bot.chat(x); await sleep(w); return lines.slice(n).join(' | '); };
bot.once('spawn', async () => {
  await sleep(6500);
  await ask('/gamemode survival'); await ask('/effect clear @s', 300);
  await ask('/kill @e[type=!player]', 600); await ask('/kill @e[type=minecraft:item_display]', 600); await sleep(1500);
  await ask('/character select juggernaut', 600); await ask('/expedition', 1500); await sleep(1500);
  await ask('/attribute @s minecraft:max_health base set 200', 300); await ask('/effect give @s minecraft:instant_health 1 10 true', 300);
  await ask('/emberfall spawnelite umbral_magus', 900);
  await ask('/execute at @e[type=emberfall:umbral_magus,limit=1] run tp @s ~9 ~ ~', 400);
  let last = bot.health, drops = [];
  for (let i = 0; i < 160; i++) {          // about 40s: 3 to 4 lobs
    await sleep(250);
    const h = bot.health;
    if (h < last - 0.4) drops.push({ t: (i * 0.25).toFixed(1), drop: (last - h).toFixed(1) });
    last = h;
  }
  console.log('health drops (t seconds, hp lost):', JSON.stringify(drops));
  console.log('final health', bot.health);
  bot.quit(); setTimeout(() => process.exit(0), 300);
});
bot.on('error', e => console.log('ERR', e.message));
