const mineflayer = require('mineflayer');
const bot = mineflayer.createBot({ host: '127.0.0.1', port: 25565, username: 'EmberTester', version: '1.21.11', auth: 'offline' });
const sleep = ms => new Promise(r => setTimeout(r, ms));
const lines = []; bot.on('message', m => lines.push(m.toString()));
const ask = async (x, w = 250) => { const n = lines.length; bot.chat(x); await sleep(w); return lines.slice(n).join(' | '); };
const y = s => { const m = /\[([\d.-]+)d, ([\d.-]+)d, ([\d.-]+)d\]/.exec(s); return m ? +m[2] : NaN; };
bot.once('spawn', async () => {
  await sleep(6500);
  await ask('/kill @e[type=!player]', 500); await ask('/kill @e[type=minecraft:item_display]', 500); await sleep(1500);
  await ask('/gamemode survival'); await ask('/effect give @s minecraft:resistance 999 4 true', 300);
  await ask('/effect give @s minecraft:regeneration 999 4 true', 300);
  await ask('/character select juggernaut', 600); await ask('/expedition', 1500); await sleep(1500);
  await ask('/emberfall bossdevourer 0', 1500);
  const ys = []; const t0 = Date.now(); let floor = null;
  while (Date.now() - t0 < 45000) {
    const v = y(await ask('/data get entity @e[type=emberfall:devourer_brain,limit=1] Pos', 230));
    if (!isNaN(v)) { ys.push([Date.now() - t0, v]); if (floor === null) floor = v; }
  }
  // the floor is the modal y while surfaced; the leap peak is the max above it
  const counts = {}; ys.forEach(([, v]) => { const k = v.toFixed(0); counts[k] = (counts[k] || 0) + 1; });
  const modal = +Object.entries(counts).sort((a, b) => b[1] - a[1])[0][0];
  const peak = Math.max(...ys.map(a => a[1]));
  console.log(`samples=${ys.length} modal floor y=${modal} peak y=${peak.toFixed(2)} => leap rise above floor ${(peak - modal).toFixed(2)} | min y=${Math.min(...ys.map(a => a[1])).toFixed(2)} (burrow depth ${(modal - Math.min(...ys.map(a => a[1]))).toFixed(2)})`);
  console.log('y histogram', JSON.stringify(counts));
  bot.quit(); setTimeout(() => process.exit(0), 300);
});
bot.on('error', e => console.log('ERR', e.message));
