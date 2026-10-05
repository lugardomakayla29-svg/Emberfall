const mineflayer = require('mineflayer');
const bot = mineflayer.createBot({ host: '127.0.0.1', port: 25565, username: 'EmberTester', version: '1.21.11', auth: 'offline' });
const sleep = (ms) => new Promise(r => setTimeout(r, ms));
const kind = process.argv[2];
let out = [];
bot.on('message', (m) => { const t=m.toString(); if(/has the following|scoreboard|Set \[/.test(t)) out.push(t); });
async function c(x,w=300){ bot.chat(x); await sleep(w); }
bot.once('spawn', async () => {
  await sleep(1500);
  await c('/gamemode survival');
  await c('/effect clear @s');
  await c('/scoreboard objectives remove bites');
  await c('/scoreboard objectives add bites minecraft.custom:minecraft.damage_taken');
  await c('/execute in emberfall:expedition run tp EmberTester 0.5 100 0.5', 500);
  await c('/kill @e[type=!player,distance=..80]', 300);
  await c('/scoreboard players set EmberTester bites 0');
  await c(`/execute in emberfall:expedition positioned 0 100 10 run ${kind === "broodmother_stalker" ? "emberfall spawnelite broodmother_stalker" : "summon emberfall:" + kind}`, 600);
  await sleep(10000);
  await c('/scoreboard players get EmberTester bites', 400);
  console.log(kind, '->', JSON.stringify(out));
  await c('/kill @e[type=!player,distance=..80]', 300);
  bot.quit(); await sleep(300); process.exit(0);
});
setTimeout(()=>process.exit(1), 40000);
