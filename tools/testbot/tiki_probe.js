const mineflayer = require('mineflayer');
const bot = mineflayer.createBot({ host: '127.0.0.1', port: 25565, username: 'EmberTester', version: '1.21.11', auth: 'offline' });
const sleep = ms => new Promise(r => setTimeout(r, ms));
const lines = []; bot.on('message', m => lines.push(m.toString()));
const ask = async (x, w = 900) => { const n = lines.length; bot.chat(x); await sleep(w); return lines.slice(n).join(' | '); };
bot.once('spawn', async () => {
  await sleep(6500);
  await ask('/gamemode creative'); // creative: not a target, so nothing attacks the test subject
  for (const [label, cmd] of [['FODDER', '/emberfall spawnveteran tiki_magma'], ['ELITE', '/emberfall spawnelite tiki_magma'], ['CORRUPTED', '/emberfall spawnelite tiki_magma_corrupted']]) {
    await ask('/kill @e[type=!player]', 700); await sleep(2500);
    const sp = await ask(cmd, 1200);
    await sleep(2500);
    const r = await ask('/emberfall tikidump EmberTester', 1000);
    console.log(`=== ${label}: ${sp.slice(0, 60)}`);
    console.log(r.replace(/ \[/g, '\n  [').slice(0, 900));
  }
  bot.quit(); setTimeout(() => process.exit(0), 300);
});
bot.on('error', e => console.log('ERR', e.message));
