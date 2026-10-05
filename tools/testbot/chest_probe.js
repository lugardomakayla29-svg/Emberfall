const mineflayer = require('mineflayer');
const bot = mineflayer.createBot({ host: '127.0.0.1', port: 25565, username: 'EmberTester', version: '1.21.11', auth: 'offline' });
const sleep = ms => new Promise(r => setTimeout(r, ms));
const lines = []; bot.on('message', m => lines.push(m.toString()));
bot.once('spawn', async () => {
  await sleep(6000);
  const c = async (x, w = 600) => { bot.chat(x); await sleep(w); };
  const ask = async (x, w = 800) => { const n = lines.length; bot.chat(x); await sleep(w); return lines.slice(n).join(' | ').slice(0, 300); };
  await c('/gamemode survival'); await c('/character select vanguard', 500);
  await c('/expedition leave', 900); await c('/expedition', 4000);
  for (let i = 0; i < 30; i++) { await sleep(1500); if (/expedition/.test(await ask('/data get entity @s Dimension', 400))) break; } await sleep(2500);
  await c('/emberfall wavestop 0', 300);
  console.log('SET  :', await ask('/emberfall relic gold EmberTester 80'));
  console.log('READ :', await ask('/emberfall relic gold EmberTester 0'));
  console.log('STATE:', await ask('/emberfall relic state EmberTester'));
  const ch = await ask('/emberfall relic chests EmberTester'); console.log('CHEST:', ch);
  const m = ch.match(/nearest=(-?\d+) (-?\d+) (-?\d+)/);
  await c(`/tp @s ${+m[1] + 0.5} ${+m[2]} ${+m[3] + 2.5} 180 0`, 1500);
  const blk = bot.blockAt(new (require('vec3').Vec3)(+m[1], +m[2], +m[3]));
  console.log('BLOCK:', blk && blk.name, blk && JSON.stringify(blk.getProperties ? blk.getProperties() : {}));
  try { await bot.activateBlock(blk); } catch (e) { console.log('ACTIVATE ERR', e.message); }
  await sleep(1200);
  console.log('AFTER lines:', lines.slice(-6).join(' | ').slice(0, 400));
  console.log('STATE2:', await ask('/emberfall relic state EmberTester'));
  await c('/expedition leave', 800); bot.quit(); setTimeout(() => process.exit(0), 400);
});
