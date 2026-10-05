const mineflayer = require('mineflayer');
const bot = mineflayer.createBot({ host: '127.0.0.1', port: 25565, username: 'EmberTester', version: '1.21.11', auth: 'offline' });
const sleep = ms => new Promise(r => setTimeout(r, ms));
const lines = []; bot.on('message', m => lines.push(m.toString()));
const ask = async (x, w = 700) => { const n = lines.length; bot.chat(x); await sleep(w); return lines.slice(n).join(' | '); };
bot.once('spawn', async () => {
  await sleep(6500);
  await ask('/kill @e[type=!player]', 500); await ask('/kill @e[type=minecraft:item_display]', 500); await sleep(2500);
  await ask('/gamemode survival'); await ask('/effect give @s minecraft:resistance 999 4 true', 300);
  await ask('/character select juggernaut', 600); await ask('/expedition', 1500); await sleep(1500);
  await ask('/emberfall bossdevourer 0', 1500);
  for (let i = 0; i < 14; i++) {
    const r = await ask('/emberfall devdump EmberTester', 600);
    console.log(i, r.replace(/DEVDUMP /, '').replace(/ItemDisplay/g, 'D').replace(/DevourerBrain/, 'BRAIN').slice(0, 330));
  }
  bot.quit(); setTimeout(() => process.exit(0), 300);
});
bot.on('error', e => console.log('ERR', e.message));
