// Grave Legion: level-10 Gravedigger, meter full, 8 weak zombies + 1 tough one inside the vortex. The server log line LEGION_TEST reports
// raised/strikes/standing. Prints the survivor's hp after the vortex so a control run (-Demberfall.noLegion=true) can be compared.
const mineflayer = require('mineflayer');
const bot = mineflayer.createBot({ host: '127.0.0.1', port: 25565, username: 'EmberTester', version: '1.21.11', auth: 'offline' });
const sleep = ms => new Promise(r => setTimeout(r, ms));
const lines = []; bot.on('message', m => lines.push(m.toString())); bot.on('error', e => console.log('ERR', e.message));
const ask = async (x, w = 450) => { const n = lines.length; bot.chat(x); await sleep(w); return lines.slice(n).join(' | '); };
const num = t => { const m = t.match(/entity data: (-?[\d.]+)/); return m ? parseFloat(m[1]) : NaN; };
bot.once('spawn', async () => {
  await sleep(5000);
  await ask('/gamemode survival'); await ask('/effect clear @s');
  await ask('/character select gravedigger'); await ask('/expedition', 2500);
  for (let i = 0; i < 40; i++) { await sleep(2000); if (/expedition/.test(await ask('/data get entity @s Dimension', 400))) break; }
  await sleep(2000);
  await ask('/effect give @s minecraft:resistance 999 4 true', 200); await ask('/effect give @s minecraft:regeneration 999 4 true', 200);
  await ask('/emberfall wavestop 0', 300); await ask('/time set midnight', 200);
  await ask('/kill @e[type=!player,type=!minecraft:item_display,type=!minecraft:interaction]', 900);
  const bx = bot.entity.position.x, by = bot.entity.position.y, bz = bot.entity.position.z;
  const pin = setInterval(() => bot.chat(`/tp @s ${bx.toFixed(2)} ${by.toFixed(2)} ${bz.toFixed(2)} 0 0`), 700);
  await ask('/emberfall debugloadout EmberTester', 700);
  // level 10 (108 kills) and a FULL meter so the very next landed hit opens the vortex
  await ask('/emberfall debugweapongrowth EmberTester grant 0 108 0', 600);   // level 10, meter EMPTY: the vortex must not fire while the scene is built
  console.log('STATE', (await ask('/emberfall debugweapongrowth EmberTester', 700)).slice(0, 150));
  // 8 weak zombies in a ring 3 blocks out (NoAI so they stay in the vortex) and one tough target 4 blocks ahead
  for (let i = 0; i < 8; i++) {
    const a = i * Math.PI / 4, dx = (Math.cos(a) * 3).toFixed(2), dz = (Math.sin(a) * 3).toFixed(2);
    await ask(`/execute at @s run summon emberfall:horde_zombie ~${dx} ~ ~${dz} {Tags:["weak","keep"],NoAI:1b,Silent:1b,PersistenceRequired:1b,Health:30f}`, 120);
  }
  console.log('SUMMON_BIG', (await ask('/execute at @s run summon emberfall:horde_zombie ~0 ~ ~4 {Tags:["big","keep"],NoAI:1b,Silent:1b,PersistenceRequired:1b}', 300)).slice(-100));
  console.log('BIG_EXISTS', (await ask('/execute if entity @e[tag=big]', 300)).slice(-60));
  console.log('ATTR', (await ask('/attribute @e[tag=big,limit=1] minecraft:max_health base set 1024', 200)).slice(-100));
  await ask('/data modify entity @e[tag=big,limit=1] Health set value 1024.0f', 150);
  await sleep(500);
  await ask('/emberfall debugweapongrowth EmberTester grant 0 0 1000', 500);   // NOW fill the meter: the next landed hit opens the vortex
  const r0 = await ask('/data get entity @e[tag=big,limit=1] Health', 400); console.log('RAW0', r0.slice(-120));
  const h0 = num(r0);
  await sleep(9000);                                   // the auto weapon lands its hit, the vortex opens, 15 pulses x 4 ticks = 3 s, then settle
  const r1 = await ask('/data get entity @e[tag=big,limit=1] Health', 400); console.log('RAW1', r1.slice(-120));
  const h1 = num(r1);
  const weak = (await ask('/execute if entity @e[tag=weak]', 400));
  console.log('BIG_HP', h0, '->', h1, 'lost', (h0 - h1).toFixed(1), '| weak left:', /passed|Test passed/.test(weak) ? 'some' : 'none');
  clearInterval(pin); await ask('/expedition leave', 800);
  console.log('LEGION_DONE'); bot.quit(); setTimeout(() => process.exit(0), 300);
});
