// V4 live, part 2 (hub): the gate countdown bell. Built on gate_test.js's hub setup.
//  - hold still for the full 3 second countdown: 3 bells (seconds left 3, 2, 1), pitch 0.9, 0.95, 1.0
//  - CONTROL: click again, then move away right after the first bell: the countdown stops, so fewer than 3 bells
// Graded from the server log by: cues_grade.py server_run.log gate
const mineflayer = require('mineflayer');
const sleep = ms => new Promise(r => setTimeout(r, ms));
const X = -29, Y = 75, Z = -2;   // hearth; gate cell is (X, Y, Z-1)
const bot = mineflayer.createBot({ host: '127.0.0.1', port: 25565, username: 'EmberTester', version: '1.21.11', auth: 'offline' });
const lines = []; bot.on('message', m => lines.push(m.toString())); bot.on('error', e => console.log('ERR', e.message));
const ask = async (x, w = 700) => { const n = lines.length; bot.chat(x); await sleep(w); return lines.slice(n).join(' | '); };
const dim = async () => (await ask('/data get entity EmberTester Dimension', 500)).includes('expedition') ? 'expedition' : 'other';
let fails = 0; const check = (n, ok, note = '') => { console.log((ok ? 'PASS ' : 'FAIL ') + n + ' ' + note); if (!ok) fails++; };
bot.once('spawn', async () => {
  await sleep(6000);
  await ask('/gamemode creative', 500);
  await ask(`/tp @s ${X + 0.5} ${Y + 1} ${Z + 6.5}`, 2500);
  await ask(`/setblock ${X} ${Y + 1} ${Z} emberfall:ember_hearth`, 900);
  await ask(`/emberfall hubactivate ${X} ${Y + 1} ${Z}`, 3500);
  await ask('/gamemode survival', 500); await ask('/effect give @s minecraft:resistance 999 4 true', 300);
  await ask('/character select juggernaut', 600);
  await ask(`/tp @s ${X + 0.5} ${Y + 1} ${Z + 1.5}`, 1500);

  // 1 a full hold: click the gate and stand still. The run starts after 3 s + the map build.
  await ask('/say MARK_GATE_HOLD_BEGIN', 300);
  console.log('CLICK1', (await ask('/emberfall hubclick hubact_gate', 600)).slice(0, 90));
  // Only the quoted dimension name counts. Chat lines such as "Preparing the expedition" must not match.
  const inExpedition = t => /Dimension: "[^"]*expedition[^"]*"|entity data: "[^"]*expedition[^"]*"/.test(t);
  let last = ''; for (let i = 0; i < 80; i++) { await sleep(2000); last = await ask('/data get entity EmberTester Dimension', 500); if (inExpedition(last)) break; }
  await ask('/say MARK_GATE_HOLD_END', 300);
  check('K1 the hold put the player in an expedition (the countdown ran to its end)', inExpedition(last), last.slice(0, 80));
  await sleep(2500); await ask('/expedition leave', 1500); await sleep(14000); // out of the lockout

  // 2 control: click, then walk away after about one bell
  await ask(`/tp @s ${X + 0.5} ${Y + 1} ${Z + 1.5}`, 1500);
  await ask('/say MARK_GATE_CANCEL_BEGIN', 300);
  console.log('CLICK2', (await ask('/emberfall hubclick hubact_gate', 100)).slice(0, 90));
  await sleep(500);
  await ask(`/tp @s ${X + 0.5} ${Y + 1} ${Z + 9.5}`, 300);
  await sleep(4500);
  await ask('/say MARK_GATE_CANCEL_END', 300);
  check('K2 the walk-away did not start a run', (await dim()) === 'other');
  console.log(fails === 0 ? 'ALL PASS' : 'SOME FAIL ' + fails); bot.quit(); setTimeout(() => process.exit(fails ? 1 : 0), 400);
});
setTimeout(() => { console.log('TIMEOUT'); process.exit(2); }, 280000);
