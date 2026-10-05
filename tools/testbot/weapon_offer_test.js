// Usage: node weapon_offer_test.js   (run on a FRESH world: 1 weapon slot, default unlocks)
// Proves the mid-run weapon offer: a FULL loadout gets no offer, a free slot gets one, skip keeps all, a pick is ADDED (never replaces), and a timeout never swaps or deletes.
const mineflayer = require('mineflayer');
const bot = mineflayer.createBot({ host: '127.0.0.1', port: 25565, username: 'EmberTester', version: '1.21.11', auth: 'offline' });
const sleep = ms => new Promise(r => setTimeout(r, ms));
bot.on('error', e => console.log('ERROR', e));
const lines = []; bot.on('message', m => lines.push(m.toString()));
bot.once('spawn', async () => {
  await sleep(6000);
  const c = async (x, w = 900) => { bot.chat(x); await sleep(w); };
  const ask = async (x, w = 1000) => { const n = lines.length; bot.chat(x); await sleep(w); return lines.slice(n).filter(l => !/Teleported/.test(l)).join(' | '); };
  const pend = async () => { const r = await ask('/emberfall weaponpending EmberTester'); const m = r.match(/WPEND (\S+)/); return m ? m[1] : 'UNREADABLE:' + r.slice(0, 60); };
  const load = async () => { const r = await ask('/emberfall debugloadout EmberTester'); const m = r.match(/Loadout:\s*(.*?)\s*\|\s*held=(\S+)\s*\|\s*weaponSlots=(\d+)/); return m ? { weapons: m[1].trim(), held: m[2], slots: +m[3] } : { weapons: 'UNREADABLE:' + r.slice(0, 60), held: '?', slots: -1 }; };
  const ok = (cond) => cond ? 'PASS' : 'FAIL';

  await c('/gamemode survival', 700); await c('/character select juggernaut', 700);
  await c('/expedition leave', 800); await c('/expedition', 4000);
  await c('/effect give @s minecraft:resistance 900 4 true', 400);

  const l0 = await load();
  console.log('W0 start           :', l0.weapons, '| slots', l0.slots, ok(l0.slots === 1 && l0.weapons === 'war_halberd'));

  // W1 (RULE): with every weapon slot full there is NO offer at all (the old popup let a one-weapon player swap his only weapon)
  await c('/emberfall weaponoffer EmberTester', 1200);
  const p1 = await pend(); const l1 = await load();
  console.log('W1 full = no offer :', 'pending', p1, '| weapons', l1.weapons, ok(p1 === 'none' && l1.weapons === 'war_halberd'));

  // W2: buy a 2nd weapon slot through the real shop path; now an offer opens, with other weapons and nothing incoming
  await c('/emberfall givecurrency EmberTester 100', 800);
  await c('/emberfall shopbuy EmberTester upgrade slot_weapon', 1000);
  const l2 = await load();
  console.log('W2 slot bought     :', 'slots', l2.slots, ok(l2.slots === 2 && l2.weapons === 'war_halberd'));
  await c('/emberfall weaponoffer EmberTester', 1200);
  const p2 = await pend(); const offered = p2.split('|')[0].split(',').filter(x => x && x !== 'none');
  console.log('W3 free = offer    :', p2, ok(p2.includes('incoming=none') && offered.length > 0 && !offered.includes('war_halberd')));

  // W4: Skip on that offer keeps everything and closes the screen
  await c('/emberfall weaponanswer EmberTester skip', 1000);
  const p5 = await pend(); const l5 = await load();
  console.log('W5 skip mid-run    :', 'pending', p5, '| weapons', l5.weapons, ok(p5 === 'none' && l5.weapons === 'war_halberd'));

  // W6: pick one into the FREE slot: it is ADDED, nothing is replaced, the halberd stays
  await c('/emberfall weaponoffer EmberTester', 1200);
  const p4a = await pend(); const pick4 = p4a.split('|')[0].split(',')[0];
  await c(`/emberfall weaponanswer EmberTester ${pick4}`, 1200);
  const l4 = await load(); const p4 = await pend();
  console.log('W7 pick is added   :', l4.weapons, 'pending', p4, ok(l4.weapons.includes('war_halberd') && l4.weapons.includes(pick4) && p4 === 'none'));

  // W8: both slots now full again -> no offer, both weapons kept
  await c('/emberfall weaponoffer EmberTester', 1200);
  const p8 = await pend(); const l8 = await load();
  console.log('W8 full again      :', 'pending', p8, '| weapons', l8.weapons, ok(p8 === 'none' && l8.weapons.includes('war_halberd') && l8.weapons.includes(pick4)));

  await c('/emberfall givecurrency EmberTester 300', 900);
  await c('/emberfall shopbuy EmberTester upgrade slot_weapon', 1800);
  const l3s = await load();
  console.log('W8b 3rd slot bought:', 'slots', l3s.slots, ok(l3s.slots === 3));
  // a fresh world only has 2 weapons unlocked, both held, so there is nothing left to offer: unlock a third through the real shop
  await c('/emberfall givecurrency EmberTester 2000', 900);
  const unl = await ask('/emberfall shopbuy EmberTester weapon twin_daggers', 1500);
  console.log('W8c dagger unlocked:', unl.slice(0, 90), ok(!/Not enough|Unknown|already/i.test(unl)));
  // W9: a mid-run offer left alone must be DECLINED at the timeout, never auto-picked
  const before = await load();
  await c('/emberfall weaponoffer EmberTester', 1200);
  const p6a = await pend();
  console.log('W9 chat after offer:', lines.slice(-6).filter(l => !/Teleported|WPEND|Loadout/.test(l)).join(' | ').slice(0, 220));
  await sleep(10000);
  const p6 = await pend(); const l6 = await load();
  console.log('W9 timeout declines:', 'opened', p6a.split('|')[0], '-> pending', p6, '| weapons', l6.weapons, ok(p6a.split('|')[0].includes('twin_daggers') && p6 === 'none' && l6.weapons === before.weapons));

  await c('/expedition leave', 1200);
  bot.quit(); setTimeout(() => process.exit(0), 600);
});
