// Crossing level 5 must give the level-5 Tome pick FIRST and THEN a weapon offer, never one instead of the other.
const mineflayer = require('mineflayer');
const bot = mineflayer.createBot({ host: '127.0.0.1', port: 25565, username: 'EmberTester', version: '1.21.11', auth: 'offline' });
const sleep = ms => new Promise(r => setTimeout(r, ms));
const lines = []; bot.on('message', m => lines.push(m.toString()));
bot.once('spawn', async () => {
  await sleep(6000);
  const c = async (x, w = 900) => { bot.chat(x); await sleep(w); };
  const ask = async (x, w = 1000) => { const n = lines.length; bot.chat(x); await sleep(w); return lines.slice(n).filter(l => !/Teleported/.test(l)).join(' | '); };
  const tomes = async () => { const r = await ask('/emberfall tomeoffers EmberTester'); const m = r.match(/offers: \[(.*?)\]/); return m ? (m[1] === '' ? 'none' : 'open') : 'UNREADABLE:' + r.slice(0, 60); };
  const weap = async () => { const r = await ask('/emberfall weaponpending EmberTester'); const m = r.match(/WPEND (\S+)/); return m ? (m[1] === 'none' ? 'none' : 'open') : 'UNREADABLE:' + r.slice(0, 60); };
  await c('/gamemode survival', 700); await c('/character select juggernaut', 700);
  await c('/expedition leave', 800); await c('/expedition', 4000);
  await c('/effect give @s minecraft:resistance 900 4 true', 400);

  await c('/xp set @s 5 levels', 2500);
  // Levels 1 to 5 each owe a tome pick, so five tome screens come first (skip each), and only then the weapon offer.
  const log = []; let lvl = 0;
  for (let i = 0; i < 8; i++) {
    const t = await tomes(), w = await weap();
    log.push(`t=${t} w=${w}`);
    if (t === 'open') { lvl++; await c(`/emberfall tomeskip EmberTester ${lvl}`, 1500); continue; }
    if (w === 'open') { await c('/emberfall weaponanswer EmberTester skip', 1500); log.push('weapon skipped'); break; }
    await sleep(1500);
  }
  console.log(log.join('\n'));
  const tomeScreens = log.filter(x => x.startsWith('t=open')).length;
  const weaponAfter = log.indexOf('weapon skipped');
  const lastTomeIdx = log.map((x, i) => x.startsWith('t=open') ? i : -1).filter(i => i >= 0).pop();
  console.log('tome screens seen  :', tomeScreens, tomeScreens === 5 ? 'PASS (one per level 1..5)' : 'FAIL (want 5)');
  console.log('weapon offer seen  :', weaponAfter >= 0 ? 'PASS' : 'FAIL (never opened)');
  console.log('weapon came LAST   :', weaponAfter > lastTomeIdx ? 'PASS' : 'FAIL');
  console.log('level-5 tome kept  :', tomeScreens >= 5 ? 'PASS (milestone did not eat the pick)' : 'FAIL');
  await c('/expedition leave', 1200);
  bot.quit(); setTimeout(() => process.exit(0), 600);
});
