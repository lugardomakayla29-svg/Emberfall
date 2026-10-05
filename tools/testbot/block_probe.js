// Repeat the move scenario until the boss fails to advance, then dump the blocks around it.
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
  const a = await pos(G); await sleep(5000); const b = await pos(G);
  const moved = Math.hypot(a[0] - b[0], a[2] - b[2]);
  console.log('boss', a.map(v => v.toFixed(1)).join(' '), '->', b.map(v => v.toFixed(1)).join(' '), 'moved', moved.toFixed(2));
  const pl = await pos('@s'); if (pl) console.log('final gap to player', Math.hypot(b[0] - pl[0], b[2] - pl[2]).toFixed(1), 'player y', pl[1].toFixed(1), 'boss y', b[1].toFixed(1));
  if (moved < 1.0) {
    const bx = Math.floor(b[0]), by = Math.floor(b[1]), bz = Math.floor(b[2]);
    console.log('STUCK. blocks ahead of the boss (+x), rows y-1..y+2, x from bx to bx+3, at z=bz:');
    for (let y = by + 2; y >= by - 1; y--) {
      let row = ''; for (let x = bx; x <= bx + 3; x++) { const r = await ask(`/execute if block ${x} ${y} ${bz} minecraft:air`, 130); row += /passed/.test(r) ? ' air ' : ' SOLID'; }
      console.log(`y=${y - by >= 0 ? '+' : ''}${y - by}:${row}`);
    }
    const r = await ask(`/execute as ${G} at @s run data get entity @s OnGround`, 400); console.log('OnGround:', r.slice(0, 70));
  }
  await ask('/kill @e[type=emberfall:ember_guardian]', 800); await sleep(2000);
  bot.quit(); setTimeout(() => process.exit(0), 300);
});
bot.on('error', e => console.log('ERR', e.message));
