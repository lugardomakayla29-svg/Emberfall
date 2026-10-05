// Measures ONE Star Bit lob from the server: where the star display travels (apex, landing) versus where the
// player stood when the wind-up started, plus whether the player took damage and how many shard bolts fired.
const mineflayer = require('mineflayer');
const bot = mineflayer.createBot({ host: '127.0.0.1', port: 25565, username: 'EmberTester', version: '1.21.11', auth: 'offline' });
const sleep = ms => new Promise(r => setTimeout(r, ms));
const lines = []; bot.on('message', m => lines.push(m.toString()));
const ask = async (x, w = 300) => { const n = lines.length; bot.chat(x); await sleep(w); return lines.slice(n).join(' | '); };
const pos = s => { const m = /\[(-?[\d.]+)d, (-?[\d.]+)d, (-?[\d.]+)d\]/.exec(s); return m ? [+m[1], +m[2], +m[3]] : null; };
bot.once('spawn', async () => {
  await sleep(6500);
  await ask('/gamemode survival'); await ask('/effect give @s minecraft:resistance 999 0 true', 300);
  await ask('/kill @e[type=!player]', 600); await ask('/kill @e[type=minecraft:item_display]', 600); await sleep(1500);
  await ask('/character select juggernaut', 600); await ask('/expedition', 1500); await sleep(1500);
  await ask('/emberfall spawnelite umbral_magus', 900);
  const track = []; let lastStars = 0; let playerAtWindup = null; const hpSeries = [];
  for (let i = 0; i < 220; i++) {
    await ask('/execute at @e[type=emberfall:umbral_magus,limit=1] run tp @s ~9 ~ ~', 120);
    const sp = await ask('/data get entity @e[type=minecraft:item_display,tag=emberfall_starbit,limit=1,sort=nearest] Pos', 160);
    const p = pos(sp);
    const me = pos(await ask('/data get entity @s Pos', 160));
    const hp = /: ([\d.]+)f/.exec(await ask('/data get entity @s Health', 160));
    if (hp) hpSeries.push(+hp[1]);
    if (p) { track.push({ t: i, p, me }); }
    if (track.length > 12) break;     // one full lob is about 26 ticks; the slow sampler catches enough points
  }
  console.log('star samples:', track.length);
  track.forEach(o => console.log(`  t=${o.t} star=(${o.p.map(v => v.toFixed(1))}) player=(${o.me.map(v => v.toFixed(1))})`));
  if (track.length) {
    const ys = track.map(o => o.p[1]); const apex = Math.max(...ys), low = Math.min(...ys);
    const last = track[track.length - 1];
    console.log(`apex y=${apex.toFixed(1)} lowest y=${low.toFixed(1)} last star xz vs player xz: dx=${(last.p[0] - last.me[0]).toFixed(1)} dz=${(last.p[2] - last.me[2]).toFixed(1)}`);
  }
  console.log('player hp min/max over the run:', Math.min(...hpSeries), Math.max(...hpSeries));
  bot.quit(); setTimeout(() => process.exit(0), 300);
});
bot.on('error', e => console.log('ERR', e.message));
