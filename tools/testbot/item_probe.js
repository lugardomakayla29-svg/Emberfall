const mineflayer = require('mineflayer');
const bot = mineflayer.createBot({ host: '127.0.0.1', port: 25565, username: 'EmberTester', version: '1.21.11', auth: 'offline' });
const sleep = ms => new Promise(r => setTimeout(r, ms));
const lines = []; bot.on('message', m => lines.push(m.toString()));
bot.once('spawn', async () => {
  await sleep(3500);
  bot.chat('/clear @s'); await sleep(500);
  bot.chat('/give @s emberfall:ember_hearth 1'); await sleep(800);
  const n = lines.length;
  bot.chat('/execute if items entity @s container.0 emberfall:ember_hearth[minecraft:profile~{name:"ember_hearth"}]'); await sleep(900);
  console.log(lines.slice(n).join('\n').slice(0, 900));
  bot.quit(); setTimeout(() => process.exit(0), 400);
});
