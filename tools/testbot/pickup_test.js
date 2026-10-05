// Usage: node pickup_test.js
// Proves the virtual pickup system: (1) an XP pickup raises the player's level-up progress, (2) a gold pickup
// is collected, (3) gold is wiped when the run ends, (4) a real kill of an Emberfall mob spawns NO vanilla orb.
const mineflayer = require('mineflayer');
const bot = mineflayer.createBot({ host: '127.0.0.1', port: 25565, username: 'EmberTester', version: '1.21.11', auth: 'offline' });
const sleep = ms => new Promise(r => setTimeout(r, ms));
bot.on('error', e => console.log('ERROR', e));
const lines = [];
bot.on('message', m => { const t = m.toString(); lines.push(t); });
const last = () => lines[lines.length - 1] || '';
bot.once('spawn', async () => {
  await sleep(6000);
  const c = async (x, w = 800) => { bot.chat(x); await sleep(w); return last(); };
  const ask = async (x, w = 1200) => { const n = lines.length; bot.chat(x); await sleep(w); return lines.slice(n).join(' | '); };
  await c('/gamemode survival', 700);
  await c('/character select juggernaut', 700);
  await c('/expedition leave', 800);
  await c('/expedition', 4000);
  await c('/effect give @s minecraft:resistance 900 4 true', 400);
  await c('/effect give @s minecraft:regeneration 900 4 true', 400);
  const purge = setInterval(() => bot.chat('/kill @e[type=!player,tag=!keep,distance=..60]'), 2000);
  await sleep(2500);
  const pin = setInterval(() => bot.chat('/tp @s ' + bot.entity.position.x.toFixed(2) + ' ' + bot.entity.position.y.toFixed(2) + ' ' + bot.entity.position.z.toFixed(2)), 1000);

  console.log('T0 gold before      :', await ask('/emberfall debuggold EmberTester'));
  await c('/emberfall debugpickup EmberTester gold 7', 300);
  await sleep(2500);
  console.log('T1 gold after gold7 :', await ask('/emberfall debuggold EmberTester'));

  const xpBefore = await ask('/data get entity EmberTester XpTotal');
  await c('/emberfall debugpickup EmberTester xp 12', 300);
  await sleep(2500);
  const xpAfter = await ask('/data get entity EmberTester XpTotal');
  console.log('T2 xp before/after  :', xpBefore, '=>', xpAfter);

  // A REAL kill: the player's own auto-attack must land the killing blow, so the pickup is attributed to them.
  // Hold one Emberfall mob 2 blocks away at 1 HP and let the halberd finish it. Stop the purge so it survives.
  clearInterval(purge);
  const goldBefore = await ask('/emberfall debuggold EmberTester');
  await c('/execute at @s run summon emberfall:horde_zombie ~2 ~ ~0 {Tags:["kt"],PersistenceRequired:1b}', 700);
  await c('/attribute @e[tag=kt,limit=1] minecraft:movement_speed base set 0', 400);
  await c('/data merge entity @e[tag=kt,limit=1] {Health:1f}', 400);
  const hold = setInterval(() => bot.chat('/execute at @s run tp @e[tag=kt,limit=1] ~2 ~ ~0'), 700);
  let dead = false;
  for (let i = 0; i < 14 && !dead; i++) {
    await sleep(1000);
    const r = await ask('/execute if entity @e[tag=kt]', 700);
    dead = /Test failed/.test(r);
  }
  clearInterval(hold);
  console.log('T3a mob killed by player :', dead);
  await sleep(2500);
  console.log('T3 vanilla orbs          :', await ask('/execute if entity @e[type=minecraft:experience_orb]'));
  console.log('T4 gold before/after kill:', goldBefore.split('|')[0], '=>', (await ask('/emberfall debuggold EmberTester')).split('|')[0]);
  console.log('T4b xp total             :', (await ask('/data get entity EmberTester XpTotal')).split('|')[0]);

  // Run end wipes gold.
  await c('/emberfall debugpickup EmberTester gold 5', 300);
  await sleep(2500);
  console.log('T5 gold before leave:', await ask('/emberfall debuggold EmberTester'));
  await c('/expedition leave', 2000);
  console.log('T6 gold after leave :', await ask('/emberfall debuggold EmberTester'));
  clearInterval(pin);
  bot.quit();
  setTimeout(() => process.exit(0), 800);
});
