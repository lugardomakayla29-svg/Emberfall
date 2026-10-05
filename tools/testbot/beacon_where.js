const mineflayer = require('mineflayer');
const bot = mineflayer.createBot({ host: '127.0.0.1', port: 25565, username: 'EmberTester', version: '1.21.11', auth: 'offline' });
const sleep = ms => new Promise(r => setTimeout(r, ms));
const lines = []; bot.on('message', m => lines.push(m.toString()));
const ask = async (x, w = 500) => { const n = lines.length; bot.chat(x); await sleep(w); return lines.slice(n).join(' | '); };
bot.once('spawn', async () => {
  await sleep(5000);
  await ask('/gamemode survival'); await ask('/character select emberwarden'); await ask('/expedition', 2500);
  for (let i = 0; i < 40; i++) { await sleep(2000); if (/expedition/.test(await ask('/data get entity @s Dimension', 400))) break; }
  await sleep(2000);
  await ask('/effect give @s minecraft:resistance 999 4 true', 200); await ask('/emberfall wavestop 0', 300);
  await ask('/kill @e[type=!player,type=!minecraft:item_display,type=!minecraft:interaction]', 900);
  console.log('PLAYER', (await ask('/data get entity @s Pos', 500)).slice(-70));
  for (const [t, dx, dz] of [['prim', 0, 3.0], ['lone', 2.5, 3.0], ['g1', -1.0, 11.0], ['far', 6.5, 3.0]]) {
    console.log('SUMMON', t, (await ask(`/execute at @s run summon emberfall:horde_zombie ~${dx} ~ ~${dz} {Tags:["${t}","keep"],NoAI:1b,Silent:1b,PersistenceRequired:1b}`, 400)).slice(0, 90));
  }
  await sleep(1500);
  for (const t of ['prim', 'lone', 'g1', 'far']) console.log('POS', t, (await ask(`/data get entity @e[tag=${t},limit=1] Pos`, 450)).slice(-60));
  await ask('/kill @e[tag=keep]', 400); await ask('/expedition leave', 800);
  bot.quit(); setTimeout(() => process.exit(0), 300);
});
