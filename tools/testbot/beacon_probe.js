// Probe: gravechain at level 10, ONE NoAI foe 2.5 ahead. Samples health + position + raw reply every 500 ms for 8 s.
const mineflayer = require('mineflayer');
const bot = mineflayer.createBot({ host: '127.0.0.1', port: 25565, username: 'EmberTester', version: '1.21.11', auth: 'offline' });
const sleep = ms => new Promise(r => setTimeout(r, ms));
const lines = []; bot.on('message', m => lines.push(m.toString())); bot.on('error', e => console.log('ERR', e.message));
const ask = async (x, w = 450) => { const n = lines.length; bot.chat(x); await sleep(w); return lines.slice(n).join(' | '); };
const BIG = 1000000;
bot.once('spawn', async () => {
  await sleep(5000);
  await ask('/gamemode survival'); await ask('/effect clear @s');
  await ask('/character select emberwarden'); await ask('/expedition', 2500);
  for (let i = 0; i < 40; i++) { await sleep(2000); if (/expedition/.test(await ask('/data get entity @s Dimension', 400))) break; }
  await sleep(2000);
  await ask('/effect give @s minecraft:resistance 999 4 true', 200); await ask('/effect give @s minecraft:regeneration 999 4 true', 200);
  await ask('/emberfall wavestop 0', 300); await ask('/time set midnight', 200);
  await ask('/kill @e[type=!player,type=!minecraft:item_display,type=!minecraft:interaction]', 900);
  const bx = bot.entity.position.x, by = bot.entity.position.y, bz = bot.entity.position.z;
  const pin = setInterval(() => bot.chat(`/tp @s ${bx.toFixed(2)} ${by.toFixed(2)} ${bz.toFixed(2)} 0 0`), 700);
  await ask('/emberfall debugloadout EmberTester', 700);
  await ask('/emberfall debugweapongrowth EmberTester grant 0 108 0', 600);
  console.log('STATE', (await ask('/emberfall debugweapongrowth EmberTester', 700)).slice(0, 140));
  await ask('/kill @e[tag=keep]', 500);
  await ask('/execute at @s run summon emberfall:horde_zombie ~0 ~ ~2.5 {Tags:["s1","keep"],NoAI:1b,Silent:1b,PersistenceRequired:1b}', 300);
  await ask(`/attribute @e[tag=s1,limit=1] minecraft:max_health base set ${BIG}`, 150);
  await ask(`/data modify entity @e[tag=s1,limit=1] Health set value ${BIG}.0f`, 150);
  await sleep(600);
  for (let k = 0; k < 16; k++) {
    const h = await ask('/data get entity @e[tag=s1,limit=1] Health', 300);
    const p = await ask('/data get entity @e[tag=s1,limit=1] Pos', 300);
    console.log(`T${k} HP:`, h.slice(-70), '| POS:', p.slice(-70));
    await sleep(200);
  }
  clearInterval(pin); await ask('/expedition leave', 800);
  console.log('PROBE_DONE'); bot.quit(); setTimeout(() => process.exit(0), 300);
});
