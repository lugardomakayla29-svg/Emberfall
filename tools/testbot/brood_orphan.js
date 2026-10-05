// Kill the mother WHILE sacs are waiting; the orphan cleanup in remove() must delete them. Non-vacuous: require sacs > 0 right before the kill.
const mineflayer = require('mineflayer');
const bot = mineflayer.createBot({ host: '127.0.0.1', port: 25565, username: 'EmberTester', version: '1.21.11', auth: 'offline' });
const sleep = ms => new Promise(r => setTimeout(r, ms));
const lines = []; bot.on('message', m => lines.push(m.toString()));
const ask = async (x, w = 400) => { const n = lines.length; bot.chat(x); await sleep(w); return lines.slice(n).join(' | '); };
const cnt = async sel => { await ask(`/execute store result score #n emberfall_t run execute if entity ${sel}`, 200); const r = await ask('/scoreboard players get #n emberfall_t', 200); const m = /has (-?\d+)/.exec(r); return m ? +m[1] : -1; };
const MOM = '@e[type=emberfall:broodmother_stalker]', DISP = '@e[type=minecraft:item_display]', KID = '@e[type=emberfall:broodling]';
bot.once('spawn', async () => {
  await sleep(6000);
  await ask('/scoreboard objectives add emberfall_t dummy', 300);
  await ask('/gamemode survival'); await ask('/effect give @s minecraft:resistance 900 4 true', 200);
  await ask('/kill @e[type=!player]', 700);
  const pin = setInterval(() => bot.chat('/tp @s ' + bot.entity.position.x.toFixed(2) + ' ' + bot.entity.position.y.toFixed(2) + ' ' + bot.entity.position.z.toFixed(2)), 500);
  await ask('/emberfall spawnelite broodmother_stalker', 1000);
  const acc = await cnt(DISP);
  console.log('displays with just the mother (her accessories):', acc);
  // wait until sacs exist (displays rise above the accessory baseline) but broodlings do not yet
  let saw = false, at = 0;
  for (let i = 0; i < 60; i++) {
    const d = await cnt(DISP), k = await cnt(KID);
    if (d > acc && k === 0) { saw = true; at = d - acc; break; }
    await sleep(20);
  }
  console.log(saw ? `sacs waiting: ${at} (broodlings 0) -> killing the mother NOW` : 'NEVER caught a waiting sac');
  const tk = Date.now();
  await ask(`/kill ${MOM}`, 80);
  console.log('kill issued', Date.now() - tk, 'ms after the sacs were first seen (+ counting latency)');
  const kAtDeath = await cnt(KID);   // broodlings alive the instant she died were hatched BEFORE her death: legitimate
  await sleep(2800);
  const d2 = await cnt(DISP), k2 = await cnt(KID), m2 = await cnt(MOM);
  console.log(`broodlings at her death ${kAtDeath}, 2.8s later ${k2} (new after death: ${Math.max(0, k2 - kAtDeath)})`);
  console.log(`after: mother ${m2}, displays ${d2}, broodlings ${k2}`);
  const ok = saw && m2 === 0 && d2 === 0 && k2 <= kAtDeath;   // no sac survives, and nothing hatches after she is dead
  console.log((ok ? 'PASS' : 'FAIL') + ' orphan cleanup: mother dead mid-sac leaves 0 sacs and 0 broodlings (non-vacuous: sacs existed before)');
  clearInterval(pin); await ask('/kill @e[type=!player]', 400);
  bot.quit(); setTimeout(() => process.exit(0), 300);
});
