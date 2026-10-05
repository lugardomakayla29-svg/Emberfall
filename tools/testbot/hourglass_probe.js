const mineflayer = require('mineflayer');
const bot = mineflayer.createBot({ host: '127.0.0.1', port: 25565, username: 'EmberTester', version: '1.21.11', auth: 'offline' });
const sleep = ms => new Promise(r => setTimeout(r, ms));
const lines = []; bot.on('message', m => lines.push(m.toString()));
bot.once('spawn', async () => {
  await sleep(6000);
  const c = async (x, w = 600) => { bot.chat(x); await sleep(w); };
  const ask = async (x, w = 700) => { const n = lines.length; bot.chat(x); await sleep(w); return lines.slice(n).join(' | ').slice(0, 260); };
  await c('/gamemode survival'); await c('/character select vanguard', 500);
  await c('/expedition leave', 900); await c('/expedition', 4000); for (let i = 0; i < 30; i++) { await sleep(1500); if (/expedition/.test(await ask('/data get entity @s Dimension', 400))) break; } await sleep(2000);
  await c('/emberfall wavestop 0', 300); await c('/effect give @s minecraft:resistance 999 4 true', 200);
  await c('/emberfall relic give EmberTester hourglass 1', 500);
  await c('/effect clear @s', 200);
  await c('/damage @s 16 minecraft:generic', 400);
  console.log('HP after damage 16 (max 24)', (await ask('/data get entity @s Health', 400)).match(/data: ([\d.]+)f/)?.[1]);
  await c(`/execute at @s run summon emberfall:horde_zombie ~0 ~ ~14 {Tags:["hz"],PersistenceRequired:1b}`, 400);
  for (let k = 0; k < 4; k++) {
    console.log(`t+${k * 1.5}s player:`, (await ask('/data get entity @s Health', 400)).match(/data: ([\d.]+)f/)?.[1], '| state:', (await ask('/emberfall relic state EmberTester', 500)).match(/active=\w+ owned=\{[^}]*\}/)?.[0]);
    console.log('   zombie effects:', await ask('/data get entity @e[tag=hz,limit=1] active_effects', 500));
    console.log('   zombie speed  :', await ask('/attribute @e[tag=hz,limit=1] minecraft:movement_speed get', 500));
    await sleep(600);
  }
  await c('/kill @e[tag=hz]', 300); await c('/expedition leave', 800); bot.quit(); setTimeout(() => process.exit(0), 400);
});
