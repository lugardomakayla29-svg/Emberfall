const mineflayer = require('mineflayer');
const bot = mineflayer.createBot({ host: '127.0.0.1', port: 25565, username: 'EmberTester', version: '1.21.11', auth: 'offline' });
const sleep = ms => new Promise(r => setTimeout(r, ms));
bot.on('error', e => console.log('ERROR', e));
const CHAR = process.argv[2], SECS = parseInt(process.argv[3] || '30', 10);
const pk = []; let on = false;
bot._client.on('packet', (d, m) => { if (on && m.name === 'world_particles' && d.particle) pk.push({ t: Date.now(), x: d.x, y: d.y, z: d.z, type: d.particle.type, f: d.alwaysShow }); });
bot.once('spawn', async () => {
  await sleep(6000);
  const c = async (x, w = 800) => { bot.chat(x); await sleep(w); };
  await c('/gamemode survival', 700);
  await c('/character select ' + CHAR, 700);
  await c('/expedition leave', 800);
  await c('/expedition', 4000);
  await c('/effect give @s minecraft:resistance 900 4 true', 400);
  await c('/effect give @s minecraft:regeneration 900 4 true', 400);
  // Clear whatever the wave director has spawned, then place two held-still targets beside the player.
  await c('/kill @e[type=!player,tag=!keep,distance=..60]', 800);
  await c('/execute at @s run summon emberfall:horde_zombie ~1.6 ~ ~ {Tags:["t1"],Silent:1b,PersistenceRequired:1b,Attributes:[{id:"minecraft:movement_speed",base:0.0}]}', 700);
  await c('/execute at @s run summon emberfall:horde_zombie ~1.8 ~ ~1 {Tags:["t2"],Silent:1b,PersistenceRequired:1b,Attributes:[{id:"minecraft:movement_speed",base:0.0}]}', 700);
  for (const t of ['t1', 't2']) {
    await c(`/attribute @e[tag=${t},limit=1] minecraft:movement_speed base set 0`, 400);
    await c(`/attribute @e[tag=${t},limit=1] minecraft:max_health base set 1000`, 400);
    await c(`/effect give @e[tag=${t}] minecraft:instant_health 1 10 true`, 400);
  }
  await c('/data get entity @e[tag=t1,limit=1] Pos', 700);
  await c('/data get entity @e[tag=t2,limit=1] Pos', 700);
  await c('/data get entity @s Pos', 700);
  const pin = setInterval(() => bot.chat('/tp @s ' + bot.entity.position.x.toFixed(2) + ' ' + bot.entity.position.y.toFixed(2) + ' ' + bot.entity.position.z.toFixed(2)), 1500);
  // Mobs will not hold still, so hold them: re-place both targets 4.5 and 5.5 blocks from the player every 400 ms.
  const hold = setInterval(() => {
    bot.chat('/execute at @s run tp @e[tag=t1,limit=1] ~1.6 ~ ~');
    setTimeout(() => bot.chat('/execute at @s run tp @e[tag=t2,limit=1] ~1.8 ~ ~1'), 200);
  }, 1000);
  on = true; await sleep(SECS * 1000); on = false; clearInterval(pin); clearInterval(hold);
  await c('/data get entity @e[tag=t1,limit=1] Health', 700);
  await c('/kill @e[tag=t1]', 300); await c('/kill @e[tag=t2]', 300);
  await c('/expedition leave', 800);
  require('fs').writeFileSync('/tmp/sick_' + CHAR + '.json', JSON.stringify(pk));
  console.log(CHAR, 'particle packets', pk.length);
  bot.quit(); process.exit(0);
});
bot.on('message', m => { const t = m.toString(); if (/entity data|Unknown|Started|expedition/i.test(t)) console.log('CHAT:', t.slice(0, 170)); });
