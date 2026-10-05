const mineflayer = require('mineflayer');
const bot = mineflayer.createBot({ host: '127.0.0.1', port: 25565, username: 'EmberTester', version: '1.21.11', auth: 'offline' });
const sleep = ms => new Promise(r => setTimeout(r, ms));
bot.on('error', e => console.log('ERROR', e));
let shown = 0;
bot._client.on('packet', (d, m) => {
  if ((m.name === 'sound_effect' || m.name === 'named_sound_effect') && shown < 2) { shown++; console.log(m.name, JSON.stringify(d)); }
});
bot.once('spawn', async () => {
  await sleep(6000);
  const c = async (x, w = 800) => { bot.chat(x); await sleep(w); };
  await c('/gamemode survival', 700);
  await c('/effect give @s minecraft:resistance 900 4 true', 400);
  await c('/tp @s 44 72 40.5', 2000);
  await c('/summon minecraft:zombie 60 72 60', 1500);
  await c('/playsound minecraft:entity.blaze.shoot hostile @s', 1500);
  bot.quit(); process.exit(0);
});
