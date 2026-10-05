const mineflayer = require('mineflayer');
const bot = mineflayer.createBot({ host: '127.0.0.1', port: 25565, username: 'EmberTester', version: '1.21.11', auth: 'offline' });
const sleep = ms => new Promise(r => setTimeout(r, ms));
bot.on('error', e => console.log('ERROR', e));
let last = null;
bot._client.on('packet', (d, m) => { if (m.name === 'sound_effect') last = d.sound.soundId !== undefined ? d.sound.soundId : 'named:' + (d.sound.data && d.sound.data.soundName); });
const names = ['minecraft:entity.husk.ambient','minecraft:entity.blaze.shoot','minecraft:entity.ravager.roar','minecraft:entity.zombie_villager.converted'];
bot.once('spawn', async () => {
  await sleep(5000);
  bot.chat('/gamemode survival'); await sleep(800);
  const out = {};
  for (const n of names) { last = null; bot.chat('/playsound ' + n + ' hostile @s'); await sleep(900); out[n] = last; }
  console.log(JSON.stringify(out));
  bot.quit(); process.exit(0);
});
