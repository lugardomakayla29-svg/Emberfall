// Repeats the REAL kill path N times to tell a 60% coin flip from a broken payout. Tier-0 horde zombie: 60% for 1 gold.
const mineflayer = require('mineflayer');
const bot = mineflayer.createBot({ host: '127.0.0.1', port: 25565, username: 'EmberTester', version: '1.21.11', auth: 'offline' });
const sleep = ms => new Promise(r => setTimeout(r, ms));
const lines = []; bot.on('message', m => lines.push(m.toString()));
bot.once('spawn', async () => {
  await sleep(6000);
  const c = async (x, w = 700) => { bot.chat(x); await sleep(w); };
  const ask = async (x, w = 700) => { const n = lines.length; bot.chat(x); await sleep(w); return lines.slice(n).join(' | '); };
  const gold = async () => Number((await ask('/emberfall debuggold EmberTester')).match(/Gold: (\d+)/)?.[1] ?? NaN);
  await c('/gamemode survival'); await c('/effect give EmberTester minecraft:resistance 999 4 true'); await c('/effect give EmberTester minecraft:regeneration 999 4 true');
  await c('/character select juggernaut'); await c('/expedition leave', 800); await c('/expedition', 4000); await sleep(1500);
  const purge = setInterval(() => bot.chat('/kill @e[type=!player,type=!item,tag=!kt]'), 1500); // keep stray mobs from paying us
  await sleep(3000);
  const deltas = [];
  for (let k = 0; k < 12; k++) {
    const before = await gold();
    await c('/execute at @s run summon emberfall:horde_zombie ~2 ~ ~0 {Tags:["kt"],PersistenceRequired:1b}', 600);
    await c('/attribute @e[tag=kt,limit=1] minecraft:movement_speed base set 0', 300);
    await c('/data merge entity @e[tag=kt,limit=1] {Health:1f}', 300);
    const hold = setInterval(() => bot.chat('/execute at @s run tp @e[tag=kt,limit=1] ~2 ~ ~0'), 600);
    let dead = false;
    for (let i = 0; i < 14 && !dead; i++) { await sleep(700); dead = /Test failed/.test(await ask('/execute if entity @e[tag=kt]', 500)); }
    clearInterval(hold);
    await sleep(1500);
    const after = await gold();
    deltas.push(dead ? after - before : 'alive');
    console.log(`kill ${k + 1}: dead=${dead} gold ${before} -> ${after}`);
    await c('/kill @e[tag=kt]', 300);
  }
  clearInterval(purge);
  const paid = deltas.filter(d => typeof d === 'number' && d > 0).length, kills = deltas.filter(d => typeof d === 'number').length;
  console.log(`RESULT: paid ${paid} of ${kills} kills; deltas ${JSON.stringify(deltas)}`);
  bot.quit(); setTimeout(() => process.exit(0), 500);
});
