const mineflayer = require('mineflayer');
const bot = mineflayer.createBot({ host: '127.0.0.1', port: 25565, username: 'EmberTester', version: '1.21.11', auth: 'offline' });
const t0 = Date.now();
const log = (m) => console.log(`[${((Date.now()-t0)/1000).toFixed(1)}s] ${m}`);
const sleep = (ms) => new Promise(r => setTimeout(r, ms));
async function c(x,w=300){ bot.chat(x); await sleep(w); }
const kind = process.argv[2] || 'horde_spider';
let dmg = 0, last = 20;
bot.on('health', () => { if (bot.health < last) dmg++; last = bot.health; });
let spiderPos = null;
bot.on('entitySpawn', (e) => {});
bot.once('spawn', async () => {
  await sleep(1500);
  await c('/gamemode survival');
  await c('/effect clear @s');
  await c('/execute in emberfall:expedition run tp EmberTester 0.5 100 0.5', 500);
  await c('/kill @e[type=!player,distance=..80]', 300);
  await c(`/execute in emberfall:expedition positioned 0 100 10 run ${kind === "broodmother_stalker" ? "emberfall spawnelite broodmother_stalker" : "summon emberfall:" + kind}`, 600);
  if (process.argv[3]) { await c(`/execute in emberfall:expedition run attribute @e[type=emberfall:${kind},limit=1] minecraft:scale base set ${process.argv[3]}`, 300); }
  last = bot.health;
  const samples = [];
  for (let i=0;i<40;i++){
    const others = Object.values(bot.entities).filter(e => e !== bot.entity && e.position && e.position.distanceTo(bot.entity.position) < 40);
    if (i===0) log('nearby entities: ' + JSON.stringify(others.map(e=>[e.name,e.type,e.entityType,e.position.distanceTo(bot.entity.position).toFixed(1)])));
    const sp = others.find(e => e.entityType !== undefined && e.type !== 'player');
    samples.push(sp ? sp.position.distanceTo(bot.entity.position).toFixed(1) : 'n/a');
    await sleep(250);
  }
  log(`distance to ${kind} each second: ${samples.join(', ')}`);
  log(`bite damage events in 10s: ${dmg}`);
  await c('/kill @e[type=!player,distance=..80]', 300);
  bot.quit(); await sleep(300); process.exit(0);
});
setTimeout(()=>process.exit(1), 40000);
