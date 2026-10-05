// Probe: print loadout + offers around a paid reroll. FRESH world. Read-only diagnosis.
const mineflayer = require('mineflayer');
const bot = mineflayer.createBot({ host: '127.0.0.1', port: 25565, username: 'EmberTester', version: '1.21.11', auth: 'offline' });
const sleep = ms => new Promise(r => setTimeout(r, ms));
const lines = []; bot.on('message', m => lines.push(m.toString()));
bot.once('spawn', async () => {
  await sleep(6000);
  const ask = async (x, w = 1000) => { const n = lines.length; bot.chat(x); await sleep(w); return lines.slice(n).join(' | ').slice(0, 260); };
  await ask('/gamemode survival', 700); await ask('/character select juggernaut', 700);
  await ask('/expedition leave', 800); await ask('/expedition', 4000); await sleep(2000);
  console.log('A loadout        :', await ask('/emberfall debugloadout EmberTester'));
  console.log('A charges        :', await ask('/emberfall tomecharges EmberTester'));
  console.log('B open           :', await ask('/emberfall tomeopen EmberTester 2', 1500));
  console.log('B offers         :', await ask('/emberfall tomeoffers EmberTester'));
  console.log('B loadout        :', await ask('/emberfall debugloadout EmberTester'));
  await ask('/emberfall debugpickup EmberTester gold 30', 800); await sleep(1500);
  console.log('C charges (30g)  :', await ask('/emberfall tomecharges EmberTester'));
  console.log('C reroll         :', await ask('/emberfall tomereroll EmberTester 2', 1500));
  console.log('C offers         :', await ask('/emberfall tomeoffers EmberTester'));
  console.log('C charges        :', await ask('/emberfall tomecharges EmberTester'));
  console.log('C loadout        :', await ask('/emberfall debugloadout EmberTester'));
  await ask('/expedition leave', 1000); bot.quit(); setTimeout(() => process.exit(0), 500);
});
