const mineflayer = require('mineflayer');
const bot = mineflayer.createBot({ host: '127.0.0.1', port: 25565, username: 'EmberTester', version: '1.21.11', auth: 'offline' });
const sleep = ms => new Promise(r => setTimeout(r, ms));
bot.on('error', e => console.log('ERROR', e));
bot.on('kicked', r => console.log('KICKED', JSON.stringify(r)));
bot.on('message', m => { const t = m.toString(); if (t.trim()) console.log('CHAT', t); });
const c = async (x, w=900) => { bot.chat(x); await sleep(w); };
const chars = ['vanguard','duelist','juggernaut','ranger','battlemage','gravedigger','reaper','emberwarden'];
bot.once('spawn', async () => {
  await sleep(6000);
  await c('/gamemode creative', 500);
  await c('/character list', 1500);
  for (const id of chars) {
    console.log('=== ' + id);
    await c('/character select ' + id, 700);
    await c('/expedition leave', 700);
    await c('/expedition', 3500);
    await c('/data get entity @s SelectedItem.id', 700);
    await c('/attribute @s minecraft:max_health get', 500);
    await c('/attribute @s minecraft:entity_interaction_range get', 500);
    await c('/attribute @s minecraft:armor get', 500);
    await c('/attribute @s minecraft:knockback_resistance get', 500);
    await c('/expedition leave', 1200);
  }
  console.log('FINISHED');
  bot.quit(); process.exit(0);
});
