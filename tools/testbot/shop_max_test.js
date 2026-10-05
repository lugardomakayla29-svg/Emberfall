const mineflayer = require('mineflayer');
const bot = mineflayer.createBot({ host: '127.0.0.1', port: 25565, username: 'EmberTester', version: '1.21.11', auth: 'offline' });
const sleep = ms => new Promise(r => setTimeout(r, ms)); const lines = []; bot.on('message', m => lines.push(m.toString()));
const ask = async (x, w = 700) => { const n = lines.length; bot.chat(x); await sleep(w); return lines.slice(n).join(' | '); };
bot.once('spawn', async () => {
  await sleep(6000); await ask('/gamemode survival'); await ask('/emberfall givecurrency EmberTester 5000', 700);
  const out = [];
  for (let i = 0; i < 5; i++) out.push(await ask('/emberfall shopbuy EmberTester upgrade slot_weapon', 600));
  out.forEach((r, i) => console.log(i + 1, r.slice(0, 95)));
  const last = out[out.length - 1];
  console.log((/already unlocked/i.test(last) ? 'PASS' : 'FAIL') + ' S8 a fully unlocked slot kind answers');
  bot.quit(); setTimeout(() => process.exit(0), 300);
});
