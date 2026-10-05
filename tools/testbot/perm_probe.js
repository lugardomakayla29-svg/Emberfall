const mineflayer = require('mineflayer');
const bot = mineflayer.createBot({ host: '127.0.0.1', port: 25565, username: 'PlainPlayer', version: '1.21.11', auth: 'offline' });
const sleep = ms => new Promise(r => setTimeout(r, ms));
const chat = [];
bot.on('message', m => { const t = m.toString(); if (t.trim()) chat.push(t); });
const say = async (cmd, w = 1100) => { chat.length = 0; bot.chat(cmd); await sleep(w); return chat.join(' | ').slice(0, 110); };
bot.once('spawn', async () => {
  await sleep(6000);
  console.log('vanilla /gamemode creative :', await say('/gamemode creative'));
  console.log('vanilla /give (op only)    :', await say('/give @s minecraft:stone'));
  console.log('/emberfall blockat (gated) :', await say('/emberfall blockat 0 70 0'));
  console.log('/op PlainPlayer (owner only):', await say('/op PlainPlayer'));
  bot.quit(); process.exit(0);
});
