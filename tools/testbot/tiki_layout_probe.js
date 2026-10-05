// Reads the real vertical layout of a Tiki: mob, decorative cube, head displays and roof, in a frozen tick.
const mineflayer = require('mineflayer');
const sleep = ms => new Promise(r => setTimeout(r, ms));
const bot = mineflayer.createBot({ host: '127.0.0.1', port: 25565, username: 'EmberTester', version: '1.21.11', auth: 'offline' });
const chat = []; bot.on('message', m => chat.push(m.toString())); bot.on('error', e => console.log('ERR', e.message));
const ask = async (c, w = 450) => { chat.length = 0; bot.chat(c); await sleep(w); return chat.join(' | '); };
const mode = process.argv[2] || 'fodder';
bot.once('spawn', async () => {
  await sleep(5000); await ask('/op EmberTester'); await ask('/gamemode creative');
  await ask('/fill -6 199 -6 6 199 14 minecraft:stone', 1200); await ask('/tp @s 0 201 10', 1500);  // platform in the sky, nothing else near
  await sleep(1500);
  console.log('player at', (await ask('/data get entity @s Pos', 500)).replace(/^.*following entity data: /, ''));
  await ask('/kill @e[type=!player]', 700); await sleep(1500);
  const cmd = mode === 'fodder' ? '/emberfall spawnveteran tiki_magma' : `/emberfall spawnelite ${mode}`;
  console.log('spawn:', (await ask(cmd, 1500)).slice(0, 90));
  await ask('/execute at @s run tp @e[type=emberfall:tiki_magma,limit=1] 0 200 0', 600);
  await ask('/attribute @e[type=emberfall:tiki_magma,limit=1] minecraft:movement_speed base set 0', 2500);
  await ask('/tick freeze', 400);
  const show = async (label, sel, path) => { console.log(label.padEnd(10), (await ask(`/data get entity ${sel} ${path}`, 450)).replace(/^.*following entity data: /, '')); };
  await show('MOB', '@e[type=emberfall:tiki_magma,limit=1]', 'Pos');
  await show('CUBE2', '@e[type=emberfall:tiki_cube,limit=1]', 'Pos');
  for (let i = 0; i < 4; i++) {
    await show('HEAD' + i, `@e[type=minecraft:item_display,limit=1,sort=nearest,tag=!r${i},x=0,y=200,z=0,distance=..6]`, 'Pos');
    await ask(`/tag @e[type=minecraft:item_display,limit=1,sort=nearest,tag=!r${i},x=0,y=200,z=0,distance=..6] add r${i}`, 200);
  }
  await show('ROOF', '@e[type=minecraft:block_display,limit=1,x=0,y=200,z=0,distance=..6]', 'Pos');
  await show('ROOFXF', '@e[type=minecraft:block_display,limit=1,x=0,y=200,z=0,distance=..6]', 'transformation');
  await ask('/tick unfreeze', 300);
  bot.quit(); setTimeout(() => process.exit(0), 300);
});
