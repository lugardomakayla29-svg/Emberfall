const mineflayer = require('mineflayer');
const bot = mineflayer.createBot({ host: '127.0.0.1', port: 25565, username: 'EmberTester', version: '1.21.11', auth: 'offline' });
const sleep = ms => new Promise(r => setTimeout(r, ms));
const lines = []; bot.on('message', m => lines.push(m.toString()));
const ask = async (x, w = 900) => { const n = lines.length; bot.chat(x); await sleep(w); const r = lines.slice(n).join(' | '); console.log(`> ${x}\n   ${r.slice(0, 220)}`); return r; };
bot.once('spawn', async () => {
  await sleep(4000);
  await ask('/tp EmberTester 70.5 72 -1.5', 1500); await ask('/gamemode survival'); await sleep(4000); await ask('/data get entity EmberTester OnGround', 600);
  await ask('/character select juggernaut', 1200); await ask('/expedition', 3000);
  await ask('/emberfall arenabox EmberTester', 800);
  bot.quit(); setTimeout(() => process.exit(0), 400);
});
bot.on('error', e => console.log('ERR', e.message));
