// Two windows on one fight. Window A: pylons alive. Window B: after the last pylon breaks. The run player is kept alive and 3 blocks
// from the boss the whole time. The verdict comes from the server's HITDBG lines between the WINA / WINB / END markers in the log.
const mineflayer = require('mineflayer');
const bot = mineflayer.createBot({ host: '127.0.0.1', port: 25565, username: 'EmberTester', version: '1.21.11', auth: 'offline' });
const sleep = ms => new Promise(r => setTimeout(r, ms));
const lines = []; bot.on('message', m => lines.push(m.toString()));
const ask = async (x, w = 500) => { const n = lines.length; bot.chat(x); await sleep(w); return lines.slice(n).join(' | '); };
const G = '@e[type=emberfall:ember_guardian,limit=1]';
bot.once('spawn', async () => {
  await sleep(6500);
  await ask('/kill @e[type=!player]', 600); await ask('/kill @e[type=minecraft:item_display]', 600); await sleep(1200);
  await ask('/gamemode survival'); await ask('/character select juggernaut', 600); await ask('/expedition', 1500); await sleep(1500);
  await ask('/effect give @s minecraft:resistance 999 4 true', 250); await ask('/effect give @s minecraft:regeneration 999 4 true', 250);
  await ask('/emberfall boss 0', 900); await ask('/emberfall wavestop 0', 300);
  await ask('/kill @e[type=!player,type=!emberfall:ember_guardian,type=!emberfall:cinder_pylon,type=!minecraft:item_display]', 500);
  const hold = async ms => { const t0 = Date.now(); while (Date.now() - t0 < ms) { await ask(`/execute as ${G} at @s run tp EmberTester ~3 ~ ~`, 150); await sleep(200); } };
  await ask('/say WINA', 200); await hold(14000);
  await ask('/kill @e[type=emberfall:cinder_pylon]', 900); await sleep(300);
  await ask('/say WINB', 200); await hold(9000);
  await ask('/say END', 200);
  await ask('/kill @e[type=emberfall:ember_guardian]', 800); await sleep(2500);
  bot.quit(); setTimeout(() => process.exit(0), 300);
});
bot.on('error', e => console.log('ERR', e.message));
