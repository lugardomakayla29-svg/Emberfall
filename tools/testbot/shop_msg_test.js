// Every shop refusal must ANSWER, and every buy must really change state. Goes through ShopManager.onBuyReceived (the buy button's method).
const mineflayer = require('mineflayer');
const bot = mineflayer.createBot({ host: '127.0.0.1', port: 25565, username: 'EmberTester', version: '1.21.11', auth: 'offline' });
const sleep = ms => new Promise(r => setTimeout(r, ms));
const lines = []; bot.on('message', m => lines.push(m.toString()));
const ask = async (x, w = 800) => { const n = lines.length; bot.chat(x); await sleep(w); return lines.slice(n).join(' | '); };
const R = (n, ok, extra = '') => console.log(`${ok ? 'PASS' : 'FAIL'} ${n} ${extra}`);
const bal = async () => { const r = await ask('/emberfall balance EmberTester', 700); const m = /has (\d+)/.exec(r); return m ? +m[1] : NaN; };
bot.once('spawn', async () => {
  await sleep(6000);
  await ask('/gamemode survival');
  const b0 = await bal();
  R('S0 balance read (not NaN)', !isNaN(b0), `(${b0})`);
  // 1) broke: refused WITH the price and the balance
  let r = await ask('/emberfall shopbuy EmberTester upgrade slot_weapon', 900);
  R('S1 a broke weapon-slot buy answers with balance / price', /Not enough Silver/.test(r) && /\d+ \/ \d+/.test(r), `(${r.slice(0, 90)})`);
  r = await ask('/emberfall shopbuy EmberTester weapon twin_daggers', 900);
  R('S2 a broke weapon buy answers with balance / price', /Not enough Silver for/.test(r) && /\d+ \/ \d+/.test(r), `(${r.slice(0, 90)})`);
  // 2) unknown id answers instead of vanishing
  r = await ask('/emberfall shopbuy EmberTester upgrade not_a_real_item', 900);
  R('S3 an unknown item answers', /not for sale/i.test(r), `(${r.slice(0, 70)})`);
  // 3) fund, buy, prove the state changed and the message says Silver
  await ask('/emberfall givecurrency EmberTester 2000', 700);
  const b1 = await bal();
  r = await ask('/emberfall shopbuy EmberTester upgrade slot_weapon', 900);
  const b2 = await bal();
  R('S4 a weapon slot buy pays and says Silver', /Weapon Slot/.test(r) && /Silver/.test(r) && b2 === b1 - 100, `(${b1} -> ${b2}; ${r.slice(0, 70)})`);
  // 4) owning a weapon: buy, then buy again -> "already own"
  r = await ask('/emberfall shopbuy EmberTester weapon twin_daggers', 900);
  const b3 = await bal();
  R('S5 a weapon buy unlocks and charges its price', /Unlocked/.test(r) && b3 < b2, `(${b2} -> ${b3})`);
  r = await ask('/emberfall shopbuy EmberTester weapon twin_daggers', 900);
  const b4 = await bal();
  R('S6 buying an owned weapon answers "already own" and charges nothing', /already own/i.test(r) && b4 === b3, `(${r.slice(0, 60)})`);
  // 5) stat upgrade to max, then once more -> "max level"
  let last = '';
  for (let i = 0; i < 12; i++) { last = await ask('/emberfall shopbuy EmberTester upgrade vitality', 500); if (/max level/i.test(last)) break; }
  R('S7 a maxed upgrade answers instead of staying silent', /max level/i.test(last), `(${last.slice(0, 70)})`);
  // 6) slots maxed
  for (let i = 0; i < 4; i++) { last = await ask('/emberfall shopbuy EmberTester upgrade slot_weapon', 500); }
  R('S8 a fully unlocked slot kind answers', /already unlocked/i.test(last), `(${last.slice(0, 70)})`);
  bot.quit(); setTimeout(() => process.exit(0), 400);
});
bot.on('error', e => console.log('ERR', e.message));
