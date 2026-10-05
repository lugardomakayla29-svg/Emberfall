const mineflayer = require('mineflayer');
const bot = mineflayer.createBot({ host: '127.0.0.1', port: 25565, username: 'EmberTester', version: '1.21.11', auth: 'offline' });
const sleep = ms => new Promise(r => setTimeout(r, ms));
bot.on('error', e => console.log('ERROR', e));
const mob = () => Object.values(bot.entities).find(e => e !== bot.entity && e.type !== 'player' && e.name !== 'villager' && e.position.distanceTo(bot.entity.position) < 30 && (e.name||'') !== 'item_display');
async function run(label, withVillager, type) {
  bot.chat('/kill @e[tag=vt]'); await sleep(400);
  bot.chat('/tp @s 200 119 215'); await sleep(1500);
  if (withVillager) { bot.chat('/summon minecraft:villager 200 119 192 {Tags:["vt"],Invulnerable:1b,NoAI:1b}'); await sleep(400); }
  bot.chat(`/summon ${type} 200 119 204 {Tags:["vt"],Invulnerable:1b}`); await sleep(700);
  const a = mob(); if (!a) { console.log(label, 'no mob'); return; }
  const id = a.id, s = a.position.clone();
  const samples = [];
  for (let i = 0; i < 6; i++) { await sleep(1000); const m = bot.entities[id]; if (m) samples.push(m.position.z.toFixed(1)); }
  console.log(`${label}: start z=${s.z.toFixed(1)} samples(z)=${samples.join(',')}`);
}
bot.once('spawn', async () => {
  await sleep(4000);
  bot.chat('/gamemode survival'); await sleep(400);
  bot.chat('/effect give @s minecraft:resistance 300 4 true'); await sleep(300);
  bot.chat('/difficulty hard'); await sleep(300);
  await run('colossus, NO villager  ', false, 'emberfall:plague_colossus');
  await run('colossus, WITH villager', true,  'emberfall:plague_colossus');
  await run('horde_zombie, WITH vill', true,  'emberfall:horde_zombie');
  bot.chat('/kill @e[tag=vt]'); await sleep(300); bot.quit(); process.exit(0);
});
