// Session 1: open one Rift and leave it open, then the wrapper kills the server with -9 (no clean shutdown).
const mineflayer = require('mineflayer');
const bot = mineflayer.createBot({ host: '127.0.0.1', port: 25565, username: 'EmberTester', version: '1.21.11', auth: 'offline' });
const sleep = ms => new Promise(r => setTimeout(r, ms));
const lines = []; bot.on('message', m => lines.push(m.toString())); bot.on('error', e => console.log('ERR', e.message));
const ask = async (x, w = 600) => { const n = lines.length; bot.chat(x); await sleep(w); return lines.slice(n).join(' | '); };
bot.once('spawn', async () => {
  await sleep(4000);
  await ask('/gamemode creative', 400); await ask('/emberfall rift clear', 200); await ask('/emberfall rift closeall', 200);
  await ask('/fill 94 199 94 106 199 106 minecraft:stone', 900); await ask('/fill 94 200 94 106 235 106 minecraft:air', 900);
  await ask('/tp @s 100 200 100', 800); await sleep(1500);
  console.log('OPEN', JSON.stringify((await ask('/emberfall rift open', 900)).slice(0, 60))); await sleep(2000);
  console.log('S1 forced', JSON.stringify((await ask('/forceload query 100 100', 600)).slice(0, 80)));
  console.log('S1 target', JSON.stringify((await ask('/execute if entity @e[type=minecraft:interaction,tag=emberfall_rift_target]', 600)).slice(0, 60)));
  await ask('/save-all flush', 3000);
  console.log('S1 saved');
  process.exit(0);
});
