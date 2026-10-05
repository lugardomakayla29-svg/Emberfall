const mineflayer = require('mineflayer');
const sleep = ms => new Promise(r => setTimeout(r, ms));
const bot = mineflayer.createBot({ host: '127.0.0.1', port: 25565, username: 'EmberTester', version: '1.21.11', auth: 'offline' });
const chat = []; bot.on('message', m => chat.push(m.toString())); bot.on('error', e => console.log('ERR', e.message));
const ask = async (c, w = 600) => { chat.length = 0; bot.chat(c); await sleep(w); return chat.join(' | '); };
// the mount is the horse whose Passengers list is non-empty; the sorted-first horse may be a foal decoy
const rid = async () => (await ask('/execute if entity @e[type=minecraft:zombie_horse,nbt={Passengers:[{}]}]', 500)).slice(0, 40);
bot.once('spawn', async () => {
  await sleep(5000); await ask('/op EmberTester'); await ask('/gamemode creative'); await ask('/effect give @s resistance 600 4 true', 400);
  await ask('/tp @s 0 80 0', 500); await ask('/kill @e[type=!player]', 600);
  await ask('/emberfall spawnelite bonecaller_necromancer', 1500);
  let ridden = 0, total = 0;
  for (let i = 0; i < 24; i++) { const r = await rid(); total++; if (/passed/i.test(r)) ridden++; await sleep(1000); }
  console.log('seconds with the rider on a horse:', ridden, 'of', total);
  console.log('necro alive:', (await ask('/execute if entity @e[type=emberfall:bonecaller_necromancer]', 500)).slice(0, 40));
  bot.quit(); setTimeout(() => process.exit(0), 300);
});
