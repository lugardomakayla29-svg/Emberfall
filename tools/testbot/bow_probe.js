// Does the Hunting Bow damage a frozen 1000 hp zombie at a given distance? Prints hp over time. Loadout: halberd + bow.
const mineflayer = require('mineflayer');
const bot = mineflayer.createBot({ host: '127.0.0.1', port: 25565, username: 'EmberTester', version: '1.21.11', auth: 'offline' });
const sleep = ms => new Promise(r => setTimeout(r, ms));
const lines = []; bot.on('message', m => lines.push(m.toString())); bot.on('error', e => console.log('ERR', e.message));
const ask = async (x, w = 500) => { const n = lines.length; bot.chat(x); await sleep(w); return lines.slice(n).join(' | '); };
const DIST = process.argv[2] || '7';
const hp = async tag => { const r = await ask(`/data get entity @e[tag=${tag},limit=1] Health`, 500); const m = /entity data: (-?[\d.]+)f/.exec(r); return m ? +m[1] : null; };
bot.once('spawn', async () => {
  await sleep(5000);
  await ask('/gamemode survival'); await ask('/effect clear @s');
  await ask('/character select juggernaut'); await ask('/expedition', 2500);
  for (let i = 0; i < 40; i++) { await sleep(2000); if (/expedition/.test(await ask('/data get entity @s Dimension', 400))) break; }
  await sleep(2000);
  await ask('/effect give @s minecraft:resistance 999 4 true', 200); await ask('/effect give @s minecraft:regeneration 999 4 true', 200);
  await ask('/emberfall wavestop 0', 300); await ask('/time set midnight', 200);
  await ask('/kill @e[type=!player,type=!minecraft:item_display,type=!minecraft:interaction]', 900);
  console.log('loadout:', (await ask('/emberfall debugloadout EmberTester', 500)).slice(-120));
  await ask('/emberfall selectweapon EmberTester hunting_bow', 700);
  console.log('loadout:', (await ask('/emberfall debugloadout EmberTester', 500)).slice(-120));
  await ask(`/execute at @s run summon emberfall:horde_zombie ~${DIST} ~ ~0 {Tags:["p1","keep"],NoAI:1b,PersistenceRequired:1b}`, 500);
  await ask('/attribute @e[tag=p1,limit=1] minecraft:max_health base set 1000', 200);
  await ask('/data modify entity @e[tag=p1,limit=1] Health set value 1000.0f', 200);
  const pin = setInterval(() => bot.chat('/tp @s ' + bot.entity.position.x.toFixed(2) + ' ' + bot.entity.position.y.toFixed(2) + ' ' + bot.entity.position.z.toFixed(2)), 1000);
  const out = []; for (let i = 0; i < 8; i++) { out.push(await hp('p1')); await sleep(1500); }
  console.log('hp at dist', DIST, ':', out.join(' '));
  console.log('growth:', (await ask('/emberfall debugweapongrowth EmberTester', 700)).slice(0, 300));
  clearInterval(pin); await ask('/kill @e[tag=keep]', 300); await ask('/expedition leave', 800);
  bot.quit(); setTimeout(() => process.exit(0), 400);
});
