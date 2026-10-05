// Two weapons in slots 0 and 1. After lots of swings, is the main hand still slot 0's weapon, and did the hotbar stay clean?
const mineflayer = require('mineflayer');
const bot = mineflayer.createBot({ host: '127.0.0.1', port: 25565, username: 'EmberTester', version: '1.21.11', auth: 'offline' });
const sleep = ms => new Promise(r => setTimeout(r, ms));
const lines = []; bot.on('message', m => lines.push(m.toString()));
bot.once('spawn', async () => {
  await sleep(6000);
  const c = async (x, w = 700) => { bot.chat(x); await sleep(w); };
  const ask = async (x, w = 800) => { const n = lines.length; bot.chat(x); await sleep(w); return lines.slice(n).join(' | '); };
  const hand = async () => (await ask('/data get entity EmberTester SelectedItem')).replace(/\s+/g, ' ').slice(0, 130);
  const inv = async () => (await ask('/data get entity EmberTester Inventory', 900)).match(/id: "minecraft:[a-z_]+"/g)?.join(',') ?? 'NONE';
  await c('/gamemode survival'); await c('/effect give EmberTester minecraft:resistance 999 4 true'); await c('/effect give EmberTester minecraft:regeneration 999 4 true');
  await c('/character select juggernaut'); await c('/expedition leave', 800); await c('/expedition', 4000); await sleep(1500);
  console.log('H0 loadout       :', (await ask('/emberfall debugloadout EmberTester')).replace(/\s+/g, ' ').slice(0, 110));
  await c('/emberfall givecurrency EmberTester 5000', 700);
  await c('/emberfall shopbuy EmberTester upgrade slot_weapon', 900);
  await c('/emberfall buyweapon EmberTester hunting_bow', 900);
  await c('/emberfall weaponoffer EmberTester', 1200);
  await c('/emberfall weaponanswer EmberTester hunting_bow', 1200);
  console.log('H1 loadout       :', (await ask('/emberfall debugloadout EmberTester')).replace(/\s+/g, ' ').slice(0, 130));
  console.log('H1 hand before   :', await hand());
  // a hostile to swing at, held near the player
  for (let i = 0; i < 6; i++) { await c('/execute at EmberTester run summon emberfall:horde_zombie ~3 ~ ~ {Attributes:[{id:"minecraft:max_health",base:600}]}', 300); }
  await sleep(12000);
  console.log('H2 hand after    :', await hand());
  console.log('H2 inventory ids :', await inv());
  await c('/kill @e[type=emberfall:horde_zombie]', 400);
  await c('/expedition leave', 1200);
  console.log('H3 hand after leave:', await hand());
  console.log('H3 inventory ids  :', await inv());
  bot.quit(); setTimeout(() => process.exit(0), 500);
});
