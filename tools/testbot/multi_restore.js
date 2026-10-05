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
  bot.chat('/gamemode survival'); await sleep(400);
  bot.chat('/effect give @s minecraft:resistance 900 4 true'); await sleep(300);
  bot.chat('/difficulty peaceful'); await sleep(300);
  const spots = [[0,0],[400,0],[0,400],[-400,0],[0,-400],[300,300],[-300,300],[300,-300]];
  let totalBad = 0, plantRuns = 0;
  for (const [x,z] of spots) {
    bot.chat(`/tp @s ${x} 250 ${z}`); await sleep(4000);
    bot.chat('/spreadplayers ' + x + ' ' + z + ' 0 1 false @s'); await sleep(3000);
    const a = snap(); const plants = [...a.values()].filter(n => /grass|fern|flower|dandelion|poppy|tulip|bush|azure|cornflower/.test(n)).length;
    bot.chat('/expedition'); await sleep(3500);
    bot.chat('/expedition leave'); await sleep(3500);
    const b = snap(); const d = diff(a,b);
    if (plants > 20) plantRuns++;
    totalBad += d.length;
    console.log(`spot ${x},${z}: plants=${plants} unrestored=${d.length} ${d.slice(0,3).join(' | ')}`);
  }
  console.log(`SUMMARY: ${spots.length} runs, ${plantRuns} plant-heavy, total unrestored blocks = ${totalBad}`);
  bot.quit(); process.exit(0);
});
