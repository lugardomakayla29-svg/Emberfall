// EmberTester #3 step 2: a bot WALKS to a standing Testificate merchant, opens the real screen and buys a relic through the
// real MerchantManager. Control: a second bot in the same run with no gold never goes and never buys.
const mineflayer = require('mineflayer');
const sleep = ms => new Promise(r => setTimeout(r, ms));
const mk = name => new Promise(res => { const b = mineflayer.createBot({ host: '127.0.0.1', port: 25565, username: name, version: '1.21.11', auth: 'offline' }); b.chat_ = []; b.on('message', m => b.chat_.push(m.toString())); b.once('spawn', () => res(b)); });
let fails = 0; const check = (n, ok, note = '') => { console.log((ok ? 'PASS ' : 'FAIL ') + n + ' ' + note); if (!ok) fails++; };
(async () => {
  const op = await mk('EmberTester'); await sleep(4000);
  const say = async (cmd, w = 900) => { op.chat_.length = 0; op.chat(cmd); await sleep(w); return op.chat_.join(' | '); };
  const rs = async n => (await say(`/emberfall relic state ${n}`, 700));
  const num = (s, k) => { const m = new RegExp(k + '=(-?\\d+)').exec(s); return m ? +m[1] : NaN; };
  const bst = async n => (await say(`/emberfall bot state ${n}`, 700)).replace(/.*BOT state /, '');
  await say('/gamemode creative', 300);
  await say('/emberfall bot spawn Buyer', 2000);
  await say('/emberfall bot spawn Broke', 2000);
  for (const n of ['Buyer', 'Broke']) {
    await say(`/effect give @a[name=${n}] minecraft:resistance 999 4 true`, 300);
    await say(`/effect give @a[name=${n}] minecraft:regeneration 999 4 true`, 300);
  }
  const r1 = await say('/emberfall bot run Buyer ranger', 1500);
  const r2 = await say('/emberfall bot run Broke ranger', 1500);
  check('T0 both bots start a run', /run Buyer ok/.test(r1) && /run Broke ok/.test(r2), (r1 + r2).slice(0, 110));
  let s = '';
  for (let i = 0; i < 70; i++) { await sleep(2000); s = await bst('Buyer'); if (/run=\d/.test(s) && /weapons=\w/.test(s)) break; }
  check('T1 the buyer is in a run with a weapon', /run=\d/.test(s) && /weapons=\w/.test(s), s.slice(0, 120));
  const slot = (/run=(\d+)/.exec(s) || [])[1] || '0';
  await say(`/emberfall wavestop ${slot}`, 500);                  // no waves: this test is about the merchant, not survival
  await say('/kill @e[type=!player,type=!minecraft:item_display,type=!minecraft:interaction,type=!emberfall:testificate]', 600);
  const g0 = num(await rs('Buyer'), 'wallet'), t0 = num(await rs('Buyer'), 'total');
  await say('/emberfall relic gold Buyer 2000', 500);
  const g1 = num(await rs('Buyer'), 'wallet');
  check('T2 the buyer has gold and the broke bot has none', g1 >= 2000 && num(await rs('Broke'), 'wallet') === 0, `buyer ${g0}->${g1}`);
  const summon = await say('/emberfall relic merchant Buyer 0', 1500);
  check('T3 a merchant arrives', /arrived/.test(summon), summon.slice(0, 90));
  const mp = /\[(-?[\d.]+)d, (-?[\d.]+)d, (-?[\d.]+)d\]/.exec(await say('/data get entity @e[type=emberfall:testificate,limit=1] Pos', 700));
  const bp = /pos=(-?[\d.]+),(-?[\d.]+)/.exec(await bst('Buyer'));
  if (mp && bp) console.log('INFO merchant is ' + Math.hypot(+mp[1] - +bp[1], +mp[3] - +bp[2]).toFixed(1) + ' blocks from the buyer at summon');
  // the bot must get there and buy with no further help; give it up to 60 s
  let bought = false, st = '';
  for (let i = 0; i < 30; i++) { await sleep(2000); st = await rs('Buyer'); if (num(st, 'total') > t0) { bought = true; break; } }
  const g2 = num(st, 'wallet'), t2 = num(st, 'total');
  check('T4 the buyer walked over and bought a relic: relics went up', bought && t2 === t0 + 1, `relics ${t0} -> ${t2}`);
  check('T5 and paid for it: gold went down', g2 < g1, `gold ${g1} -> ${g2}`);
  const br = await rs('Broke');
  const brWallet = num(await say('/emberfall relic state Broke', 700), 'wallet');
  check('T6 control: the bot with almost no gold bought no relic and did not spend (gold stayed under the cheapest price)', num(br, 'total') === 0 && brWallet < 30, 'relics ' + num(br, 'total') + ' wallet ' + brWallet);
  await say('/emberfall bot remove Buyer', 1500); await say('/emberfall bot remove Broke', 1500);
  console.log(fails === 0 ? 'ALL PASS' : 'SOME FAIL ' + fails);
  op.quit(); setTimeout(() => process.exit(0), 400);
})();
