// Why did no attack start with the player held 8 above the fight centre? Sample boss + player every 2 s and log the
// server's own view of the boss's state (phase, exposed, attack) through /data.
const mineflayer = require('mineflayer');
const bot = mineflayer.createBot({ host: '127.0.0.1', port: 25565, username: 'EmberTester', version: '1.21.11', auth: 'offline' });
const sleep = ms => new Promise(r => setTimeout(r, ms));
const lines = []; bot.on('message', m => lines.push(m.toString()));
const ask = async (x, w = 500) => { const n = lines.length; bot.chat(x); await sleep(w); return lines.slice(n).join(' | '); };
const G = '@e[type=emberfall:ember_guardian,limit=1]';
const num = async (sel, path) => { for (let k = 0; k < 4; k++) { const r = await ask(`/data get entity ${sel} ${path}`, 350); const m = /(-?[\d.]+)[fdb]?$/.exec(r.trim()); if (m) return +m[1]; } return NaN; };
bot.once('spawn', async () => {
  await sleep(6500);
  await ask('/kill @e[type=!player]', 600); await ask('/kill @e[type=minecraft:item_display]', 600); await sleep(1200);
  await ask('/gamemode survival'); await ask('/effect give @s minecraft:resistance 999 4 true', 250); await ask('/effect give @s minecraft:regeneration 999 4 true', 250);
  await ask('/character select juggernaut', 600); await ask('/expedition', 1500); await sleep(1500);
  await ask('/emberfall boss 0', 900); await ask('/emberfall wavestop 0', 300);
  await ask('/kill @e[type=emberfall:cinder_pylon]', 600); await sleep(800);
  await ask(`/data modify entity ${G} Health set value 380f`, 500); await sleep(1500);
  const bx0 = await num(G, 'Pos[0]'), by0 = await num(G, 'Pos[1]'), bz0 = await num(G, 'Pos[2]');
  console.log('boss start', bx0.toFixed(1), by0.toFixed(1), bz0.toFixed(1));
  for (let i = 0; i < 10; i++) {
    for (let k = 0; k < 6; k++) { await ask(`/tp EmberTester ${(bx0 + 15).toFixed(2)} ${(by0 + 8).toFixed(2)} ${bz0.toFixed(2)}`, 60); await sleep(120); }
    const bx = await num(G, 'Pos[0]'), by = await num(G, 'Pos[1]'), bz = await num(G, 'Pos[2]');
    const px = await num('EmberTester', 'Pos[0]'), py = await num('EmberTester', 'Pos[1]'), pz = await num('EmberTester', 'Pos[2]');
    const hp = await num('EmberTester', 'Health');
    console.log(`s${i}: boss(${bx.toFixed(1)},${by.toFixed(1)},${bz.toFixed(1)}) player(${px.toFixed(1)},${py.toFixed(1)},${pz.toFixed(1)}) dy=${(py - by).toFixed(1)} d3=${Math.sqrt((bx - px) ** 2 + (by - py) ** 2 + (bz - pz) ** 2).toFixed(1)} hp=${hp}`);
  }
  bot.quit(); setTimeout(() => process.exit(0), 300);
});
bot.on('error', e => console.log('ERR', e.message));
