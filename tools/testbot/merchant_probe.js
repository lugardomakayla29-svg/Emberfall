const mineflayer = require('mineflayer'); const sleep = ms => new Promise(r => setTimeout(r, ms));
const bot = mineflayer.createBot({ host: '127.0.0.1', port: 25565, username: 'EmberTester', version: '1.21.11', auth: 'offline' });
const lines = []; bot.on('message', m => lines.push(m.toString()));
const ask = async (x, w = 600) => { const n = lines.length; bot.chat(x); await sleep(w); return lines.slice(n).join(' | '); };
bot.once('spawn', async () => {
  await sleep(6500); await ask('/gamemode survival'); await ask('/character select juggernaut', 600); await ask('/expedition', 1500);
  for (let i = 0; i < 60; i++) { await sleep(2000); if (/expedition/.test(await ask('/data get entity @s Dimension', 400))) break; }
  await sleep(1500); await ask('/emberfall wavestop 0', 500);
  console.log('ARR', await ask('/emberfall relic merchant EmberTester 0', 1500));
  console.log('POS', await ask('/data get entity @e[type=emberfall:testificate,limit=1] Pos', 600));
  await sleep(1500);
  console.log('ENTS', Object.values(bot.entities).filter(e => e.type !== 'player').map(e => `${e.name}/${e.type}/${e.id}@${e.position.x.toFixed(0)},${e.position.z.toFixed(0)}`).slice(0, 12).join(' '));
  console.log('ME', bot.entity.position.toString());
  bot.quit(); setTimeout(() => process.exit(0), 300);
});
