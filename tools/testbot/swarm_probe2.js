const mineflayer = require('mineflayer'); const sleep = ms => new Promise(r => setTimeout(r, ms));
const bot = mineflayer.createBot({ host: '127.0.0.1', port: 25565, username: 'EmberTester', version: '1.21.11', auth: 'offline' });
const lines = []; bot.on('message', m => lines.push(m.toString()));
const ask = async (x, w = 600) => { const n = lines.length; bot.chat(x); await sleep(w); return lines.slice(n).join(' | '); };
bot.once('spawn', async () => {
  await sleep(6500); await ask('/gamemode survival'); await ask('/effect give @s minecraft:resistance 999 4 true', 300);
  await ask('/character select juggernaut', 600); await ask('/expedition', 1500);
  for (let i = 0; i < 60; i++) { await sleep(2000); if (/expedition/.test(await ask('/data get entity @s Dimension', 400))) break; }
  await sleep(1500);
  console.log('START', await ask('/emberfall relic swarm EmberTester start', 800));
  await ask('/emberfall relic swarm EmberTester skip 1200', 700); await sleep(2500);
  const pr = (await ask('/emberfall relic swarm EmberTester portal', 700)).match(/portal (-?\d+) (-?\d+) (-?\d+)/); console.log('PORTAL', pr && pr.slice(1).join(','));
  await ask(`/tp @s ${+pr[1] + 0.5} ${pr[2]} ${+pr[3] + 0.5}`, 500);
  for (let i = 0; i < 10; i++) {
    const pos = await ask('/data get entity @s Pos', 500); const sw = await ask('/emberfall relic swarm EmberTester state', 500);
    console.log(i, pos.replace(/.*data: /, '').slice(0, 60), '|', sw.replace(/.*RELIC /, '').slice(0, 90), '| chat:', lines.filter(l => /Hold still|escape/i.test(l)).length);
    await sleep(600);
  }
  console.log('DIM', await ask('/data get entity @s Dimension', 500));
  bot.quit(); setTimeout(() => process.exit(0), 300);
});
