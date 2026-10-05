const mineflayer = require('mineflayer');
const bot = mineflayer.createBot({ host: '127.0.0.1', port: 25565, username: 'EmberTester', version: '1.21.11', auth: 'offline' });
const sleep = ms => new Promise(r => setTimeout(r, ms));
bot.on('error', e => console.log('ERROR', e));
let last = null;
bot.on('message', m => { const t=m.toString(); const r=t.match(/data: \[?([-0-9.]+)f?[,\]]/); if (r) last = parseFloat(r[1]); });
const c = async (x, w=500) => { bot.chat(x); await sleep(w); };
const q = async (sel) => { last = null; await c(`/data get entity ${sel} Rotation`, 500); return last; };
bot.once('spawn', async () => {
  await sleep(6000);
  await c('/gamemode creative'); await c('/tp @s 0 200 10', 1500);
  await c('/kill @e[type=minecraft:item_display]', 400); await c('/kill @e[type=emberfall:tiki_magma]', 400); await c('/kill @e[type=emberfall:tiki_segment]', 400);
  await sleep(6000);
  await c('/emberfall spawnelite tiki_magma', 2000);
  await c('/execute at @s run tp @e[type=emberfall:tiki_magma,limit=1] 0 200 0', 500);
  await c('/attribute @e[type=emberfall:tiki_magma,limit=1] minecraft:movement_speed base set 0', 500);
  for (const yaw of [0, 90, 180, -90]) {
    await c(`/tp @e[type=emberfall:tiki_magma,limit=1] 0 200 0 ${yaw} 0`, 4000);
    const mob = await q('@e[type=emberfall:tiki_magma,limit=1]');
    const seg = await q('@e[type=emberfall:tiki_segment,limit=1,sort=nearest,x=0,y=201.5,z=0]');
    const dsp = await q('@e[type=minecraft:item_display,limit=1,sort=nearest,x=0,y=201.5,z=0]');
    console.log(`set yaw ${yaw}: mob=${mob} segment=${seg} mask display=${dsp}`);
  }
  bot.quit(); process.exit(0);
});
