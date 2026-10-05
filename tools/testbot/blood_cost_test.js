// Blood cost under a LIVE wave: how many particle packets/s does Blood add, against everything else?
const mineflayer = require('mineflayer');
const bot = mineflayer.createBot({ host: '127.0.0.1', port: 25565, username: 'EmberTester', version: '1.21.11', auth: 'offline' });
const sleep = ms => new Promise(r => setTimeout(r, ms));
const lines = []; bot.on('message', m => lines.push(m.toString()));
const ask = async (x, w = 600) => { const n = lines.length; bot.chat(x); await sleep(w); return lines.slice(n).join(' | '); };
const PAL = new Set([0xB01010, 0x780A0A, 0x6FD21E, 0x3F8A10, 0xFF7A1A, 0xC04A0A, 0xD8D2BC, 0xA8A38E]);
let rec = false, total = 0, blood = 0, bloodParticles = 0, allParticles = 0;
bot._client.on('packet', (d, m) => {
  if (!rec || m.name !== 'world_particles') return;
  total++; allParticles += d.numberOfParticles || d.count || 1;
  if (d.particle && String(d.particle.type) === 'dust' && d.particle.data) {
    const col = (typeof d.particle.data.color === 'number' ? d.particle.data.color : 0) & 0xFFFFFF;
    if (PAL.has(col)) { blood++; bloodParticles += d.numberOfParticles || d.count || 1; }
  }
});
bot.once('spawn', async () => {
  await sleep(4000);
  await ask('/gamemode survival'); await ask('/effect clear @s');
  await ask('/character select ranger', 500); await ask('/expedition leave', 800); await ask('/expedition', 3500);
  await ask('/effect give @s minecraft:resistance 999 4 true'); await ask('/effect give @s minecraft:regeneration 999 4 true');
  await sleep(8000);   // let the wave build up
  const alive = await ask('/execute if entity @e[type=#minecraft:undead]', 400);
  rec = true; await sleep(30000); rec = false;
  console.log(`30s live wave: ${total} particle packets total (${(total / 30).toFixed(1)}/s), of which blood ${blood} (${(blood / 30).toFixed(1)}/s) = ${(100 * blood / Math.max(1, total)).toFixed(1)}% of packets`);
  console.log(`particles: ${allParticles} total, blood ${bloodParticles} = ${(100 * bloodParticles / Math.max(1, allParticles)).toFixed(1)}%`);
  await ask('/expedition leave', 800);
  bot.quit(); process.exit(0);
});
