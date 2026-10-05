const mineflayer = require('mineflayer');
const bot = mineflayer.createBot({ host: '127.0.0.1', port: 25565, username: 'EmberTester', version: '1.21.11', auth: 'offline' });
const sleep = ms => new Promise(r => setTimeout(r, ms));
const lines = []; bot.on('message', m => lines.push(m.toString()));
bot.once('spawn', async () => {
  await sleep(6000);
  const ask = async (x, w = 1300) => { const n = lines.length; bot.chat(x); await sleep(w); return lines.slice(n).filter(l => !/Teleported/.test(l)).join(' | ').slice(0, 200); };
  await ask('/gamemode survival'); await ask('/character select juggernaut'); await ask('/expedition leave'); await ask('/expedition', 4000);
  console.log('xp set  :', await ask('/xp set @s 1 levels', 2500));
  for (let i = 0; i < 4; i++) {
    console.log('offers  :', await ask('/emberfall tomeoffers EmberTester'));
    console.log('skip    :', await ask('/emberfall tomeskip EmberTester', 1800));
  }
  await ask('/expedition leave', 1200);
  bot.quit(); setTimeout(() => process.exit(0), 500);
});
