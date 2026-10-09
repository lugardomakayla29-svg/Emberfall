// Creative tab items: registration, summoner behaviour inside and outside a run, and non-op lockout.
const mineflayer = require('mineflayer');
const sleep = ms => new Promise(r => setTimeout(r, ms));
const mk = name => new Promise(res => { const b = mineflayer.createBot({ host: '127.0.0.1', port: 25565, username: name, version: '1.21.11', auth: 'offline' }); b.chat_ = []; b.on('message', m => b.chat_.push(m.toString())); b.once('spawn', () => res(b)); });
let fails = 0; const check = (n, ok, note = '') => { console.log((ok ? 'PASS ' : 'FAIL ') + n + ' ' + note); if (!ok) fails++; };
(async () => {
  const op = await mk('EmberTester'); await sleep(5000);
  const say = async (b, cmd, w = 900) => { b.chat_.length = 0; b.chat(cmd); await sleep(w); return b.chat_.join(' | '); };
  const sum = async k => (await say(op, `/emberfall relic summoner EmberTester ${k}`, 800)).replace(/.*RELIC /, '');
  for (const id of ['broodtide_summoner', 'devourer_summoner', 'merchant_summoner']) {
    const r = await say(op, `/give @s emberfall:${id}`, 500);
    check('C1 emberfall:' + id + ' exists', /Gave 1/.test(r) && !/Unknown|Incorrect/.test(r), r.slice(0, 70));
  }
  await say(op, '/gamemode survival', 400); await say(op, '/effect give @s minecraft:resistance 999 4 true', 300); await say(op, '/effect give @s minecraft:regeneration 999 4 true', 300);
  for (const k of ['broodtide', 'devourer', 'merchant']) check('C2 ' + k + ' summoner is refused outside a run', (await sum(k)).includes('refused'), '');
  await say(op, '/character select juggernaut', 600); await say(op, '/expedition', 1500);
  for (let i = 0; i < 60; i++) { await sleep(2000); if (/expedition/.test(await say(op, '/data get entity @s Dimension', 400))) break; }
  await sleep(2000);
  check('C3 the merchant summoner brings a Testificate inside a run', (await sum('merchant')).includes('ok'), '');
  check('C3b a second use is refused (he is already here)', (await sum('merchant')).includes('refused'), '');
  const before = await say(op, '/execute if entity @e[type=emberfall:broodtide]', 500);
  check('C4 no Broodtide before the summoner is used', /failed|Test failed/.test(before), before.slice(0, 50));
  check('C4b the Broodtide summoner starts the fight', (await sum('broodtide')).includes('ok'), '');
  await sleep(3000);
  check('C4c a Broodtide now exists', /Test passed|Count/.test(await say(op, '/execute if entity @e[type=emberfall:broodtide]', 600)), '');
  check('C4d a second boss is refused while one is fighting', (await sum('devourer')).includes('refused') && (await sum('broodtide')).includes('refused'), '');
  // non-op: a name that is NOT in ops.json (PlainPlayer is, at level 4, on this test server)
  const pl = await mk('NoPermGuest'); await sleep(4000);
  const who = await say(pl, '/emberfall balance', 900);
  check('C5 a real non-operator cannot run /emberfall (the command does not even resolve)', /Unknown or incomplete command/.test(who), who.slice(0, 90) || '(no reply)');
  const r2 = await say(pl, '/give @s emberfall:broodtide_summoner', 700);
  check('C5b and cannot /give themselves a summoner', !/Gave 1/.test(r2) && /Unknown or incomplete command/.test(r2), r2.slice(0, 80) || '(no reply)');
  const r3 = await say(pl, '/emberfall relic summoner NoPermGuest guardian', 700);
  check('C5c and cannot drive the summoner test hook', /Unknown or incomplete command/.test(r3), r3.slice(0, 80) || '(no reply)');
  console.log(fails === 0 ? 'ALL PASS' : 'SOME FAIL ' + fails);
  pl.quit(); op.quit(); setTimeout(() => process.exit(0), 400);
})();
