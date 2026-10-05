const mineflayer = require('mineflayer');
const bot = mineflayer.createBot({ host: '127.0.0.1', port: 25565, username: 'EmberTester', version: '1.21.11', auth: 'offline' });
const sleep = ms => new Promise(r => setTimeout(r, ms));
const chat = []; bot.on('message', m => chat.push(m.toString()));
bot.on('error', e => console.log('ERROR', e));
const snd = []; let on = false;
let odd = 0;
bot._client.on('packet', (d, m) => {
  if (!on || m.name !== 'sound_effect') return;
  const so = d.sound || {};
  // named form: {data:{soundName}}; registry form: {soundId:n} (sound name unknown without the registry)
  const n = so.data && so.data.soundName ? so.data.soundName : ('id:' + (so.soundId !== undefined ? so.soundId : JSON.stringify(so)));
  if (!(so.data && so.data.soundName) && odd++ < 2) console.log('non-named sound shape:', JSON.stringify(d).slice(0, 220));
  snd.push({ t: Date.now(), n, v: d.volume, p: d.pitch, x: d.x / 8, y: d.y / 8, z: d.z / 8 });
});
const MODE = process.argv[2];
bot.once('spawn', async () => {
  await sleep(6000);
  const c = async (x, w = 800) => { bot.chat(x); await sleep(w); };
  await c('/gamemode survival', 700);
  await c('/effect give @s minecraft:resistance 900 4 true', 400);
  await c('/effect give @s minecraft:regeneration 900 4 true', 400);
  await c('/tp @s 44 72 40.5', 2500);
  await c('/kill @e[type=!player,distance=..80]', 800);
  if (MODE === 'bone') {
    await c('/summon emberfall:bonecaller_necromancer 40.5 72 40.5 {Tags:["t"]}', 1000);
    await c('/attribute @e[tag=t,limit=1] minecraft:movement_speed base set 0', 600);
  } else {
    // A head only needs a player within 24 blocks to fire; the anchor/brain rig is not needed for that.
    await c('/summon emberfall:hydra_head 40.5 72 40.5 {Tags:["t"]}', 1500);
    await c('/attribute @e[tag=t,limit=1] minecraft:movement_speed base set 0', 600);
  }
  const pin = setInterval(() => bot.chat('/tp @s 44 72 40.5'), 1100);
  on = true; await sleep(50000); on = false; clearInterval(pin);
  await c('/kill @e[tag=t]', 500);
  require('fs').writeFileSync('/tmp/snd_' + MODE + '.json', JSON.stringify(snd));
  console.log(MODE, 'sound packets', snd.length);
  bot.quit(); process.exit(0);
});
