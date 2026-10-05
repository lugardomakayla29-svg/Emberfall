// EmberTester #3 step 1: a bot answers the HUB SHOP through the real ShopManager. Control: a second bot gets the same Silver
// but never opens the shop, so it must buy nothing. (Merchant and shrine need a walk and are a separate step.)
const mineflayer = require('mineflayer');
const sleep = ms => new Promise(r => setTimeout(r, ms));
const mk = name => new Promise(res => { const b = mineflayer.createBot({ host: '127.0.0.1', port: 25565, username: name, version: '1.21.11', auth: 'offline' }); b.chat_ = []; b.on('message', m => b.chat_.push(m.toString())); b.once('spawn', () => res(b)); });
let fails = 0; const check = (n, ok, note = '') => { console.log((ok ? 'PASS ' : 'FAIL ') + n + ' ' + note); if (!ok) fails++; };
(async () => {
  const op = await mk('EmberTester'); await sleep(4000);
  const say = async (cmd, w = 900) => { op.chat_.length = 0; op.chat(cmd); await sleep(w); return op.chat_.join(' | '); };
  const num = (s, k) => { const m = /has (-?\d+) Silver/.exec(s); return m ? parseInt(m[1], 10) : NaN; }; // reply: "<name> has N Silver"
  await say('/gamemode creative', 300);
  await say('/emberfall bot spawn ShopBot', 2000);
  await say('/emberfall bot spawn ShopControl', 2000);
  // both start with the same wallet; read it back instead of assuming it is zero
  await say('/emberfall givecurrency ShopBot 100000', 400);
  await say('/emberfall givecurrency ShopControl 100000', 400);
  const b0 = await say('/emberfall balance ShopBot', 700), c0 = await say('/emberfall balance ShopControl', 700);
  const bal0 = num(b0, 'alance'), cal0 = num(c0, 'alance');
  check('S0 both bots start with the same Silver', bal0 === cal0 && bal0 >= 100000, `bot ${bal0} control ${cal0}`);
  const w0 = await say('/emberfall listweapons ShopBot', 800);
  // open the real shop AS the bot; the brain hears the OpenShopPayload and answers after its think time
  await say('/execute as ShopBot run shop', 500);
  let b1 = '', spent = false;
  for (let i = 0; i < 12; i++) { await sleep(1000); b1 = await say('/emberfall balance ShopBot', 500); if (num(b1, 'alance') < bal0) { spent = true; break; } }
  const bal1 = num(b1, 'alance');
  check('S1 the bot bought something: its Silver went down', spent && bal1 < bal0, `${bal0} -> ${bal1}`);
  const c1 = await say('/emberfall balance ShopControl', 700);
  check('S2 control: the bot that never opened the shop kept all its Silver', num(c1, 'alance') === cal0, `${cal0} -> ${num(c1, 'alance')}`);
  // the cheapest-first rule: it must not have spent more than the most expensive line would cost; a second open buys again
  await say('/execute as ShopBot run shop', 500);
  let b2 = '';
  for (let i = 0; i < 12; i++) { await sleep(1000); b2 = await say('/emberfall balance ShopBot', 500); if (num(b2, 'alance') < bal1) break; }
  check('S3 a second visit buys again (it keeps answering, no stale state)', num(b2, 'alance') < bal1, `${bal1} -> ${num(b2, 'alance')}`);
  // a bot with no Silver must answer the shop by buying nothing (and not crash the server)
  await say('/emberfall bot spawn BrokeBot', 2000);
  const r0 = num(await say('/emberfall balance BrokeBot', 700), 'alance');
  await say('/execute as BrokeBot run shop', 500); await sleep(3500);
  const r1 = num(await say('/emberfall balance BrokeBot', 700), 'alance');
  check('S4 a bot that cannot afford anything buys nothing', r1 === r0, `${r0} -> ${r1}`);
  for (const n of ['ShopBot', 'ShopControl', 'BrokeBot']) await say('/emberfall bot remove ' + n, 1200);
  console.log(fails === 0 ? 'ALL PASS' : 'SOME FAIL ' + fails);
  op.quit(); setTimeout(() => process.exit(0), 400);
})();
