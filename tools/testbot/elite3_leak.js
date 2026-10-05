const mineflayer = require('mineflayer');
const bot = mineflayer.createBot({ host: '127.0.0.1', port: 25565, username: 'EmberTester', version: '1.21.11', auth: 'offline' });
const t0 = Date.now();
const log = (m) => console.log(`[${((Date.now()-t0)/1000).toFixed(1)}s] ${m}`);
const sleep = (ms) => new Promise(r => setTimeout(r, ms));
const type = process.argv[2];
bot.on('message', (m) => { const t=m.toString(); if(/Test passed|Test failed/.test(t)) log('  '+t); });
async function c(x,w=300){ bot.chat(x); await sleep(w); }
async function count(label){ log(label); await c('/execute in emberfall:expedition as @s at @s if entity @e[type=minecraft:item_display,distance=..80]', 400); }
bot.once('spawn', async () => {
  await sleep(1500);
  await c('/gamemode creative');
  await c('/execute in emberfall:expedition run tp EmberTester 0.5 100 0.5', 500);
  await c('/kill @e[type=!player,distance=..80]', 500);
  await count('displays BEFORE spawn (expect failed = 0)');
  await c(`/execute in emberfall:expedition positioned 0 100 10 run emberfall spawnelite ${type}`, 600);
  await count('displays AFTER spawn');
  await c(`/kill @e[type=emberfall:${type}]`, 200);
  await count('displays 0.2s after kill');
  await sleep(2500);
  await count('displays AFTER kill (expect failed = 0)');
  await c('/kill @e[type=!player,distance=..80]', 300);
  bot.quit(); await sleep(300); process.exit(0);
});
setTimeout(()=>process.exit(1), 30000);
