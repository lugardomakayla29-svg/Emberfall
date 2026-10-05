const mineflayer = require('mineflayer');
const bot = mineflayer.createBot({ host: '127.0.0.1', port: 25565, username: 'EmberTester', version: '1.21.11', auth: 'offline' });
const sleep = ms => new Promise(r => setTimeout(r, ms));
bot.on('error', e => console.log('ERROR', e));
const out = [];
bot.on('message', m => { const t=m.toString(); const r=t.match(/data: ([-0-9.]+)d/); if (r) out.push(parseFloat(r[1])); });
const c = async (x, w=450) => { bot.chat(x); await sleep(w); };
const which = process.argv[2];
bot.once('spawn', async () => {
  await sleep(6000);
  await c('/gamemode creative'); await c('/tp @s 0 200 10', 1500);
  await c('/fill -6 199 -6 6 199 6 minecraft:stone', 900);
  await c('/kill @e[type=emberfall:tiki_magma]', 500); await c('/kill @e[type=emberfall:tiki_segment]', 500);
  await c('/kill @e[type=minecraft:item_display]', 500); await c('/kill @e[type=minecraft:block_display]', 500);
  await sleep(6000);
  await c('/kill @e[type=emberfall:tiki_segment]', 500); await sleep(3000);
  await c(`/emberfall spawnelite ${which}`, 1500);
  await c('/execute at @s run tp @e[type=emberfall:tiki_magma,limit=1] 0 200 0', 600);
  await c('/attribute @e[type=emberfall:tiki_magma,limit=1] minecraft:movement_speed base set 0', 2500);
  out.length = 0;
  await c('/data get entity @e[type=emberfall:tiki_magma,limit=1] Pos[1]');
  for (let i = 0; i < 5; i++) {
    await c('/data get entity @e[type=emberfall:tiki_segment,tag=!read,limit=1,sort=nearest,x=0,y=200,z=0] Pos[1]');
    await c('/tag @e[type=emberfall:tiki_segment,tag=!read,limit=1,sort=nearest,x=0,y=200,z=0] add read');
  }
  console.log(which, 'Y list:', out.map(v=>+v.toFixed(3)).join(' '), '| diffs:', out.slice(1).map((v,i)=>+(v-out[i]).toFixed(3)).join(' '));
  bot.quit(); process.exit(0);
});
