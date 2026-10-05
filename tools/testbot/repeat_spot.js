const mineflayer = require('mineflayer');
const bot = mineflayer.createBot({ host: '127.0.0.1', port: 25565, username: 'EmberTester', version: '1.21.11', auth: 'offline' });
const sleep = ms => new Promise(r => setTimeout(r, ms));
bot.on('error', e => console.log('ERROR', e));
function snap() {
  const p = bot.entity.position.floored(), m = new Map();
  for (let dx=-30;dx<=30;dx++) for (let dz=-30;dz<=30;dz++) for (let dy=-10;dy<=12;dy++) {
    const b = bot.blockAt(p.offset(dx,dy,dz)); if (b) m.set(`${p.x+dx},${p.y+dy},${p.z+dz}`, b.name);
  }
  return m;
}
const drift = /kelp|water|seagrass/;
bot.once('spawn', async () => {
  await sleep(6000);
  bot.chat('/gamemode survival'); await sleep(400);
  bot.chat('/effect give @s minecraft:resistance 900 4 true'); await sleep(300);
  bot.chat('/difficulty peaceful'); await sleep(300);
  bot.chat('/tp @s 300 90 -300'); await sleep(4000);
  bot.chat('/spreadplayers 300 -300 0 1 false @s'); await sleep(3000);
  let real = 0;
  for (let i = 0; i < 6; i++) {
    const a = snap();
    bot.chat('/expedition'); await sleep(3500);
    bot.chat('/expedition leave'); await sleep(3500);
    const b = snap(); const d = [];
    for (const [k,v] of a) if (b.get(k)!==v && !(drift.test(v)||drift.test(b.get(k)||'')) && !(v==='grass_block'&&b.get(k)==='dirt')) d.push(`${k}: ${v} -> ${b.get(k)}`);
    real += d.length; console.log(`run ${i+1}: non-drift changes=${d.length} ${d.slice(0,4).join(' | ')}`);
  }
  console.log(`TOTAL non-drift changes over 6 runs at one spot: ${real}`);
  bot.quit(); process.exit(0);
});
