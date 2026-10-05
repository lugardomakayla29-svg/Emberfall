// Usage: node gold_reroll_test.js   (FRESH world). Screens auto-resolve after 8s, so every reroll
// happens on a screen opened moments earlier, with gold granted BEFORE opening.
const mineflayer = require('mineflayer');
const bot = mineflayer.createBot({ host: '127.0.0.1', port: 25565, username: 'EmberTester', version: '1.21.11', auth: 'offline' });
const sleep = ms => new Promise(r => setTimeout(r, ms));
bot.on('error', e => console.log('ERROR', e));
const lines = []; bot.on('message', m => lines.push(m.toString()));
bot.once('spawn', async () => {
  await sleep(6000);
  const c = async (x, w = 700) => { bot.chat(x); await sleep(w); };
  const ask = async (x, w = 800) => { const n = lines.length; bot.chat(x); await sleep(w); return lines.slice(n).join(' | '); };
  const state = async () => {
    const r = await ask('/emberfall tomecharges EmberTester');
    const m = r.match(/rerollsRemaining=(\d+) banishesRemaining=(\d+) goldPrice=(\d+) gold=(\d+)/);
    return m ? { rr: +m[1], price: +m[3], gold: +m[4] } : { rr: -1, price: -1, gold: -1 };
  };
  const offers = async () => { const r = await ask('/emberfall tomeoffers EmberTester'); const m = r.match(/offers: \[(.*?)\]/); return m ? m[1] : 'UNREADABLE'; };
  const ok = b => b ? 'PASS' : 'FAIL';
  let lvl = 10;
  // Opens a fresh screen at a new level and returns { lvl, offers } - proves it OPENED (non-empty) before we judge anything.
  const open = async () => { lvl++; await c(`/emberfall tomeopen EmberTester ${lvl}`, 900); return { lvl, o: await offers() }; };
  const reroll = async l => c(`/emberfall tomereroll EmberTester ${l}`, 900);
  const skip = async l => c(`/emberfall tomeskip EmberTester ${l}`, 500);
  const setGold = async n => { // bring balance to exactly n, retrying if a stray kill coin lands meanwhile
    let s = await state();
    for (let t = 0; t < 4 && s.gold !== n; t++) {
      if (s.gold > n) await c(`/emberfall debugspendgold EmberTester ${s.gold - n}`, 500);
      else await c(`/emberfall debugpickup EmberTester gold ${n - s.gold}`, 600), await sleep(1500);
      s = await state();
    }
    return s;
  };
  // Opens a screen, then re-reads gold; a boundary test is only valid if the balance is still exactly what we set.
  // A live run pays stray kill coins. Repeat set, open, read, reroll until the balance read just BEFORE the reroll is exactly n,
  // so a coin landing between the open and the reroll cannot turn a 'one short' step into an exact one.
  const cleanStep = async n => {
    for (let t = 0; t < 6; t++) {
      await setGold(n);
      const a = await open(); const pre = await state();
      if (pre.gold !== n) { await skip(a.lvl); continue; }
      await reroll(a.lvl);
      return { a, pre, valid: true };
    }
    return { a: { o: '', lvl: 0 }, pre: null, valid: false };
  };
  const openAt = async n => { const a = await open(); const g = await state(); return { ...a, gold: g.gold, price: g.price, valid: g.gold === n }; };

  await c('/gamemode survival', 700); await c('/character select juggernaut', 700);
  await c('/expedition leave', 800); await c('/expedition', 4000); await sleep(2000);
  // A live wave kills an unprotected bot after ~40-70 s and the run ends 'fallen' (which also strips effects), so protect it.
  await c('/effect give @s minecraft:resistance 999 4 true', 300); await c('/effect give @s minecraft:regeneration 999 4 true', 300);

  const s0 = await state();
  console.log('G0 start            :', JSON.stringify(s0), ok(s0.rr === 0 && s0.price === 30));

  // G1: broke -> refused, nothing changes. The screen must be proven open first.
  await setGold(0);
  let a = await open();
  await reroll(a.lvl);
  const o1 = await offers(), s1 = await state();
  console.log('G1 broke (0g)       :', 'opened=' + (a.o !== ''), 'offers same=' + (a.o === o1), 'gold', s1.gold, 'price', s1.price, ok(a.o !== '' && a.o === o1 && s1.gold === 0 && s1.price === 30));
  await skip(a.lvl);

  // G2: 29 gold = one short of 30 -> refused
  await setGold(29);
  a = await openAt(29); await reroll(a.lvl);
  const o2 = await offers(), s2 = await state();
  console.log('G2 one short (29)   :', 'offers same=' + (a.o === o2), 'gold', s2.gold, 'price', s2.price, ok(a.valid && a.o !== '' && a.o === o2 && s2.gold === 29 && s2.price === 30));
  await skip(a.lvl);

  // G3: exactly 30 -> succeeds, costs exactly 30, price becomes 60
  { const cs = await cleanStep(30); a = cs.a;
  const o3 = await offers(), s3 = await state();
  console.log('G3 exact (30)       :', 'offers changed=' + (a.o !== o3 && o3 !== ''), 'gold', 30, '->', s3.gold, 'price', 30, '->', s3.price, ok(cs.valid && a.o !== o3 && o3 !== '' && s3.gold >= 0 && s3.gold <= 3 && s3.price === 60));  // paid exactly 30 (price step is exact); a stray kill coin can land after
  await skip(a.lvl); }

  // G4: price is 60 now: 59 refused, 60 pays exactly 60 and price becomes 90
  { const cs = await cleanStep(59); a = cs.a;
  const o4 = await offers(), s4 = await state();
  console.log('G4 one short (59)   :', 'offers same=' + (a.o === o4), 'gold', s4.gold, 'price', s4.price, ok(cs.valid && a.o !== '' && a.o === o4 && s4.gold === 59 && s4.price === 60));
  await skip(a.lvl); }
  { const cs = await cleanStep(60); a = cs.a;
  const o5 = await offers(), s5 = await state();
  console.log('G4 exact (60)       :', 'offers changed=' + (a.o !== o5 && o5 !== ''), 'gold', s5.gold, 'price', s5.price, ok(cs.valid && a.o !== o5 && o5 !== '' && s5.gold >= 0 && s5.gold <= 3 && s5.price === 90));  // paid exactly 60 (price step is exact); a stray kill coin can land after
  await skip(a.lvl); }

  // G5: rich player -> a live run can add a stray kill coin, so judge against the balance read right before the reroll.
  await setGold(500);
  a = await open();
  const pre6 = await state();
  await reroll(a.lvl);
  const s6 = await state();
  console.log('G5 rich (~500)      :', 'gold', pre6.gold, '->', s6.gold, 'price', pre6.price, '->', s6.price,
    ok(pre6.price === 90 && s6.price === 120 && pre6.gold - s6.gold >= 90 - 20 && pre6.gold - s6.gold <= 90 + 20));  // the price step is exact; a stray kill coin can land either side of the reroll, so the balance delta is 90 +/- a few coins
  await skip(a.lvl);

  // G6: slot full of a MAXED tome -> the pool is empty, so no screen opens and a reroll must cost nothing.
  for (let i = 0; i < 5; i++) await c('/emberfall granttome EmberTester ember_touch', 600);
  console.log('G6 stacks before   :', (await ask('/emberfall buildinfo EmberTester')).slice(0, 120));
  // Granting 5 tomes levels the bot up, and a real level-up screen may still be open (rolled before the 5th stack landed).
  // Close any such screen first (tomeskip on a level with nothing open is a silent no-op), so G6 judges only its own screen.
  for (let l = 1; l <= 4; l++) await c(`/emberfall tomeskip EmberTester ${l}`, 250);
  await sleep(1500);
  await setGold(500);
  const pre = await state();
  lvl++; await c(`/emberfall tomeopen EmberTester ${lvl}`, 900);
  const oEmpty = await offers();
  await reroll(lvl);
  const s7 = await state();
  console.log('G6 empty pool       :', 'offers [' + oEmpty + ']', 'gold', pre.gold, '->', s7.gold, 'price', pre.price, '->', s7.price,
    ok(pre.gold >= 500 && oEmpty === '' && s7.gold >= pre.gold && s7.price === pre.price));

  console.log('log problems        :', lines.filter(l => /exception|error/i.test(l)).length);
  await c('/expedition leave', 1000);
  bot.quit(); setTimeout(() => process.exit(0), 500);
});
