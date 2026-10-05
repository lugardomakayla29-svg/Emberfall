const mineflayer = require('mineflayer');
const bot = mineflayer.createBot({ host: '127.0.0.1', port: 25565, username: 'EmberTester', version: '1.21.11', auth: 'offline' });
const sleep = ms => new Promise(r => setTimeout(r, ms));
bot.on('error', e => console.log('ERROR', e));
bot.on('message', m => { const t = m.toString(); if (t.includes('HUBSCAN')) console.log(t); });
const c = async (x, w=600) => { bot.chat(x); await sleep(w); };
const spots = [[0,0],[900,0],[-900,0],[0,900],[1800,1800],[-1800,1800],[1800,-1800],[2700,900]];
bot.once('spawn', async () => {
  await sleep(6000);
  await c('/gamemode creative', 500);
  for (const [x, z] of spots) {
    await c(`/tp @s ${x} 200 ${z}`, 6000);     // let chunks generate
    console.log(`--- around ${x},${z}`);
    await c('/emberfall hubscan 60', 8000);
  }
  bot.quit(); process.exit(0);
});
