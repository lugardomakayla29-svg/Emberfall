// Ring vs a player standing HIGH: a 7 block stone tower 15 blocks from the fight centre, player on top. The server's
// RINGDBG burst line reports horizontal dist, dy above the centre, and whether the player was put in the band.
const mineflayer = require('mineflayer');
const fs = require('fs');
const bot = mineflayer.createBot({ host: '127.0.0.1', port: 25565, username: 'EmberTester', version: '1.21.11', auth: 'offline' });
const sleep = ms => new Promise(r => setTimeout(r, ms));
const lines = []; bot.on('message', m => lines.push(m.toString()));
const ask = async (x, w = 500) => { const n = lines.length; bot.chat(x); await sleep(w); return lines.slice(n).join(' | '); };
const G = '@e[type=emberfall:ember_guardian,limit=1]';
const LOG = (process.env.EMBERFALL_HOME || '.') + '/run/server_run.log';
const centre = () => { const l = fs.readFileSync(LOG, 'utf8').split('\n').filter(x => x.includes('boss fight started')); if (!l.length) return null; const m = /x=(-?\d+), y=(-?\d+), z=(-?\d+)/.exec(l[l.length - 1]); return m ? { x: +m[1] + 0.5, y: +m[2], z: +m[3] + 0.5 } : null; };
bot.once('spawn', async () => {
  await sleep(6500);
  await ask('/kill @e[type=!player]', 600); await ask('/kill @e[type=minecraft:item_display]', 600); await sleep(1200);
  await ask('/gamemode survival'); await ask('/effect give @s minecraft:resistance 999 4 true', 250); await ask('/effect give @s minecraft:regeneration 999 4 true', 250);
  await ask('/character select juggernaut', 600); await ask('/expedition', 1500); await sleep(1500);
  await ask('/emberfall boss 0', 900); await ask('/emberfall wavestop 0', 300);
  await ask('/kill @e[type=emberfall:cinder_pylon]', 600); await sleep(800);
  await ask(`/data modify entity ${G} Health set value 380f`, 500); await sleep(1500);
  const c = centre(); console.log('centre', JSON.stringify(c));
  const tx = Math.floor(c.x + 15), tz = Math.floor(c.z), top = c.y + 7;
  // Tower: clear the column, then stone from the centre's floor up to +7, player stands on it.
  await ask(`/fill ${tx} ${c.y} ${tz} ${tx} ${c.y + 12} ${tz} minecraft:air`, 500);
  await ask(`/fill ${tx} ${c.y - 2} ${tz} ${tx} ${top - 1} ${tz} minecraft:stone`, 500);
  const t0 = Date.now();
  while (Date.now() - t0 < 100000) { await ask(`/tp EmberTester ${tx + 0.5} ${top} ${tz + 0.5}`, 60); await sleep(150); }
  bot.quit(); setTimeout(() => process.exit(0), 300);
});
bot.on('error', e => console.log('ERR', e.message));
