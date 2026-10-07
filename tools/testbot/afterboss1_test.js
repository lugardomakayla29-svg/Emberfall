// V2 live: after the first boss (Ember Guardian) is killed, the player is warned once and the run moves to tier 2.
// The spawn-interval and sound proof come from the server log line AFTERBOSS1, graded by afterboss1_grade.py.
const mineflayer = require('mineflayer');
const sleep = ms => new Promise(r => setTimeout(r, ms));
const bot = mineflayer.createBot({ host: '127.0.0.1', port: 25565, username: 'EmberTester', version: '1.21.11', auth: 'offline' });
const lines = []; bot.on('message', m => lines.push(m.toString())); bot.on('error', e => console.log('ERR', e.message));
const ask = async (x, w = 600) => { const n = lines.length; bot.chat(x); await sleep(w); return lines.slice(n).join(' | '); };
let fails = 0; const check = (n, ok, note = '') => { console.log((ok ? 'PASS ' : 'FAIL ') + n + ' ' + note); if (!ok) fails++; };
const startRun = async () => {
  await ask('/expedition', 1500);
  for (let i = 0; i < 60; i++) { await sleep(2000); if (/expedition/.test(await ask('/data get entity @s Dimension', 400))) break; }
  await sleep(1500);
};
const status = async () => { const t = await ask('/emberfall wavestatus 0', 800); const m = t.match(/tier=(\d+) threat=([\d.]+) totalSpawned=(\d+)/); return m ? { tier: +m[1], threat: +m[2], spawned: +m[3], raw: t } : { raw: t }; };
const WARN = /WARNING: The horde grows restless/;
bot.once('spawn', async () => {
  await sleep(6500);
  await ask('/gamemode survival'); await ask('/effect give @s minecraft:resistance 999 4 true', 300); await ask('/effect give @s minecraft:regeneration 999 4 true', 300);
  await ask('/character select juggernaut', 600); await startRun();
  await sleep(2000);
  const before = await status();
  check('A1 the run is alive and at tier 1 before any boss falls', before.tier === 1, JSON.stringify(before));
  check('A1b no AFTER-BOSS warning has been shown yet', !lines.some(l => WARN.test(l)));
  await ask('/emberfall boss 0', 3500);
  check('A2 the Ember Guardian was spawned (awakens line seen)', lines.some(l => /Ember Guardian awakens/.test(l)));
  const killed = await ask('/kill @e[type=emberfall:ember_guardian]', 1500);
  check('A3 the kill command hit the Guardian (not "No entity was found")', !/No entity was found/.test(killed), killed.slice(0, 90));
  await sleep(3500);
  const after = await status();
  check('A4 the run moved to tier 2 after the genuine kill', after.tier === 2, JSON.stringify(after));
  const warns = lines.filter(l => WARN.test(l)).length;
  check('A5 the warning line was shown to the player exactly once', warns === 1, 'count=' + warns);
  check('A5b the existing "corruption deepens" line still shows once (not replaced)', lines.filter(l => /corruption deepens/.test(l)).length === 1);
  // a second look 30 s later: no repeat of the warning
  await sleep(30000);
  check('A6 the warning does not repeat 30 s later', lines.filter(l => WARN.test(l)).length === 1);
  const later = await status();
  check('A7 the horde keeps spawning in tier 2 (totalSpawned grew)', later.spawned > after.spawned, after.spawned + ' -> ' + later.spawned);
  console.log(fails === 0 ? 'ALL PASS (sound + intervals graded from the server log by afterboss1_grade.py)' : 'SOME FAIL ' + fails);
  bot.quit(); process.exit(fails === 0 ? 0 : 1);
});
setTimeout(() => { console.log('TIMEOUT'); process.exit(2); }, 240000);
