// Repro: does a pending "guaranteed weapon offer" flag survive leaving a run? (fresh world)
const mineflayer = require('mineflayer');
const bot = mineflayer.createBot({ host: '127.0.0.1', port: 25565, username: 'EmberTester', version: '1.21.11', auth: 'offline' });
const sleep = ms => new Promise(r => setTimeout(r, ms));
const lines = []; bot.on('message', m => lines.push(m.toString()));
bot.on('error', e => console.log('ERROR', e));
bot.once('spawn', async () => {
  await sleep(6000);
  const c = async (x, w = 700) => { bot.chat(x); await sleep(w); };
  const ask = async (x, w = 800) => { const n = lines.length; bot.chat(x); await sleep(w); return lines.slice(n).join(' | '); };
  const flag = async () => { const r = await ask('/emberfall guaranteeflag EmberTester'); const m = r.match(/GUARANTEEFLAG (true|false)/); return m ? m[1] : 'UNREADABLE'; };

  await c('/gamemode survival'); await c('/character select juggernaut');
  await c('/expedition leave', 800);
  console.log('L0 before any run        :', await flag(), '(expect false)');

  await c('/expedition', 4000); await sleep(1500);
  const set = await ask('/emberfall debugguarantee EmberTester');
  console.log('L1 set inside run        :', await flag(), '(expect true)');

  await c('/expedition leave', 1500);
  console.log('L2 right after leaving   :', await flag(), '(clean = false; TRUE means it leaked)');

  await c('/expedition', 4000); await sleep(1500);
  console.log('L3 inside a NEW run      :', await flag(), '(clean = false; TRUE means it leaked into the next run)');
  await c('/expedition leave', 1000);
  bot.quit(); setTimeout(() => process.exit(0), 500);
});
