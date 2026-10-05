// Does a run mob placed far away acquire the player? Spawn each type DIST blocks out, wait, read distance moved + Brain/target.
const mineflayer = require('mineflayer');
const bot = mineflayer.createBot({ host: '127.0.0.1', port: 25565, username: 'EmberTester', version: '1.21.11', auth: 'offline' });
const sleep = ms => new Promise(r => setTimeout(r, ms));
const lines = []; bot.on('message', m => lines.push(m.toString()));
const ask = async (x, w = 600) => { const n = lines.length; bot.chat(x); await sleep(w); return lines.slice(n).join(' | '); };
const DIST = parseInt(process.argv[2] || '26', 10);
const TYPES = (process.argv[3] || 'horde_zombie,horde_skeleton,horde_spider').split(',');
const pos = s => { const m = /\[(-?[\d.]+)d, (-?[\d.]+)d, (-?[\d.]+)d\]/.exec(s); return m ? [+m[1], +m[2], +m[3]] : null; };
bot.once('spawn', async () => {
  await sleep(6000);
  await ask('/gamemode survival'); await ask('/character select vanguard', 700); await ask('/expedition leave', 800); await ask('/expedition', 3500);
  await ask('/effect give @s minecraft:resistance 900 4 true', 300); await ask('/effect give @s minecraft:regeneration 900 4 true', 300);
  await ask('/kill @e[type=!player]', 800);
  const pin = setInterval(() => bot.chat('/tp @s ' + bot.entity.position.x.toFixed(2) + ' ' + bot.entity.position.y.toFixed(2) + ' ' + bot.entity.position.z.toFixed(2)), 500);
  const me = pos(await ask('/data get entity @s Pos', 700));
  console.log('player', me, 'DIST', DIST);
  for (const t of TYPES) {
    await ask(`/summon emberfall:${t} ~${DIST} ~ ~ {Tags:["probe"],PersistenceRequired:1b}`, 900);
    const p0 = pos(await ask('/data get entity @e[tag=probe,limit=1] Pos', 700));
    if (!p0) { console.log(t, 'NOT FOUND after summon'); continue; }
    const d0 = Math.hypot(p0[0] - me[0], p0[2] - me[2]);
    await sleep(5000);
    const p1 = pos(await ask('/data get entity @e[tag=probe,limit=1] Pos', 700));
    const brain = await ask('/data get entity @e[tag=probe,limit=1] Brain', 700);
    if (!p1) { console.log(t, 'died/gone'); continue; }
    const d1 = Math.hypot(p1[0] - me[0], p1[2] - me[2]);
    console.log(`${t.padEnd(16)} start ${d0.toFixed(1)} -> after 5s ${d1.toFixed(1)}  closed ${(d0 - d1).toFixed(1)} blocks`);
    await ask('/kill @e[tag=probe]', 500);
  }
  clearInterval(pin);
  await ask('/expedition leave', 800);
  bot.quit(); setTimeout(() => process.exit(0), 300);
});
bot.on('error', e => console.log('ERR', e.message));
