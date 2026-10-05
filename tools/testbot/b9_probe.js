const mineflayer = require('mineflayer');
const bot = mineflayer.createBot({ host: '127.0.0.1', port: 25565, username: 'EmberTester', version: '1.21.11', auth: 'offline' });
const sleep = ms => new Promise(r => setTimeout(r, ms));
const lines = []; bot.on('message', m => lines.push(m.toString()));
const ask = async (x, w = 400) => { const n = lines.length; bot.chat(x); await sleep(w); return lines.slice(n).join(' | '); };
bot.once('spawn', async () => {
  await sleep(4000);
  await ask('/scoreboard objectives add emberfall_t dummy', 300); await ask('/gamemode survival'); await ask('/effect give @s minecraft:resistance 999 4 true'); await ask('/effect give @s minecraft:regeneration 999 4 true');
  await ask('/tp @s 7.5 70 -0.5', 1500); await ask('/character select juggernaut'); await ask('/expedition', 3000);
  await ask('/kill @e[type=!player]', 500);
  const cnt = async sel => { await ask(`/execute store result score #n emberfall_t run execute if entity ${sel}`, 150); const r = await ask('/scoreboard players get #n emberfall_t', 200); const m = /has (-?\d+)/.exec(r); return m ? +m[1] : -1; };
  const spots = [[14, 0], [14, 3], [11, 3], [12, 3], [13, 3], [-14, 0], [0, 14], [0, -14], [20, 0]];
  for (const [dx, dz] of spots) {
    await ask('/kill @e[type=emberfall:tiki_magma]', 300);
    await ask(`/summon emberfall:tiki_magma ${7.5 + dx} 69 ${-0.5 + dz} {Tags:["pb"],Attributes:[{id:"minecraft:movement_speed",base:0.0}]}`, 500);
    await sleep(400);
    console.log(`  offset (${dx},${dz}) -> x ${7.5 + dx}, z ${-0.5 + dz}: tiki alive = ${await cnt('@e[type=emberfall:tiki_magma]')}`);
  }
  await ask('/kill @e[tag=pb]', 300); await ask('/expedition leave', 800);
  bot.quit(); setTimeout(() => process.exit(0), 300);
});
bot.on('error', e => console.log('ERR', e.message));
