// The flag must still be consumed by the next offer roll inside the SAME run (feature intact).
const mineflayer = require('mineflayer');
const bot = mineflayer.createBot({ host: '127.0.0.1', port: 25565, username: 'EmberTester', version: '1.21.11', auth: 'offline' });
const sleep = ms => new Promise(r => setTimeout(r, ms));
const lines = []; bot.on('message', m => lines.push(m.toString()));
bot.once('spawn', async () => {
  await sleep(6000);
  const c = async (x, w = 700) => { bot.chat(x); await sleep(w); };
  const ask = async (x, w = 800) => { const n = lines.length; bot.chat(x); await sleep(w); return lines.slice(n).join(' | '); };
  const flag = async () => (await ask('/emberfall guaranteeflag EmberTester')).match(/GUARANTEEFLAG (true|false)/)?.[1] ?? 'UNREADABLE';
  await c('/gamemode survival'); await c('/character select juggernaut');
  await c('/expedition leave', 800); await c('/expedition', 4000); await sleep(1500);
  await ask('/emberfall debugguarantee EmberTester');
  const before = await flag();
  const roll = await ask('/emberfall rolloffers EmberTester');
  const after = await flag();
  console.log('U1 set             :', before, '(expect true)');
  console.log('U2 after one roll  :', after, '(expect false: consumed by the roll)', roll.slice(0, 90));
  await c('/expedition leave', 1000);
  bot.quit(); setTimeout(() => process.exit(0), 500);
});
