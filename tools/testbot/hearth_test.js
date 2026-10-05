const mineflayer = require('mineflayer');
const { Vec3 } = require('vec3');
const bot = mineflayer.createBot({ host: '127.0.0.1', port: 25565, username: 'EmberTester', version: '1.21.11', auth: 'offline' });
const sleep = ms => new Promise(r => setTimeout(r, ms));
const chat = [];
bot.on('error', e => console.log('ERROR', e));
bot.on('kicked', r => console.log('KICKED', JSON.stringify(r)));
bot.on('message', m => { const t = m.toString(); if (t.trim()) { chat.push(t); console.log('CHAT', t); } });
const c = async (x, w = 900) => { bot.chat(x); await sleep(w); };
const X = -29, Y = 75, Z = -2;
const TAG = `emberfall_hub_${X}_${Y + 1}_${Z}`;  // matches HubBuilder.tagFor(hearthPos)
// server-side count of tagged entities near the hearth
const count = async (label) => {
  chat.length = 0;
  await c(`/execute if entity @e[tag=${TAG},distance=..20]`, 700);
  const t = chat.join(' ');
  console.log(`COUNT ${label}: ${t}`);
};
const blockIs = async (label, dx, dy, dz, id) => {
  chat.length = 0;
  await c(`/execute if block ${X + dx} ${Y + dy} ${Z + dz} ${id}`, 600);
  console.log(`BLOCK ${label} (${dx},${dy},${dz}) ${id}: ${chat.join(' ')}`);
};
bot.once('spawn', async () => {
  await sleep(6000);
  await c('/gamemode creative', 500);
  await c(`/tp @s ${X + 0.5} ${Y + 1} ${Z + 6.5}`, 2500);   // let chunks load
  await c('/gamemode survival', 500);
  console.log('=== baseline');
  await blockIs('ground under hearth', 0, 0, 0, 'minecraft:grass_block');
  await count('before', 0);
  console.log('=== place hearth + use it');
  await c(`/setblock ${X} ${Y + 1} ${Z} emberfall:ember_hearth`, 800);
  await blockIs('hearth placed', 0, 1, 0, 'emberfall:ember_hearth');
  await c(`/emberfall hubactivate ${X} ${Y + 1} ${Z}`, 2500);
  await count('after use');
  console.log('=== pedestal + plate blocks');
  await blockIs('bust N pedestal', 0, 1, -3, 'minecraft:polished_blackstone');
  await blockIs('bust E pedestal', 3, 1, 0, 'minecraft:polished_blackstone');
  await blockIs('keeper pedestal', 0, 1, 1, 'minecraft:gilded_blackstone');
  await blockIs('departure plate', 0, 1, -1, 'minecraft:polished_blackstone_pressure_plate');
  console.log('=== use again (must not double-build)');
  await c(`/emberfall hubactivate ${X} ${Y + 1} ${Z}`, 1500);
  await count('after 2nd use');
  console.log('=== break hearth');
  await c('/gamemode creative', 500);
  await c(`/setblock ${X} ${Y + 1} ${Z} minecraft:air`, 2000);
  await count('after break');
  await blockIs('pedestal gone', 0, 1, -3, 'minecraft:air');
  await blockIs('plate gone', 0, 1, -1, 'minecraft:air');
  await blockIs('hearth NOT resurrected', 0, 1, 0, 'minecraft:air');
  await blockIs('ground intact', 0, 0, 0, 'minecraft:grass_block');
  console.log('FINISHED');
  bot.quit(); process.exit(0);
});
