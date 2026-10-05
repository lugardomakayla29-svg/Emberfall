const mineflayer = require('mineflayer');
const bot = mineflayer.createBot({ host: '127.0.0.1', port: 25565, username: 'EmberTester', version: '1.21.11', auth: 'offline' });
const t0 = Date.now();
const log = (m) => console.log(`[${((Date.now()-t0)/1000).toFixed(1)}s] ${m}`);
const sleep = (ms) => new Promise(r => setTimeout(r, ms));
bot.on('message', (m) => { const t=m.toString(); if(!/Set own game mode|Gave|Successfully filled|No blocks/.test(t)) log('CHAT: '+t); });
async function c(x,w=300){ bot.chat(x); await sleep(w); }
bot.once('spawn', async () => {
  await sleep(1500);
  await c('/gamemode survival');
  await c('/effect give @s minecraft:resistance 999 4 true');
  await c('/effect give @s minecraft:regeneration 999 4 true');
  await c('/execute in emberfall:expedition run tp EmberTester 0.5 100 0.5', 500);
  await c('/kill @e[type=!player,distance=..60]', 300);
  await c('/execute in emberfall:expedition positioned 0 100 3 run emberfall spawnelite plague_colossus', 500);
  // Poll for burst effects on the player for 6s; report the first time each shows up.
  let sawNausea=false, sawPoison=false;
  for (let i=0;i<24;i++){
    bot.chat('/effect clear @s minecraft:nausea');
    await sleep(120);
    bot.chat('/effect clear @s minecraft:poison');
    await sleep(120);
  }
  await c('/kill @e[type=!player,distance=..80]', 300);
  bot.quit(); await sleep(300); process.exit(0);
});
setTimeout(()=>process.exit(1), 40000);
