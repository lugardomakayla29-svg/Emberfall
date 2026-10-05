const mineflayer = require('mineflayer');
const bot = mineflayer.createBot({ host: '127.0.0.1', port: 25565, username: 'EmberTester', version: '1.21.11', auth: 'offline' });
const sleep = ms => new Promise(r => setTimeout(r, ms));
const lines = []; bot.on('message', m => lines.push(m.toString()));
const ask = async (x, w = 500) => { const n = lines.length; bot.chat(x); await sleep(w); return lines.slice(n).join(' | '); };
const G = '@e[type=emberfall:ember_guardian,limit=1]';
const pos = async sel => { for (let k = 0; k < 4; k++) { const r = await ask(`/data get entity ${sel} Pos`, 450); const m = /\[(-?[\d.]+)d, (-?[\d.]+)d, (-?[\d.]+)d\]/.exec(r); if (m) return [+m[1], +m[2], +m[3]]; } return null; };
bot.once('spawn', async () => {
  await sleep(6500);
  await ask('/kill @e[type=!player]', 600); await ask('/kill @e[type=minecraft:item_display]', 600); await sleep(1200);
  await ask('/gamemode survival'); await ask('/effect give @s minecraft:resistance 999 4 true', 250); await ask('/effect give @s minecraft:regeneration 999 4 true', 250);
  await ask('/character select juggernaut', 600); await ask('/expedition', 1500); await sleep(1500);
  await ask('/emberfall boss 0', 900); await ask('/emberfall wavestop 0', 300);
  await ask(`/execute as ${G} at @s run tp EmberTester ~15 ~ ~`, 300);
  const b0 = await pos(G); console.log('boss', b0.map(v => v.toFixed(1)).join(' '), ' target line goes +x');
  // Read each pylon by tagging it one at a time via nearest-sort from the boss.
  // Tag pylons 1..4 in nearest-first order (one tag each) so each can be addressed alone.
  for (let i = 0; i < 4; i++) await ask(`/tag @e[type=emberfall:cinder_pylon,tag=!pt,sort=nearest,limit=1] add pt${i}`, 300), await ask(`/tag @e[type=emberfall:cinder_pylon,tag=pt${i}] add pt`, 250);
  for (let i = 0; i < 4; i++) {
    const p = await pos(`@e[type=emberfall:cinder_pylon,tag=pt${i},limit=1]`);
    if (p) { const dx = p[0] - b0[0], dz = p[2] - b0[2]; console.log(`pylon ${i}: dx=${dx.toFixed(1)} dz=${dz.toFixed(1)} dist=${Math.hypot(dx, dz).toFixed(1)}  ahead on +x line: ${dx > 0 && Math.abs(dz) < 1.6}`); } else console.log(`pylon ${i}: not found`);
  }
  await ask('/kill @e[type=emberfall:ember_guardian]', 800); await sleep(2000);
  bot.quit(); setTimeout(() => process.exit(0), 300);
});
bot.on('error', e => console.log('ERR', e.message));
