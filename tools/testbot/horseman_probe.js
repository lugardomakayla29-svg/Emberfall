// Measures the live Bonecaller horseman: is there a mount, is the rider on it, does the pair move toward a player, does it fight.
const mineflayer = require('mineflayer');
const sleep = ms => new Promise(r => setTimeout(r, ms));
const bot = mineflayer.createBot({ host: '127.0.0.1', port: 25565, username: 'EmberTester', version: '1.21.11', auth: 'offline' });
const chat = []; bot.on('message', m => chat.push(m.toString())); bot.on('error', e => console.log('ERR', e.message));
const ask = async (c, w = 700) => { chat.length = 0; bot.chat(c); await sleep(w); return chat.join(' | '); };
const num = s => { const m = /(-?\d+\.?\d*)d/.exec(s); return m ? parseFloat(m[1]) : NaN; };
const pos = async sel => { const r = await ask(`/data get entity ${sel} Pos`, 700); const m = [...r.matchAll(/(-?\d+\.\d+)d/g)].map(x => parseFloat(x[1])); return m.length >= 3 ? m.slice(0, 3) : null; };
bot.once('spawn', async () => {
  await sleep(5000); await ask('/op EmberTester'); await ask('/gamemode creative');
  await ask('/effect give @s resistance 600 4 true', 500); await ask('/tp @s 0 80 0', 500);
  await ask('/kill @e[type=!player]', 600);
  console.log('SPAWN', (await ask('/emberfall spawnelite bonecaller_necromancer', 1500)).slice(0, 80));
  console.log('RIDE', (await ask('/data get entity @e[type=emberfall:bonecaller_necromancer,limit=1] RootVehicle', 800)).slice(0, 260));
  console.log('HORSE', (await ask('/execute as @e[type=minecraft:zombie_horse] run data get entity @s Tame', 800)).slice(0, 160));
  const n = await ask('/execute store result score #h x if entity @e[type=minecraft:zombie_horse]', 500);
  let a = await pos('@e[type=emberfall:bonecaller_necromancer,limit=1]'); const t0 = Date.now();
  console.log('POS0', a);
  await ask('/tp @s 14 80 0', 500); await sleep(6000);
  let b = await pos('@e[type=emberfall:bonecaller_necromancer,limit=1]');
  console.log('POS6s', b, a && b ? 'moved ' + Math.hypot(b[0] - a[0], b[2] - a[2]).toFixed(1) : '');
  console.log('HP', (await ask('/data get entity @s Health', 600)).slice(0, 80));
  bot.quit(); setTimeout(() => process.exit(0), 300);
});
