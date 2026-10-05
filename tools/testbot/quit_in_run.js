// Join a run, wait 8 s, quit. The only thing under test is what the server does when a player disconnects mid-run.
const mineflayer = require('mineflayer');
const bot = mineflayer.createBot({ host: '127.0.0.1', port: 25565, username: 'EmberTester', version: '1.21.11', auth: 'offline' });
const sleep = ms => new Promise(r => setTimeout(r, ms));
const lines = []; bot.on('message', m => lines.push(m.toString()));
const ask = async (x, w = 500) => { bot.chat(x); await sleep(w); };
bot.once('spawn', async () => {
  await sleep(6500);
  await ask('/gamemode survival'); await ask('/effect give @s minecraft:resistance 999 4 true', 250);
  await ask('/character select juggernaut', 600); await ask('/expedition', 1500);
  await sleep(8000);
  console.log('quitting mid-run', new Date().toISOString());
  bot.quit(); setTimeout(() => process.exit(0), 300);
});
bot.on('error', e => console.log('ERR', e.message));
