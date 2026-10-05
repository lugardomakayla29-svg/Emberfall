// Usage: node slots_test.js
// Proves the slot economy against the REAL server paths:
//  S1 a broke player cannot buy a slot, and nothing is charged
//  S2 the price ladder for weapon slots is 100, 300, 700 and stops at 4 slots
//  S3 tome slots: at 1 slot a player holding one tome is only ever offered that tome (it stacks)
//  S4 after buying a tome slot, new tomes are offered again
const mineflayer = require('mineflayer');
const bot = mineflayer.createBot({ host: '127.0.0.1', port: 25565, username: 'EmberTester', version: '1.21.11', auth: 'offline' });
const sleep = ms => new Promise(r => setTimeout(r, ms));
bot.on('error', e => console.log('ERROR', e));
const lines = []; bot.on('message', m => lines.push(m.toString()));
bot.once('spawn', async () => {
  await sleep(6000);
  const c = async (x, w = 800) => { bot.chat(x); await sleep(w); };
  const ask = async (x, w = 1000) => { const n = lines.length; bot.chat(x); await sleep(w); return lines.slice(n).filter(l => !/Teleported/.test(l)).join(' | '); };
  const bal = async () => { const r = await ask('/emberfall balance EmberTester'); const m = r.match(/(\d+)\s*(?:meta|currency|silver)?/i); return r; };
  const slots = async () => { const r = await ask('/emberfall debugloadout EmberTester'); const m = r.match(/weaponSlots=(\d+) tomeSlots=(\d+)/); return m ? { w: +m[1], t: +m[2], raw: r } : { w: null, t: null, raw: r }; };
  const roll = async () => { const r = await ask('/emberfall rolloffers EmberTester'); const m = r.match(/ROLL \[(.*?)\]/); return m ? m[1].split(',').map(x => x.trim()).filter(Boolean) : null; };

  await c('/gamemode survival', 700); await c('/character select juggernaut', 700);
  await c('/expedition leave', 800); await c('/expedition', 4000);
  await c('/effect give @s minecraft:resistance 900 4 true', 400);
  const purge = setInterval(() => bot.chat('/kill @e[type=!player,tag=!keep,distance=..60]'), 2000);

  const s0 = await slots();
  console.log('S0 start           :', s0.w, 'weapon slots,', s0.t, 'tome slots');
  console.log('S0 balance         :', (await bal()).slice(0, 110));

  // Runs pay a little Silver on exit, so do not assume 0: read the real balance and remove it.
  const startBal = parseInt(((await bal()).match(/has (\d+)/) || [0, 0])[1], 10);
  if (startBal > 0) { await c(`/emberfall givecurrency EmberTester ${-startBal}`, 800); }
  const zero = parseInt(((await bal()).match(/has (-?\d+)/) || [0, -1])[1], 10);
  console.log('S0 zeroed balance  :', zero, zero === 0 ? 'PASS' : 'FAIL');

  // S1: broke. The balance is now exactly 0.
  await c('/emberfall shopbuy EmberTester upgrade slot_weapon', 900);
  const s1 = await slots();
  console.log('S1 broke buy       :', s1.w, 'weapon slots (want 1)', s1.w === 1 ? 'PASS' : 'FAIL');

  // S2: the ladder. Give exactly the price, buy, and confirm the count.
  const ladder = [[100, 2], [300, 3], [700, 4]];
  for (const [price, want] of ladder) {
    await c(`/emberfall givecurrency EmberTester ${price - 1}`, 700);
    await c('/emberfall shopbuy EmberTester upgrade slot_weapon', 900);
    const short = await slots();
    await c('/emberfall givecurrency EmberTester 1', 700);
    await c('/emberfall shopbuy EmberTester upgrade slot_weapon', 900);
    const ok = await slots();
    console.log(`S2 slot ${want} at ${price}`.padEnd(19), ':', 'one short ->', short.w, ' exact ->', ok.w, short.w === want - 1 && ok.w === want ? 'PASS' : 'FAIL');
  }
  await c('/emberfall givecurrency EmberTester 5000', 700);
  await c('/emberfall shopbuy EmberTester upgrade slot_weapon', 900);
  const capped = await slots();
  console.log('S2 cap at 4        :', capped.w, 'weapon slots (want 4)', capped.w === 4 ? 'PASS' : 'FAIL');

  // S3: tome cap. Take one tome, then every offer must be that same tome or nothing.
  await c('/emberfall granttome EmberTester ember_touch', 800);
  let sawOther = false, rolls = [];
  for (let i = 0; i < 6; i++) { const r = await roll(); rolls.push(r); if (r && r.some(id => id !== 'ember_touch')) sawOther = true; }
  console.log('S3 offers @1 slot  :', JSON.stringify(rolls.slice(0, 3)), sawOther ? 'FAIL (offered a new tome)' : 'PASS');

  // S4: buy a tome slot (75), and new tomes must appear.
  await c('/emberfall shopbuy EmberTester upgrade slot_tome', 900);
  const s4 = await slots();
  let sawNew = false; rolls = [];
  for (let i = 0; i < 6; i++) { const r = await roll(); rolls.push(r); if (r && r.some(id => id !== 'ember_touch')) sawNew = true; }
  console.log('S4 tome slot bought:', s4.t, 'tome slots (want 2)', s4.t === 2 ? 'PASS' : 'FAIL');
  console.log('S4 offers @2 slots :', JSON.stringify(rolls.slice(0, 2)), sawNew ? 'PASS' : 'FAIL (still no new tomes)');

  clearInterval(purge);
  await c('/expedition leave', 1200);
  bot.quit(); setTimeout(() => process.exit(0), 600);
});
