const mineflayer = require('mineflayer'); const sleep = ms => new Promise(r => setTimeout(r, ms));
const bot = mineflayer.createBot({ host: '127.0.0.1', port: 25565, username: 'EmberTester', version: '1.21.11', auth: 'offline' });
const lines = []; bot.on('message', m => lines.push(m.toString()));
const ask = async (x, w = 600) => { const n = lines.length; bot.chat(x); await sleep(w); return lines.slice(n).join(' | '); };
bot.once('spawn', async () => {
  await sleep(6500); await ask('/gamemode survival'); await ask('/effect give @s minecraft:resistance 999 4 true', 300); await ask('/effect give @s minecraft:regeneration 999 4 true', 300); await ask('/effect give @s minecraft:fire_resistance 999 0 true', 300);
  await ask('/character select juggernaut', 600); await ask('/expedition', 1500);
  for (let i = 0; i < 60; i++) { await sleep(2000); if (/expedition/.test(await ask('/data get entity @s Dimension', 400))) break; }
  await sleep(1500); await ask('/emberfall relic swarm EmberTester start', 800); await ask('/emberfall relic swarm EmberTester skip 1200', 700);
  const t0 = Date.now(); let last = '';
  for (let i = 0; i < 40; i++) {
    const e = await ask('/data get entity @s active_effects', 450); const has = ['nausea', 'darkness', 'slowness'].filter(k => e.includes('minecraft:' + k)).join(',') || '-';
    if (has !== last) { console.log(((Date.now() - t0) / 1000).toFixed(1) + 's', has); last = has; }
    await sleep(100);
  }
  bot.quit(); setTimeout(() => process.exit(0), 300);
});
