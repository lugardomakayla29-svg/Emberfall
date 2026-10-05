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
  await ask('/gamemode survival'); await ask('/effect give @s minecraft:resistance 999 4 true', 250);
  await ask('/character select juggernaut', 600); await ask('/expedition', 1500); await sleep(1500);
  await ask('/emberfall boss 0', 900); await ask('/emberfall wavestop 0', 300);
  await ask(`/data modify entity ${G} NoAI set value 1b`, 400);
  const bx = await num(G, 'Pos[0]'), by0 = await num(G, 'Pos[1]'), bz = await num(G, 'Pos[2]');
  const by = Math.floor(by0);
  console.log('boss pos', bx.toFixed(2), by0.toFixed(2), bz.toFixed(2));
  // Print the block column profile along +x from the boss at the boss's z, for the slope test.
  await ask(`/execute as ${G} at @s run fill ~-3 ${by - 8} ~-3 ~26 ${by - 5} ~8 minecraft:stone`, 900);
  await ask(`/execute as ${G} at @s run fill ~-3 ${by - 4} ~-3 ~26 ${by + 6} ~8 minecraft:air`, 900);
  await ask(`/execute as ${G} at @s run fill ~-3 ${by - 4} ~-3 ~2 ${by - 1} ~8 minecraft:stone`, 900);
  await ask(`/execute as ${G} at @s run fill ~3 ${by - 4} ~-3 ~3 ${by - 2} ~8 minecraft:stone`, 900);
  await ask(`/execute as ${G} at @s run fill ~4 ${by - 4} ~-3 ~4 ${by - 3} ~8 minecraft:stone`, 900);
  const X = Math.floor(bx), Z = Math.floor(bz);
  for (let dx = 0; dx <= 11; dx++) {
    let col = '';
    for (let y = by + 2; y >= by - 5; y--) {
      const r = await ask(`/execute if block ${X + dx} ${y} ${Z} minecraft:air`, 120);
      col += /passed/.test(r) ? '.' : '#';
    }
    console.log(`x+${String(dx).padStart(2)} (top y=${by + 2} .. bottom y=${by - 5}): ${col}`);
  }
  // Where does the probe's ray start and end? Place the player as the cover test does and read both ends.
  await ask(`/execute as ${G} at @s run tp EmberTester ~10 ${by - 3} ~`, 600);
  const px = await num('EmberTester', 'Pos[0]'), py = await num('EmberTester', 'Pos[1]'), pz = await num('EmberTester', 'Pos[2]');
  console.log('player pos', px.toFixed(2), py.toFixed(2), pz.toFixed(2), '| beam origin y would be', (py + 1.2).toFixed(2), '| boss y', by0.toFixed(2));
  for (const [nm, y] of [['player feet', Math.floor(py)], ['player chest', Math.floor(py + 1)], ['origin block', Math.floor(py + 1.2)]]) {
    const r = await ask(`/execute if block ${Math.floor(px)} ${y} ${Math.floor(pz)} minecraft:air`, 200);
    console.log(nm, 'y=' + y, /passed/.test(r) ? 'AIR' : 'SOLID');
  }
  const ox = Math.floor(bx), oz = Math.floor(bz), oy = Math.floor(py + 1.2);
  const r2 = await ask(`/execute if block ${ox} ${oy} ${oz} minecraft:air`, 200);
  console.log('boss column at beam-origin height y=' + oy, /passed/.test(r2) ? 'AIR' : 'SOLID');
  bot.quit(); setTimeout(() => process.exit(0), 300);
});
bot.on('error', e => console.log('ERR', e.message));
