// Session 2, same world after a crash: the Rift is gone (memory only). The leftover target must be discarded AND the chunk no longer forced.
const mineflayer = require('mineflayer');
const bot = mineflayer.createBot({ host: '127.0.0.1', port: 25565, username: 'EmberTester', version: '1.21.11', auth: 'offline' });
const sleep = ms => new Promise(r => setTimeout(r, ms));
const lines = []; bot.on('message', m => lines.push(m.toString())); bot.on('error', e => console.log('ERR', e.message));
const ask = async (x, w = 600) => { const n = lines.length; bot.chat(x); await sleep(w); return lines.slice(n).join(' | '); };
bot.once('spawn', async () => {
  await sleep(4000);
  await ask('/gamemode creative', 400);
  console.log('S2 rifts', JSON.stringify((await ask('/emberfall rift state', 600)).slice(-70)));
  for (let i = 0; i < 6; i++) {
    console.log('S2 t+' + i * 2 + 's target', JSON.stringify((await ask('/execute if entity @e[type=minecraft:interaction,tag=emberfall_rift_target]', 600)).slice(0, 40)), 'forced', JSON.stringify((await ask('/forceload query 100 100', 600)).slice(0, 70)));
    await sleep(800);
  }
  process.exit(0);
});
