// One plain hit vs one ultimate, measured on fresh foes so the ratio is real. vanguard, broadsword only.
const mineflayer = require('mineflayer');
const bot = mineflayer.createBot({ host: '127.0.0.1', port: 25565, username: 'EmberTester', version: '1.21.11', auth: 'offline' });
const sleep = ms => new Promise(r => setTimeout(r, ms));
const lines = []; bot.on('message', m => lines.push(m.toString())); bot.on('error', e => console.log('ERR', e.message));
const ask = async (x, w = 500) => { const n = lines.length; bot.chat(x); await sleep(w); return lines.slice(n).join(' | '); };
const hp = async tag => { const r = await ask(`/data get entity @e[tag=${tag},limit=1] Health`, 450); const m = /entity data: (-?[\d.]+)f/.exec(r); return m ? +m[1] : null; };
const foe = async (tag, dx, dz) => {
  await ask(`/execute at @s run summon emberfall:horde_zombie ~${dx} ~ ~${dz} {Tags:["${tag}","keep"],NoAI:1b,Silent:1b,PersistenceRequired:1b}`, 350);
  await ask(`/attribute @e[tag=${tag},limit=1] minecraft:max_health base set 100000`, 150);
  await ask(`/data modify entity @e[tag=${tag},limit=1] Health set value 100000.0f`, 150);
};
bot.once('spawn', async () => {
  await sleep(5000);
  await ask('/gamemode survival'); await ask('/effect clear @s');
  await ask('/character select vanguard'); await ask('/expedition', 2500);
  for (let i = 0; i < 40; i++) { await sleep(2000); if (/expedition/.test(await ask('/data get entity @s Dimension', 400))) break; }
  await sleep(2000);
  await ask('/effect give @s minecraft:resistance 999 4 true', 200); await ask('/emberfall wavestop 0', 300); await ask('/time set midnight', 200);
  await ask('/kill @e[type=!player,type=!minecraft:item_display,type=!minecraft:interaction]', 900);
  const bx = bot.entity.position.x, by = bot.entity.position.y, bz = bot.entity.position.z;
  const pin = setInterval(() => bot.chat(`/tp @s ${bx.toFixed(2)} ${by.toFixed(2)} ${bz.toFixed(2)} 0 0`), 800);
  // one foe in reach: watch hp in fine steps to see the size of each hit
  await foe('a', 0, 1.5);
  const seq = []; for (let i = 0; i < 10; i++) { seq.push(await hp('a')); }
  console.log('plain hits, hp samples:', seq.join(' '));
  const d = []; for (let i = 1; i < seq.length; i++) if (seq[i] !== null && seq[i-1] !== null) d.push((seq[i-1]-seq[i]).toFixed(2));
  console.log('per-sample drops      :', d.join(' '));
  clearInterval(pin); await ask('/kill @e[tag=keep]', 300); await ask('/expedition leave', 800);
  bot.quit(); setTimeout(() => process.exit(0), 400);
});
