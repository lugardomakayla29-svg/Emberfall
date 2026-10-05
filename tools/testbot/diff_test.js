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
function diff(a,b){ const d=[]; for (const [k,v] of a) if (b.get(k)!==v) d.push(`${k}: ${v} -> ${b.get(k)}`); return d; }
bot.once('spawn', async () => {
  await sleep(6000);
  bot.chat('/gamemode survival'); await sleep(500);
  bot.chat('/effect give @s minecraft:resistance 600 4 true'); await sleep(300);
  // CONTROL: two snapshots 20s apart with NO expedition at all -> measures natural world drift
  const c1 = snap(); await sleep(30000); const c2 = snap();
  const cd = diff(c1,c2); console.log(`CONTROL (no run, 20s apart): ${cd.length} blocks changed`, cd.slice(0,6));
  // REAL: around an expedition
  const a = snap();
  bot.chat('/expedition'); await sleep(22000);
  bot.chat('/expedition leave'); await sleep(4000);
  const b = snap();
  const d = diff(a,b); console.log(`RUN (start -> after leave): ${d.length} blocks changed`);
  const kinds = {}; d.forEach(x => { const t = x.split(': ')[1]; kinds[t]=(kinds[t]||0)+1; });
  console.log('change kinds:', JSON.stringify(kinds)); console.log(d.slice(0,10).join('\n'));
  bot.quit(); process.exit(0);
});
