const mineflayer = require('mineflayer');
const bot = mineflayer.createBot({ host: '127.0.0.1', port: 25565, username: 'EmberTester', version: '1.21.11', auth: 'offline' });
const sleep = ms => new Promise(r => setTimeout(r, ms));
bot.on('error', e => console.log('ERROR', e));
const names = {}; let shown = 0;
bot._client.on('packet', (d, m) => {
  if (m.name.includes('particle')) { names[m.name] = (names[m.name] || 0) + 1; if (shown < 3) { shown++; console.log(m.name, JSON.stringify(d).slice(0, 260)); } }
});
bot.once('spawn', async () => {
  await sleep(5000);
  const c = async (x, w = 700) => { bot.chat(x); await sleep(w); };
  await c('/gamemode survival', 700);
  await c('/emberfall selectweapon EmberTester war_halberd', 600);
  await c('/summon emberfall:horde_zombie 42.5 72 40.5 {Tags:["t1"],Silent:1b,PersistenceRequired:1b}', 500);
  await c('/attribute @e[tag=t1,limit=1] minecraft:movement_speed base set 0', 400);
  await c('/attribute @e[tag=t1,limit=1] minecraft:max_health base set 5000', 400);
  await c('/data merge entity @e[tag=t1,limit=1] {Health:5000f}', 400);
  await c('/tp @s 40.5 72 40.5', 500);
  await sleep(9000);
  console.log('particle packet names:', JSON.stringify(names));
  await c('/kill @e[tag=t1]', 400);
  bot.quit(); process.exit(0);
});
