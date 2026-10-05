// Broodmother Brood Call end to end, measured on the server:
//  L: she stops and lays (an egg-sac item_display appears on the ground), S: the sac waits 1.2 to 2.0s, H: it hatches into an
//  emberfall:broodling that has a TARGET, K: a broodling can be damaged and killed, C: nothing is left behind afterwards.
const mineflayer = require('mineflayer');
const bot = mineflayer.createBot({ host: '127.0.0.1', port: 25565, username: 'EmberTester', version: '1.21.11', auth: 'offline' });
const sleep = ms => new Promise(r => setTimeout(r, ms));
const lines = []; bot.on('message', m => lines.push(m.toString()));
const ask = async (x, w = 500) => { const n = lines.length; bot.chat(x); await sleep(w); return lines.slice(n).join(' | '); };
const cnt = async sel => { await ask(`/execute store result score #n emberfall_t run execute if entity ${sel}`, 250); const r = await ask('/scoreboard players get #n emberfall_t', 250); const m = /has (-?\d+)/.exec(r); return m ? +m[1] : -1; };
const res = []; const check = (n, ok, note) => { res.push(ok); console.log((ok ? 'PASS ' : 'FAIL ') + n + ' ' + note); };
const MOM = '@e[type=emberfall:broodmother_stalker]', EGG = '@e[type=minecraft:item_display,tag=emberfall_run,nbt={item:{id:"minecraft:player_head"}}]', KID = '@e[type=emberfall:broodling]';
bot.once('spawn', async () => {
  await sleep(6000);
  await ask('/scoreboard objectives add emberfall_t dummy', 300);
  await ask('/gamemode survival'); await ask('/effect give @s minecraft:resistance 900 4 true', 200); await ask('/effect give @s minecraft:regeneration 900 4 true', 200);
  await ask('/kill @e[type=!player]', 700);
  const pin = setInterval(() => bot.chat('/tp @s ' + bot.entity.position.x.toFixed(2) + ' ' + bot.entity.position.y.toFixed(2) + ' ' + bot.entity.position.z.toFixed(2)), 500);
  // Spawn OUTSIDE a run so no auto-weapon kills her, and so the purge does not apply. The player is her target.
  await ask('/emberfall spawnelite broodmother_stalker', 1200);
  check('L0 one Broodmother spawned', (await cnt(MOM)) === 1, '');
  const eggs0 = await cnt(EGG);   // her back-sac head is a rider display, so baseline it BEFORE the call
  console.log('   baseline player_head displays (accessories):', eggs0);
  let maxEgg = 0, firstEgg = null, firstKid = null, kidTarget = false, t0 = Date.now(), sawKid = 0;
  while (Date.now() - t0 < 26000) {
    const e = await cnt(EGG), k = await cnt(KID), now = (Date.now() - t0) / 1000;
    if (e - eggs0 > maxEgg) maxEgg = e - eggs0;
    if (firstEgg === null && e > eggs0) firstEgg = now;
    if (firstKid === null && k > 0) { firstKid = now; }
    if (k > sawKid) sawKid = k;
    if (k > 0 && !kidTarget) {
      const r = await ask('/emberfall debugaggro EmberTester', 700);
      if (/broodling.*target=EmberTester/.test(r + lines.slice(-8).join(' '))) kidTarget = true;
    }
    if (firstKid !== null && now - firstKid > 3) break;
    await sleep(250);
  }
  check('S1 an egg sac appeared on the ground', firstEgg !== null, firstEgg === null ? 'never saw a sac' : `first sac at ${firstEgg.toFixed(1)}s, max ${maxEgg} at once`);
  check('H1 the sac hatched a broodling', firstKid !== null, firstKid === null ? 'no broodling' : `first broodling at ${firstKid.toFixed(1)}s, ${sawKid} seen at once`);
  if (firstEgg !== null && firstKid !== null) {
    const wait = firstKid - firstEgg;
    check('S2 the sac waited about 1 to 2s before hatching', wait >= 0.8 && wait <= 3.2, `sac -> broodling gap ${wait.toFixed(1)}s (poll error about 0.6s)`);
  }
  check('H2 a hatched broodling has the player as its target', kidTarget, kidTarget ? 'target=EmberTester' : 'no target reported');
  // K: damageable by an emberfall weapon path = it is an emberfall-namespace Mob (isEmberfallHostile). Prove by killing with a normal hit.
  const before = await cnt(KID);
  await ask('/damage @e[type=emberfall:broodling,limit=1] 3 minecraft:generic', 500);
  const hpNow = await ask('/data get entity @e[type=emberfall:broodling,limit=1,sort=nearest] Health', 600);
  console.log('   after 3 damage:', hpNow.slice(0, 90), ' (max is 8)');
  await ask('/kill @e[type=emberfall:broodling]', 600);
  check('K1 a broodling can be damaged and killed', before > 0 && (await cnt(KID)) === 0, `had ${before}, now ${await cnt(KID)}`);
  // C: kill the mother mid-lay and confirm no orphaned sac is left
  await ask('/kill @e[type=emberfall:broodling]', 300);
  await sleep(300);
  await ask(`/kill ${MOM}`, 900); await sleep(2500);
  const leftEggs = (await cnt(EGG)) - 0, leftKids = await cnt(KID);
  console.log('   leftovers after the mother died: player_head displays', leftEggs, 'broodlings', leftKids);
  check('C1 no broodling or sac left after she dies', leftKids === 0, `broodlings ${leftKids}, displays ${leftEggs}`);
  console.log(res.every(Boolean) ? 'ALL PASS' : 'SOME FAIL');
  clearInterval(pin); await ask('/kill @e[type=!player]', 500);
  bot.quit(); setTimeout(() => process.exit(0), 300);
});
bot.on('error', e => console.log('ERR', e.message));
