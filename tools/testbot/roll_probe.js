// Read-only: roll offers 12 times for a player holding NO tome and print each result.
const mineflayer = require('mineflayer');
const bot = mineflayer.createBot({ host: '127.0.0.1', port: 25565, username: 'EmberTester', version: '1.21.11', auth: 'offline' });
const sleep = ms => new Promise(r => setTimeout(r, ms));
const lines = []; bot.on('message', m => lines.push(m.toString()));
bot.once('spawn', async () => {
  await sleep(5000);
  const ask = async (x, w = 700) => { const n = lines.length; bot.chat(x); await sleep(w); return lines.slice(n).join(' | ').slice(0, 200); };
  console.log('loadout:', await ask('/emberfall debugloadout EmberTester'));
  for (let i = 0; i < 12; i++) console.log('roll ' + (i + 1) + ':', await ask('/emberfall rolloffers EmberTester'));
  bot.quit(); setTimeout(() => process.exit(0), 400);
});
