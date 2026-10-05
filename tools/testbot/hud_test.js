// Usage: node hud_test.js   (FRESH world). Proves what the server sends to the bottom-left panel.
const mineflayer = require('mineflayer');
const bot = mineflayer.createBot({ host: '127.0.0.1', port: 25565, username: 'EmberTester', version: '1.21.11', auth: 'offline' });
const sleep = ms => new Promise(r => setTimeout(r, ms));
bot.on('error', e => console.log('ERROR', e));
const lines = []; bot.on('message', m => lines.push(m.toString()));
let packets = 0; bot.on('packet', (d, meta) => { if (meta.name === 'custom_payload' && d.channel && String(d.channel).includes('hud_state')) packets++; });
bot.once('spawn', async () => {
  await sleep(6000);
  const c = async (x, w = 900) => { bot.chat(x); await sleep(w); };
  const ask = async (x, w = 1000) => { const n = lines.length; bot.chat(x); await sleep(w); return lines.slice(n).filter(l => !/Teleported/.test(l)).join(' | '); };
  const hud = async () => { const r = await ask('/emberfall hudstate EmberTester'); const m = r.match(/HUD sends=(\d+) (\S+)/); return m ? { n: +m[1], s: m[2] } : { n: -1, s: 'UNREADABLE:' + r.slice(0, 60) }; };
  const ok = b => b ? 'PASS' : 'FAIL';

  await c('/gamemode survival', 700); await c('/character select juggernaut', 700);
  const pre = await hud();
  console.log('H0 before run      :', pre.s, ok(pre.s === 'none'));

  await c('/expedition leave', 800); await c('/expedition', 4000);
  await sleep(2500);
  const h1 = await hud();
  console.log('H1 enter run       :', h1.s, 'sends', h1.n, ok(h1.s.startsWith('1/1|war_halberd,|') && h1.n === 1));

  await sleep(6000);
  const h2 = await hud();
  console.log('H2 idle 6s         :', 'sends', h1.n, '->', h2.n, ok(h2.n === h1.n && h2.s === h1.s));

  await c('/emberfall granttome EmberTester ember_touch', 1200); await sleep(1500);
  const h3 = await hud();
  console.log('H3 tome taken      :', h3.s, ok(h3.s.includes('ember_touch:1') && h3.n === h2.n + 1));

  await c('/emberfall givecurrency EmberTester 100', 800);
  await c('/emberfall shopbuy EmberTester upgrade slot_weapon', 1200); await sleep(1500);
  const h4 = await hud();
  console.log('H4 slot bought     :', h4.s, ok(h4.s.startsWith('2/1|') && h4.n === h3.n + 1));

  await c('/expedition leave', 1500); await sleep(1000);
  const h5 = await hud();
  console.log('H5 left run        :', h5.s, ok(h5.s === 'none'));
  console.log('client packets seen:', packets, '(informational)');
  bot.quit(); setTimeout(() => process.exit(0), 600);
});
