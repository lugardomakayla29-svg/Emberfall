// EmberTester egg. A client without the mod cannot hold or right-click a modded item, so (like tab_test.js) the test calls the hook
// `/emberfall relic summoner <player> egg`, which runs BotEggItem.use() itself: the item's real checks (operator, in a run, 15 s window,
// party cap, free name) decide, exactly as for a right click. The hook is given the TARGET player, so a non-operator target is refused by the item.
const mineflayer = require('mineflayer');
const sleep = ms => new Promise(r => setTimeout(r, ms));
const mk = name => new Promise(res => { const b = mineflayer.createBot({ host: '127.0.0.1', port: 25565, username: name, version: '1.21.11', auth: 'offline' }); b.chat_ = []; b.on('message', m => b.chat_.push(m.toString())); b.once('spawn', () => res(b)); });
let fails = 0; const check = (n, ok, note = '') => { console.log((ok ? 'PASS ' : 'FAIL ') + n + ' ' + note); if (!ok) fails++; };
(async () => {
  const op = await mk('EmberTester'); await sleep(4000);
  const guest = await mk('NoPermGuest'); await sleep(2500);
  const say = async (b, cmd, w = 900) => { b.chat_.length = 0; b.chat(cmd); await sleep(w); return b.chat_.join(' | '); };
  const egg = async target => (await say(op, '/emberfall relic summoner ' + target + ' egg', 900)).replace(/.*RELIC /, '');
  const party = async slot => { const m = /partySize=(\d+)/.exec(await say(op, '/emberfall wavestatus ' + slot, 700)); return m ? parseInt(m[1], 10) : -1; };
  const roster = async () => { const m = /roster=(\d+)/.exec(await say(op, '/emberfall bot list', 700)); return m ? parseInt(m[1], 10) : -1; };
  await say(op, '/gamemode creative', 300);

  // E1: outside a run the item refuses and creates nothing.
  const r0 = await roster();
  check('E1a outside a run the egg is refused', (await egg('EmberTester')).includes('refused'), '');
  check('E1b ...and no bot was created', (await roster()) === r0, 'roster ' + r0 + ' -> ' + (await roster()));

  // The operator starts the run exactly as a human would. Everything below must happen inside the 15 s window.
  op.chat('/expedition');
  let slot = null, t0 = 0;
  for (let i = 0; i < 400 && slot === null; i++) {
    await sleep(250);
    for (const sl of [0, 1, 2, 3]) { const w = await say(op, '/emberfall wavestatus ' + sl, 200); if (/partySize=[1-9]/.test(w)) { slot = sl; t0 = Date.now(); break; } }
  }
  check('E2 setup: the operator is in a run', slot !== null, 'slot=' + slot);
  if (slot === null) { console.log('SOME FAIL (setup)'); process.exit(1); }
  const before = await party(slot);
  check('E3 setup: the run holds one body (the operator)', before === 1, 'party=' + before);

  // E7 FIRST (a refusal costs no time): a non-operator target is refused by the item itself, party unchanged.
  const gRes = await egg('NoPermGuest');
  check('E7 a non-operator is refused by the item', gRes.includes('refused'), gRes);
  check('E7b ...and the party is unchanged', (await party(slot)) === before, '');

  // E4: the operator uses the egg inside the window.
  op.chat_.length = 0;
  const res = await egg('EmberTester');
  const used = Date.now() - t0;
  const said = op.chat_.join(' | ');
  check('E4 the egg is accepted inside the window', res.includes('ok'), res + ' (' + used + ' ms after the run was seen)');
  const after = await party(slot);
  check('E5 the party grew by exactly one', after === before + 1, `before=${before} after=${after}`);
  const list = await say(op, '/emberfall bot list', 700);
  check('E6 the new body is the roster bot EmberTester1', /EmberTester1/.test(list), list.slice(-70));
  check('E6b the run was warned it got harder', /joined the party|grows harder/i.test(said), said.slice(0, 110));

  // E8: after the freeze the egg must refuse, not add a body that changes nothing.
  const wait = 17000 - (Date.now() - t0); if (wait > 0) await sleep(wait);
  const late = await egg('EmberTester');
  check('E8 after the party is counted the egg is refused', late.includes('refused'), late);
  check('E8b ...and the party is unchanged', (await party(slot)) === after, '');

  // E9: the point of the egg. The director counts the party once, ~15 s in, and must have counted the bot. The server log is the evidence.
  let frozen = null;
  for (let i = 0; i < 20 && frozen === null; i++) {
    const l = await say(op, '/emberfall wavestatus ' + slot, 300);
    frozen = await new Promise(r => require('fs').readFile(process.env.SERVER_LOG || '/app/conversations/6a73b88911d246064724a798/emberfall/testserver/logs/latest.log', 'utf8', (e, t) => { const m = t && /PARTYFROZEN slot=\d+ size=(\d+)/.exec(t); r(m ? parseInt(m[1], 10) : null); }));
    if (frozen === null) await sleep(1000);
  }
  check('E9 the director counted the bot: PARTYFROZEN size=2 (the run really is harder)', frozen === 2, 'PARTYFROZEN size=' + frozen);
  await say(op, '/emberfall bot remove EmberTester1', 500);
  console.log(fails === 0 ? 'ALL PASS' : 'SOME FAIL ' + fails);
  op.quit(); guest.quit(); setTimeout(() => process.exit(0), 400);
})();
