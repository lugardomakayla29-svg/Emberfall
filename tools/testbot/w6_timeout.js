// The timeout check on its own, in a state where an offer MUST open (carry the halberd, offer the broadsword).
const mineflayer = require('mineflayer');
const bot = mineflayer.createBot({ host: '127.0.0.1', port: 25565, username: 'EmberTester', version: '1.21.11', auth: 'offline' });
const sleep = ms => new Promise(r => setTimeout(r, ms));
const lines = []; bot.on('message', m => lines.push(m.toString()));
bot.once('spawn', async () => {
  await sleep(6000);
  const c = async (x, w = 900) => { bot.chat(x); await sleep(w); };
  const ask = async (x, w = 1000) => { const n = lines.length; bot.chat(x); await sleep(w); return lines.slice(n).filter(l => !/Teleported/.test(l)).join(' | '); };
  const pend = async () => { const r = await ask('/emberfall weaponpending EmberTester'); const m = r.match(/WPEND (\S+)/); return m ? m[1] : 'UNREADABLE:' + r.slice(0, 60); };
  const load = async () => { const r = await ask('/emberfall debugloadout EmberTester'); const m = r.match(/Loadout:\s*(.*?)\s*\|\s*held=(\S+)/); return m ? m[1].trim() : 'UNREADABLE:' + r.slice(0, 60); };
  await c('/gamemode survival', 700); await c('/character select juggernaut', 700);
  await c('/expedition leave', 800); await c('/expedition', 4000);
  const before = await load();
  await c('/emberfall weaponoffer EmberTester', 1200);
  const opened = await pend();
  const didOpen = opened !== 'none' && !opened.startsWith('UNREADABLE');
  console.log('T1 offer opened    :', opened, didOpen ? 'PASS' : 'FAIL (nothing to time out)');
  await sleep(4000);
  const mid = await pend();
  console.log('T2 still open @~5s :', mid === opened ? 'PASS (window not expired early)' : 'FAIL ' + mid);
  await sleep(6500);
  const after = await pend(); const l = await load();
  console.log('T3 declined @~11s  :', 'pending', after, '| weapons', l, '(was', before + ')', after === 'none' && l === before && didOpen ? 'PASS' : 'FAIL');
  await c('/expedition leave', 1200);
  bot.quit(); setTimeout(() => process.exit(0), 600);
});
