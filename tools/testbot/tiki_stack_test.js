const mineflayer = require('mineflayer');
const bot = mineflayer.createBot({ host: '127.0.0.1', port: 25565, username: 'EmberTester', version: '1.21.11', auth: 'offline' });
const sleep = ms => new Promise(r => setTimeout(r, ms));
const lines = []; bot.on('message', m => lines.push(m.toString()));
const ask = async (x, w = 800) => { const n = lines.length; bot.chat(x); await sleep(w); return lines.slice(n).join(' | ').replace(/.*data: /, ''); };
const ys = s => { const m = /\[[^,]+, ([\d.]+)d,/.exec(s); return m ? parseFloat(m[1]) : NaN; };
bot.once('spawn', async () => {
  await sleep(6500);
  await ask('/gamemode creative');
  await ask('/kill @e[type=!player]', 600); await sleep(2000);
  await ask('/emberfall spawnveteran tiki_magma', 1200);
  for (let i = 0; i < 6; i++) {
    const m = ys(await ask('/data get entity @e[type=emberfall:tiki_magma,limit=1] Pos', 600));
    const c = ys(await ask('/data get entity @e[type=emberfall:tiki_cube,limit=1] Pos', 600));
    const g = await ask('/data get entity @e[type=emberfall:tiki_magma,limit=1] OnGround', 500);
    console.log(`mob y=${m.toFixed(3)} cube y=${c.toFixed(3)} gap=${(c - m).toFixed(3)} ground=${g.trim()}`);
    await sleep(700);
  }
  bot.quit(); setTimeout(() => process.exit(0), 300);
});
bot.on('error', e => console.log('ERR', e.message));
