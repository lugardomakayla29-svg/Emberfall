// Defensive relics through the real damage events: Totem of Returning, Ender Pearl Shard, Thorn Vest, Mirror Shard.
const mineflayer = require('mineflayer');
const bot = mineflayer.createBot({ host: '127.0.0.1', port: 25565, username: 'EmberTester', version: '1.21.11', auth: 'offline' });
const sleep = ms => new Promise(r => setTimeout(r, ms));
const lines = []; bot.on('message', m => lines.push(m.toString()));
let runEnds = 0; bot._client.on('packet', (d, meta) => { if (meta.name === 'custom_payload' && d.channel === 'emberfall:run_end') runEnds++; });
let fails = 0; const check = (l, ok, e = '') => { console.log((ok ? 'PASS ' : 'FAIL ') + l + (e ? '  ' + e : '')); if (!ok) fails++; };
bot.once('spawn', async () => {
  await sleep(6000);
  const c = async (x, w = 700) => { bot.chat(x); await sleep(w); };
  const ask = async (x, w = 700) => { const n = lines.length; bot.chat(x); await sleep(w); return lines.slice(n).join(' | '); };
  const startRun = async () => { await c('/expedition leave', 900); await c('/expedition', 4000); for (let i = 0; i < 30; i++) { await sleep(1500); if (/expedition/.test(await ask('/data get entity @s Dimension', 400))) break; } await sleep(1500); };
  const stateOf = async () => await ask('/emberfall relic state EmberTester', 700);
  await c('/gamemode survival'); await c('/character select juggernaut');

  // ---------- A. TOTEM OF RETURNING ----------
  await startRun();
  await c('/emberfall relic give EmberTester totem_of_returning', 500);
  runEnds = 0;
  await c('/damage @s 1000 minecraft:generic', 1800);
  let st = await stateOf();
  check('A1 first lethal hit: the run did NOT end (Totem absorbed it)', runEnds === 0 && /active=true/.test(st), `runEnds=${runEnds}`);
  check('A2 the player is alive with health restored', bot.health > 0, `health ${bot.health}`);
  check('A3 totem message was shown', lines.some(l => /Totem of Returning saved you/.test(l)), '');
  await c('/effect clear @s', 400); await sleep(3500); // let the short resistance grace lapse? it is 60 ticks (3 s)
  await c('/effect clear @s', 400);
  runEnds = 0;
  await c('/damage @s 1000 minecraft:generic', 1800);
  st = await stateOf();
  check('A4 second lethal hit (Totem already spent): the run ENDS', runEnds >= 1 && /active=false/.test(st), `runEnds=${runEnds}`);

  // ---------- B. a fresh run gets a fresh Totem ----------
  await startRun();
  await c('/emberfall relic give EmberTester totem_of_returning', 500);
  runEnds = 0;
  await c('/damage @s 1000 minecraft:generic', 1800);
  st = await stateOf();
  check('B1 next run: the Totem works again (spent-flag was cleared)', runEnds === 0 && /active=true/.test(st), `runEnds=${runEnds}`);

  // ---------- C. no Totem: the first lethal hit ends the run ----------
  await startRun();
  runEnds = 0;
  await c('/damage @s 1000 minecraft:generic', 1800);
  check('C1 without a Totem a lethal hit still ends the run', runEnds >= 1, `runEnds=${runEnds}`);

  // ---------- D. ENDER PEARL SHARD: dodge rate on real damage events ----------
  await startRun();
  await c('/effect give EmberTester minecraft:regeneration 999 4 true', 300);
  await c('/emberfall relic give EmberTester pearl_shard 5', 500); // 40% cap
  let dodged = 0, N = 40;
  for (let i = 0; i < N; i++) {
    const n = lines.length; bot.chat('/damage @s 1 minecraft:generic'); await sleep(260);
    if (lines.slice(n).some(l => /Dodged!/.test(l))) dodged++;
    await c('/effect give EmberTester minecraft:instant_health 1 3 true', 60);
  }
  console.log(`     dodged ${dodged}/${N} (${(100 * dodged / N).toFixed(1)}%), expected 40% of hits`);
  check('D1 dodge happens at a plausible rate (10-70% of 40 hits; expected 40%)', dodged >= 4 && dodged <= 28, `${dodged}/${N}`);
  await c('/emberfall relic take EmberTester pearl_shard', 100); for (let i = 0; i < 4; i++) await c('/emberfall relic take EmberTester pearl_shard', 100);
  dodged = 0;
  for (let i = 0; i < 15; i++) { const n = lines.length; bot.chat('/damage @s 1 minecraft:generic'); await sleep(260); if (lines.slice(n).some(l => /Dodged!/.test(l))) dodged++; await c('/effect give EmberTester minecraft:instant_health 1 3 true', 60); }
  check('D2 with the relic removed there are no dodges', dodged === 0, `${dodged}/15`);

  console.log(fails ? `RESULT: ${fails} FAILED` : 'RESULT: ALL PASSED');
  await c('/expedition leave', 800); bot.quit(); setTimeout(() => process.exit(fails ? 1 : 0), 500);
});
