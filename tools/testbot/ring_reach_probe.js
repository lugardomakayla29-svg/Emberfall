const mineflayer = require('mineflayer');
const bot = mineflayer.createBot({ host: '127.0.0.1', port: 25565, username: 'EmberTester', version: '1.21.11', auth: 'offline' });
const sleep = ms => new Promise(r => setTimeout(r, ms));
const lines = []; bot.on('message', m => lines.push(m.toString()));
const ask = async (x, w = 500) => { const n = lines.length; bot.chat(x); await sleep(w); return lines.slice(n).join(' | '); };
const G = '@e[type=emberfall:ember_guardian,limit=1]';
const num = async (sel, path) => { for (let k = 0; k < 4; k++) { const r = await ask(`/data get entity ${sel} ${path}`, 400); const m = /(-?[\d.]+)[fdb]?$/.exec(r.trim()); if (m) return +m[1]; } return NaN; };
bot.once('spawn', async () => {
  await sleep(6500);
  await ask('/kill @e[type=!player]', 600); await ask('/kill @e[type=minecraft:item_display]', 600); await sleep(1200);
  await ask('/gamemode survival'); await ask('/effect give @s minecraft:resistance 999 4 true', 250); await ask('/effect give @s minecraft:regeneration 999 4 true', 250);
  await ask('/character select juggernaut', 600); await ask('/expedition', 1500); await sleep(1500);
  await ask('/emberfall boss 0', 900); await ask('/emberfall wavestop 0', 300);
  await ask('/kill @e[type=emberfall:cinder_pylon]', 600); await sleep(800);
  await ask(`/data modify entity ${G} Health set value 380f`, 500); await sleep(1500);
  // Put the player 12 up and 15 east of the BOSS, hold, and sample the true 3D distance every 2 s.
  for (let i = 0; i < 8; i++) {
    for (let k = 0; k < 8; k++) { await ask(`/execute as ${G} at @s run tp EmberTester ~15 ~12 ~`, 60); await sleep(150); }
    const bx = await num(G, 'Pos[0]'), by = await num(G, 'Pos[1]'), bz = await num('EmberTester', 'Pos[2]');
    const px = await num('EmberTester', 'Pos[0]'), py = await num('EmberTester', 'Pos[1]'), pz = await num('EmberTester', 'Pos[2]');
    const bz2 = await num(G, 'Pos[2]');
    const d = Math.sqrt((bx - px) ** 2 + (by - py) ** 2 + (bz2 - pz) ** 2);
    console.log(`sample ${i}: boss y=${by.toFixed(1)} player y=${py.toFixed(1)} dy=${(py - by).toFixed(1)} dist3D=${d.toFixed(1)}`);
  }
  bot.quit(); setTimeout(() => process.exit(0), 300);
});
bot.on('error', e => console.log('ERR', e.message));
