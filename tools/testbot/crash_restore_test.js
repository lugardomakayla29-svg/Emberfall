// Crash recovery: the run dies WITH the server (no clean leave). On the next join the stashed item must come back.
const mineflayer = require('mineflayer');
const phase = process.argv[2]; // "prep" then (server hard-killed) "check"
const bot = mineflayer.createBot({ host: '127.0.0.1', port: 25565, username: 'EmberTester', version: '1.21.11', auth: 'offline' });
const sleep = ms => new Promise(r => setTimeout(r, ms));
const lines = []; bot.on('message', m => lines.push(m.toString()));
bot.once('spawn', async () => {
  await sleep(6000);
  const c = async (x, w = 700) => { bot.chat(x); await sleep(w); };
  const ask = async (x, w = 800) => { const n = lines.length; bot.chat(x); await sleep(w); return lines.slice(n).join(' | '); };
  const id = async n => (await ask(`/data get entity EmberTester Inventory[{Slot:${n}b}]`)).match(/id: "([a-z_:]+)"/)?.[1] ?? 'empty';
  if (phase === 'prep') {
    await c('/gamemode survival'); await c('/character select juggernaut'); await c('/expedition leave', 900);
    await c('/clear EmberTester', 500);
    await c('/item replace entity EmberTester hotbar.0 with minecraft:diamond_sword', 500);
    await c('/expedition', 4000); await sleep(1500);
    console.log('P1 in run, slot0 =', await id(0));
    await c('/save-all flush', 3000);
    console.log('P2 saved, now hard-kill the server');
  } else {
    await sleep(2000);
    console.log('R1 after crash+rejoin slot0 =', await id(0), '(want minecraft:diamond_sword)');
    console.log('R1 weapon items in inventory =', (await ask('/data get entity EmberTester Inventory', 900)).match(/emberfall:[a-z_]+/g)?.length ?? 0, '(want 0)');
  }
  bot.quit(); setTimeout(() => process.exit(0), 500);
});
