const mineflayer = require('mineflayer');
const bot = mineflayer.createBot({ host: '127.0.0.1', port: 25565, username: 'EmberTester', version: '1.21.11', auth: 'offline' });
const t0 = Date.now();
const log = (m) => console.log(`[${((Date.now()-t0)/1000).toFixed(1)}s] ${m}`);
const sleep = (ms) => new Promise(r => setTimeout(r, ms));
bot.on('message', (m) => { const t=m.toString(); if(!/Set own game mode|Applied effect|Gave/.test(t)) log('CHAT: '+t); });
const type = process.argv[2];
const spawnCmd = { plague_colossus:'plague_colossus', boil_ridden_marksman:'boil_ridden_marksman', broodmother_stalker:'broodmother_stalker' }[type];
async function c(x,w=300){ bot.chat(x); await sleep(w); }
bot.once('spawn', async () => {
  await sleep(1500);
  await c('/gamemode survival');
  await c('/effect give @s minecraft:resistance 999 4 true');
  await c('/effect give @s minecraft:regeneration 999 4 true');
  await c('/execute in emberfall:expedition run tp EmberTester 0.5 100 0.5', 500);
  await c('/kill @e[type=!player,distance=..60]', 300);
  await c(`/execute positioned 0 100 8 run emberfall spawnelite ${spawnCmd}`, 400);
  for (let i=0;i<10;i++){
    await c(`/data get entity @e[type=emberfall:${type},limit=1] Pos`, 250);
    await c(`/data get entity @e[type=emberfall:${type},limit=1] Brain`, 100);
    await c(`/execute as @e[type=emberfall:${type},limit=1] at @s run data get entity @e[type=emberfall:${type},limit=1] AbsorptionAmount`, 100);
    await sleep(400);
  }
  await c('/kill @e[type=!player,distance=..80]', 300);
  bot.quit(); await sleep(300); process.exit(0);
});
setTimeout(()=>process.exit(1), 40000);
