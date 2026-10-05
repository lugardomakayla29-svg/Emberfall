const mineflayer = require('mineflayer');
const bot = mineflayer.createBot({ host: '127.0.0.1', port: 25565, username: 'EmberTester', version: '1.21.11', auth: 'offline' });
const sleep = ms => new Promise(r => setTimeout(r, ms));
const chat = []; bot.on('message', m => chat.push(m.toString()));
bot.on('error', e => console.log('ERROR', e));
const pk = []; let on = false;
bot._client.on('packet', (d, m) => { if (on && m.name === 'world_particles' && d.particle && d.particle.type === 'dust') pk.push({ t: Date.now(), x: d.x, y: d.y, z: d.z, c: d.particle.data.color & 0xffffff, a: d.alwaysShow && d.longDistance }); });
const read = async (cmd, re) => { chat.length = 0; bot.chat(cmd); await sleep(450); const m = chat.join(' ').match(re); return m ? +m[1] : null; };
bot.once('spawn', async () => {
  await sleep(6000);
  const c = async (x, w = 800) => { bot.chat(x); await sleep(w); };
  await c('/gamemode survival', 700);
  await c('/effect give @s minecraft:resistance 900 4 true', 400);
  await c('/effect give @s minecraft:regeneration 900 4 true', 400);
  await c('/tp @s 52.5 72 40.5', 2500);
  await c('/kill @e[type=!player,distance=..80]', 800);
  await c('/summon emberfall:blightfeather_marksman 40.5 72 40.5 {Tags:["bf"]}', 1000);
  await c('/attribute @e[tag=bf,limit=1] minecraft:movement_speed base set 0', 600);
  const mx = await read('/data get entity @e[tag=bf,limit=1] Pos[0]', /: ([\d.\-]+)d/);
  const mz = await read('/data get entity @e[tag=bf,limit=1] Pos[2]', /: ([\d.\-]+)d/);
  const my = await read('/data get entity @e[tag=bf,limit=1] Pos[1]', /: ([\d.\-]+)d/);
  console.log('marksman at', mx, my, mz, ' player at', bot.entity.position.x.toFixed(2), bot.entity.position.z.toFixed(2));
  // Pin the player: arrows and the barrage knock it around, which would move the aim point. Re-teleport constantly
  // and record where the player actually stands (server truth) at the moment of each lane draw.
  const stand = { x: 52.5, y: 72, z: 40.5 };
  const pinner = setInterval(() => bot.chat('/tp @s 52.5 72 40.5'), 900);
  on = true; await sleep(110000); on = false; clearInterval(pinner);
  await c('/save-all flush', 6000);   // force chunk serialization while stuck arrows exist
  await c('/kill @e[tag=bf]', 500);
  require('fs').writeFileSync('/tmp/laser_pk.json', JSON.stringify({ mx, my, mz, px: 52.5, pz: 40.5, py: 72, pk }));
  console.log('packets', pk.length);
  bot.quit(); process.exit(0);
});
