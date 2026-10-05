const mineflayer = require('mineflayer');
const bot = mineflayer.createBot({ host: '127.0.0.1', port: 25565, username: 'EmberTester', version: '1.21.11', auth: 'offline' });
const sleep = ms => new Promise(r => setTimeout(r, ms));
const lines = []; bot.on('message', m => lines.push(m.toString()));
const ask = async (x, w = 900) => { const n = lines.length; bot.chat(x); await sleep(w); return lines.slice(n).join(' | '); };
const snap = async (label) => {
  const r = await ask('/emberfall devdump EmberTester', 1100);
  const m = /DEVDUMP px=([\d.,-]+)(.*) parts=(\d+)/.exec(r);
  if (!m) { console.log(label, 'NO DUMP:', r.slice(0, 120)); return; }
  const py = parseFloat(m[1].split(',')[1]);
  const parts = [...m[2].matchAll(/\[(\w+)(?:#\d+)? ([\d.-]+),([\d.-]+),([\d.-]+)( invis)?\]/g)]
    .map(x => `${x[1].replace('Devourer', '')} y${(parseFloat(x[3]) - py).toFixed(1)}${x[5] ? 'I' : ''}`);
  console.log(`${label.padEnd(10)} parts=${m[3]} (y relative to player feet ${py.toFixed(1)}): ${parts.join('  ')}`);
};
bot.once('spawn', async () => {
  await sleep(6500);
  await ask('/kill @e[type=!player]', 500);
  await ask('/gamemode survival'); await ask('/effect give @s minecraft:resistance 999 4 true', 300);
  await ask('/effect give @s minecraft:regeneration 999 4 true', 300);
  await ask('/character select juggernaut', 600);
  console.log('EXPEDITION:', (await ask('/expedition', 1500)).slice(0, 90));
  await sleep(1500);
  console.log('BOSS:', (await ask('/emberfall bossdevourer 0', 1500)).slice(0, 110));
  for (const [label, wait] of [['t+1s', 1000], ['t+3s', 2000], ['t+5s', 2000], ['t+7s', 2000], ['t+9s', 2000], ['t+11s', 2000], ['t+13s', 2000]]) {
    await sleep(wait); await snap(label);
  }
  bot.quit(); setTimeout(() => process.exit(0), 300);
});
bot.on('error', e => console.log('ERR', e.message));
