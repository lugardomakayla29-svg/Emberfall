// At 1 tome slot with the held tome fully stacked, the offer must be EMPTY (nothing takeable).
const mineflayer = require('mineflayer');
const bot = mineflayer.createBot({ host: '127.0.0.1', port: 25565, username: 'EmberTester', version: '1.21.11', auth: 'offline' });
const sleep = ms => new Promise(r => setTimeout(r, ms));
const lines = []; bot.on('message', m => lines.push(m.toString()));
bot.once('spawn', async () => {
  await sleep(6000);
  const c = async (x, w = 800) => { bot.chat(x); await sleep(w); };
  const ask = async (x, w = 1000) => { const n = lines.length; bot.chat(x); await sleep(w); return lines.slice(n).filter(l => !/Teleported/.test(l)).join(' | '); };
  await c('/gamemode survival', 700); await c('/character select juggernaut', 700);
  await c('/expedition leave', 800); await c('/expedition', 4000);
  console.log('state:', (await ask('/emberfall debugloadout EmberTester')).replace(/Loadout:.*?\| held=\S+ \| /, ''));
  for (let i = 1; i <= 5; i++) {
    await c('/emberfall granttome EmberTester ember_touch', 700);
    const r = await ask('/emberfall rolloffers EmberTester');
    console.log('stacks granted', i, '->', r.slice(0, 90));
  }
  console.log('buildinfo:', (await ask('/emberfall buildinfo EmberTester')).slice(0, 160));
  await c('/expedition leave', 1200);
  bot.quit(); setTimeout(() => process.exit(0), 600);
});
