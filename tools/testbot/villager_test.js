const mineflayer = require('mineflayer');
const bot = mineflayer.createBot({ host: '127.0.0.1', port: 25565, username: 'EmberTester', version: '1.21.11', auth: 'offline' });
const t0 = Date.now(); const log = m => console.log(`[${((Date.now()-t0)/1000).toFixed(1)}s] ${m}`);
const sleep = ms => new Promise(r => setTimeout(r, ms));
bot.on('error', e => log('ERROR: ' + e));
const say = c => bot.chat(c);
// villagers are 'passive'/'villager'; we read their health via entity metadata length is unreliable, so use /data get
const chatLog = [];
bot.on('message', m => chatLog.push(m.toString()));
async function villagerHealth(tag) {
  chatLog.length = 0;
  say(`/data get entity @e[tag=${tag},limit=1] Health`); await sleep(500);
  const l = chatLog.find(s => /Health/.test(s) || /No entity/.test(s));
  return l || 'no reply';
}
async function trial(label, zombieType) {
  say('/kill @e[tag=vt]'); await sleep(300);
  say('/tp @s 200 120 200'); await sleep(2500);
  say('/fill 190 118 190 210 118 210 minecraft:stone'); await sleep(600);
  say('/fill 190 119 190 210 125 210 minecraft:air'); await sleep(600);
  say('/tp @s 200 119 209'); await sleep(500);
  say('/summon minecraft:villager 200 119 200 {Tags:["vt"],NoAI:0b}'); await sleep(600);
  say(`/summon ${zombieType} 203 119 200 {Tags:["vt"]}`); await sleep(400);
  say('/effect give @e[tag=vt,type=!minecraft:villager] minecraft:resistance 30 4 true'); await sleep(300);
  const h0 = await villagerHealth('vt'); 
  await sleep(12000);
  chatLog.length = 0; say('/data get entity @e[type=minecraft:villager,tag=vt,limit=1] Health'); await sleep(600);
  const h1 = chatLog.find(s => /Health|No entity/.test(s)) || 'no reply';
  log(`${label}: villager after 12s -> ${h1}`);
  say('/kill @e[tag=vt]'); await sleep(400);
}
bot.once('spawn', async () => {
  await sleep(4000);
  say('/gamemode creative'); await sleep(500);
  say('/difficulty hard'); await sleep(500);
  say('/gamerule mob_griefing false'); await sleep(300);
  await trial('CONTROL vanilla zombie ', 'minecraft:zombie');
  await trial('TEST    horde_zombie   ', 'emberfall:horde_zombie');
  await trial('TEST    plague_colossus', 'emberfall:plague_colossus');
  bot.quit(); await sleep(500); process.exit(0);
});
