const mineflayer = require('mineflayer');
const bot = mineflayer.createBot({ host: '127.0.0.1', port: 25565, username: 'EmberTester', version: '1.21.11', auth: 'offline' });
const t0 = Date.now();
const log = (m) => console.log(`[${((Date.now()-t0)/1000).toFixed(1)}s] ${m}`);
const sleep = (ms) => new Promise(r => setTimeout(r, ms));
async function c(x,w=300){ bot.chat(x); await sleep(w); }
let hits = [];
let last = 20;
bot.on('health', () => { if (bot.health < last) hits.push([((Date.now()-t0)/1000).toFixed(1), last, bot.health]); last = bot.health; });
bot.once('spawn', async () => {
  await sleep(1500);
  await c('/gamemode survival');
  await c('/effect clear @s');
  await c('/execute in emberfall:expedition run tp EmberTester 0.5 100 0.5', 500);
  await c('/kill @e[type=!player,distance=..80]', 300);
  await c('/execute in emberfall:expedition positioned 0 100 6 run emberfall spawnelite broodmother_stalker', 400);
  await c('/effect give @s minecraft:instant_health 1 10 true', 200); // top up
  last = bot.health;
  log('fighting for 8s at full exposure, start hp=' + bot.health);
  await sleep(8000);
  log('hp events (t, before, after): ' + JSON.stringify(hits.slice(0,8)));
  log('total damage events: ' + hits.length + ', final hp=' + bot.health);
  await c('/kill @e[type=!player,distance=..80]', 300);
  bot.quit(); await sleep(300); process.exit(0);
});
setTimeout(()=>process.exit(1), 30000);
