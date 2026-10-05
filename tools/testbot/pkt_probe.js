const mineflayer = require('mineflayer');
const bot = mineflayer.createBot({ host: '127.0.0.1', port: 25565, username: 'EmberTester', version: '1.21.11', auth: 'offline' });
const sleep = ms => new Promise(r => setTimeout(r, ms));
let n = 0;
bot._client.on('packet', (d, m) => { if (m.name === 'world_particles' && n < 2) { n++; console.log('PKT', JSON.stringify(d).slice(0, 400)); } });
bot.once('spawn', async () => {
  await sleep(5000);
  bot.chat('/gamemode creative'); await sleep(600);
  bot.chat('/particle minecraft:dust{color:[1.0,0.0,0.0],scale:1.0} ~ ~1 ~ 0 0 0 0 3 force'); await sleep(1500);
  bot.quit(); process.exit(0);
});
