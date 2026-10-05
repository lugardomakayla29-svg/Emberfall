const mineflayer = require('mineflayer');
const bot = mineflayer.createBot({ host: '127.0.0.1', port: 25565, username: 'EmberTester', version: '1.21.11', auth: 'offline' });
const sleep = ms => new Promise(r => setTimeout(r, ms));
const lines = []; bot.on('message', m => lines.push(m.toString()));
bot.once('spawn', async () => {
  await sleep(6000);
  const ask = async (x, w = 1100) => { const n = lines.length; bot.chat(x); await sleep(w); return lines.slice(n).filter(l => !/Teleported/.test(l)).join(' | ').slice(0, 170); };
  console.log('loadout :', await ask('/emberfall debugloadout EmberTester'));
  console.log('pending :', await ask('/emberfall weaponpending EmberTester'));
  console.log('offer   :', await ask('/emberfall weaponoffer EmberTester'));
  console.log('pending :', await ask('/emberfall weaponpending EmberTester'));
  console.log('answer  :', await ask('/emberfall weaponanswer EmberTester skip'));
  bot.quit(); setTimeout(() => process.exit(0), 500);
});
