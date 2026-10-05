// Calm beat. Reach phase 3 with the last pylon lit, let a Cinderfall start, then drop the boss under 5%. The server's
// CINDERDBG calm line reports pylonsAlive and the attack that was running. Afterwards: no attack may start, and the boss must
// take damage (the gate must be open).
const mineflayer = require('mineflayer');
const fs = require('fs');
const bot = mineflayer.createBot({ host: '127.0.0.1', port: 25565, username: 'EmberTester', version: '1.21.11', auth: 'offline' });
const sleep = ms => new Promise(r => setTimeout(r, ms));
const lines = []; bot.on('message', m => lines.push(m.toString()));
const ask = async (x, w = 500) => { const n = lines.length; bot.chat(x); await sleep(w); return lines.slice(n).join(' | '); };
const G = '@e[type=emberfall:ember_guardian,limit=1]';
const LOG = (process.env.EMBERFALL_HOME || '.') + '/run/server_run.log';
const count = () => fs.readFileSync(LOG, 'utf8').split('\n').filter(l => l.includes('CINDERDBG begin')).length;
const num = async (path) => { for (let k = 0; k < 5; k++) { const r = await ask(`/data get entity ${G} ${path}`, 350); const m = /(-?[\d.]+)[fdb]?$/.exec(r.trim()); if (m) return +m[1]; } return NaN; };
bot.once('spawn', async () => {
  await sleep(6500);
  await ask('/kill @e[type=!player]', 600); await ask('/kill @e[type=minecraft:item_display]', 600); await sleep(1200);
  await ask('/gamemode survival'); await ask('/effect give @s minecraft:resistance 999 4 true', 250); await ask('/effect give @s minecraft:regeneration 999 4 true', 250);
  await ask('/character select juggernaut', 600); await ask('/expedition', 1500); await sleep(1500);
  await ask('/emberfall boss 0', 900); await ask('/emberfall wavestop 0', 300);
  await ask('/kill @e[type=emberfall:cinder_pylon]', 600); await sleep(800);
  await ask(`/data modify entity ${G} Health set value 380f`, 500); await sleep(1500);
  await ask('/kill @e[type=emberfall:cinder_pylon]', 600); await sleep(800);
  await ask(`/data modify entity ${G} Health set value 190f`, 500); await sleep(1500);   // phase 3, last pylon lit
  // Stay next to the boss until a Cinderfall has begun, so an attack is running when the calm beat hits.
  const before = count(); const t0 = Date.now();
  while (count() === before && Date.now() - t0 < 60000) { await ask(`/execute as ${G} at @s run tp EmberTester ~9 ~ ~`, 60); await sleep(150); }
  console.log('attack running, cinderfalls so far', count());
  await ask(`/data modify entity ${G} Health set value 20f`, 400);   // 3.3%: under the 5% mark
  await sleep(2500);
  const begunAtCalm = count();
  console.log('pylons alive after calm:', (await ask('/execute if entity @e[type=emberfall:cinder_pylon]', 400)).trim().slice(0, 60));
  await sleep(14000);   // long enough for several attacks if any were still coming
  console.log('attacks begun after the calm beat:', count() - begunAtCalm);
  // Hittable: the boss must lose health now (auto-weapon or our own hit).
  // Hittable: hit the boss ourselves with /damage (ordinary damage, not a bypass) and read the health before and after.
  await ask(`/data modify entity ${G} Health set value 20f`, 400);
  const h0 = await num('Health'); await ask(`/damage ${G} 5 minecraft:player_attack by EmberTester`, 500);
  const h1 = await num('Health'); console.log(`boss health ${h0} -> ${h1}`);
  bot.quit(); setTimeout(() => process.exit(0), 300);
});
bot.on('error', e => console.log('ERR', e.message));
