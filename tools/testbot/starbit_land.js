// Holds the player STILL at a fixed spot 9 blocks from the Magus and lets several lobs happen; the server logs
// (STARDBG) where each star landed against where it was aimed, and the player's health shows real damage.
const mineflayer = require('mineflayer');
const bot = mineflayer.createBot({ host: '127.0.0.1', port: 25565, username: 'EmberTester', version: '1.21.11', auth: 'offline' });
const sleep = ms => new Promise(r => setTimeout(r, ms));
const lines = []; bot.on('message', m => lines.push(m.toString()));
const ask = async (x, w = 300) => { const n = lines.length; bot.chat(x); await sleep(w); return lines.slice(n).join(' | '); };
bot.once('spawn', async () => {
  await sleep(6500);
  await ask('/gamemode survival'); await ask('/effect give @s minecraft:regeneration 999 4 true', 300);
  await ask('/kill @e[type=!player]', 600); await ask('/kill @e[type=minecraft:item_display]', 600); await sleep(1500);
  await ask('/character select juggernaut', 600); await ask('/expedition', 1500); await sleep(1500);
  await ask('/emberfall spawnelite umbral_magus', 900);
  await ask('/execute at @e[type=emberfall:umbral_magus,limit=1] run tp @s ~9 ~ ~', 400);   // ONE placement, then hands off
  console.log('placed; waiting for lobs...');
  for (let i = 0; i < 12; i++) { await sleep(5000); }
  bot.quit(); setTimeout(() => process.exit(0), 300);
});
bot.on('error', e => console.log('ERR', e.message));
