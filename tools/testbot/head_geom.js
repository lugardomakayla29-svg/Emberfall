const mineflayer = require('mineflayer');
const bot = mineflayer.createBot({ host: '127.0.0.1', port: 25565, username: 'EmberTester', version: '1.21.11', auth: 'offline' });
const sleep = ms => new Promise(r => setTimeout(r, ms));
const lines = []; bot.on('message', m => lines.push(m.toString()));
const ask = async (x, w = 700) => { const n = lines.length; bot.chat(x); await sleep(w); return lines.slice(n).join(' | ').replace(/.*data: /, ''); };
bot.once('spawn', async () => {
  await sleep(6500);
  await ask('/kill @e[type=!player]', 500);
  await ask('/summon minecraft:item_display 20 70 20 {Tags:["hg"],item:{id:"minecraft:player_head",count:1},transformation:{left_rotation:[0f,0f,0f,1f],right_rotation:[0f,0f,0f,1f],translation:[0f,0f,0f],scale:[1f,1f,1f]}}', 600);
  await ask('/summon minecraft:item_display 20 70 20 {Tags:["hg"],item:{id:"minecraft:player_head",count:1},transformation:{left_rotation:[0f,0f,0f,1f],right_rotation:[0f,0f,0f,1f],translation:[0f,0f,0f],scale:[2f,2f,2f]}}', 600);
  console.log('ENTITIES', await ask('/execute if entity @e[tag=hg]', 500));
  console.log('T1', (await ask('/data get entity @e[tag=hg,limit=1,sort=nearest] transformation', 600)).slice(0, 220));
  bot.quit(); setTimeout(() => process.exit(0), 300);
});
bot.on('error', e => console.log('ERR', e.message));
