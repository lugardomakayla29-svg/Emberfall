const mineflayer = require('mineflayer');
const bot = mineflayer.createBot({ host: '127.0.0.1', port: 25565, username: 'EmberTester', version: '1.21.11', auth: 'offline' });
const t0 = Date.now();
const log = (m) => console.log(`[${((Date.now()-t0)/1000).toFixed(1)}s] ${m}`);
const sleep = (ms) => new Promise(r => setTimeout(r, ms));
bot.on('message', (m) => { const t=m.toString(); if(/Removed effect|doesn't have/.test(t)) log(t); });
async function c(x,w=300){ bot.chat(x); await sleep(w); }
bot.once('spawn', async () => {
  await sleep(1500);
  await c('/gamemode survival');
  await c('/effect give @s minecraft:resistance 999 4 true');
  await c('/effect give @s minecraft:regeneration 999 4 true');
  await c('/execute in emberfall:expedition run tp EmberTester 0.5 100 0.5', 500);
  await c('/kill @e[type=!player,distance=..80]', 300);
  await c('/execute in emberfall:expedition positioned 0 100 14 run emberfall spawnelite broodmother_stalker', 500);
  log('spawned at range 14, polling slowness/fatigue');
  for (let i=0;i<40;i++){
    bot.chat('/effect clear @s minecraft:slowness'); await sleep(150);
    bot.chat('/effect clear @s minecraft:mining_fatigue'); await sleep(150);
  }
  await c('/kill @e[type=!player,distance=..80]', 300);
  bot.quit(); await sleep(300); process.exit(0);
});
setTimeout(()=>process.exit(1), 40000);
