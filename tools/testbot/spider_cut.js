// Is the horde spider's missing target caused by the brightness gate? Read the block light at each spider vs. target state.
const mineflayer = require('mineflayer');
const bot = mineflayer.createBot({ host: '127.0.0.1', port: 25565, username: 'EmberTester', version: '1.21.11', auth: 'offline' });
const sleep = ms => new Promise(r => setTimeout(r, ms));
const lines = []; bot.on('message', m => lines.push(m.toString()));
const ask = async (x, w = 500) => { const n = lines.length; bot.chat(x); await sleep(w); return lines.slice(n).join('\n'); };
bot.once('spawn', async () => {
  await sleep(6000);
  await ask('/gamemode survival'); await ask('/effect give @s minecraft:resistance 900 4 true', 200); await ask('/effect give @s minecraft:regeneration 900 4 true', 200);
  await ask('/character select duelist', 700); await ask('/expedition leave', 800); await ask('/expedition', 3500);
  await ask('/kill @e[type=!player]', 700);
  const pin = setInterval(() => bot.chat('/tp @s ' + bot.entity.position.x.toFixed(2) + ' ' + bot.entity.position.y.toFixed(2) + ' ' + bot.entity.position.z.toFixed(2)), 500);
  console.log('daytime:', (await ask('/time query daytime', 500)).replace(/\n/g, ' ').slice(0, 80));
  for (const d of [6, 8, 10, 12, 14, 16]) await ask(`/summon emberfall:horde_spider ~${d} ~ ~`, 200);
  await sleep(2500);
  console.log((await ask('/emberfall debugaggro EmberTester', 900)).split('\n').filter(l => /aggro/.test(l)).map(l => l.replace(/.*aggro/, 'aggro')).join('\n'));
  clearInterval(pin); await ask('/kill @e[type=!player]', 400); await ask('/expedition leave', 800);
  bot.quit(); setTimeout(() => process.exit(0), 300);
});
