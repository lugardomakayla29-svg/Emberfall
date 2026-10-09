// V4 live, part 2 (Rift): the departure countdown bell. Replaces cues_gate_test.js, whose hub is gone.
//  - hold still for the full 3 second countdown: 3 bells (seconds left 3, 2, 1), pitch 0.9, 0.95, 1.0
//  - CONTROL: click again, then walk away right after the first bell: the countdown stops, so fewer than 3 bells
// Graded from the server log by: cues_grade.py server_run.log gate   (the Rift plays the same "gate_countdown" cue as the old gate)
const mineflayer = require('mineflayer');
const sleep = ms => new Promise(r => setTimeout(r, ms));
const bot = mineflayer.createBot({ host: '127.0.0.1', port: 25565, username: 'EmberTester', version: '1.21.11', auth: 'offline' });
const lines = []; bot.on('message', m => lines.push(m.toString())); bot.on('error', e => console.log('ERR', e.message));
const ask = async (x, w = 700) => { const n = lines.length; bot.chat(x); await sleep(w); return lines.slice(n).join(' | '); };
// The NBT VALUE only: the word "expedition" also appears in unrelated chat.
const inExpedition = t => /entity data: "emberfall:expedition"/.test(t);
const dim = async () => (inExpedition(await ask('/data get entity EmberTester Dimension', 500)) ? 'expedition' : 'other');
const targets = () => Object.values(bot.entities).filter(e => e.name === 'interaction');
const click = async (e) => { const n = lines.length; await bot.activateEntity(e); await sleep(700); return lines.slice(n).join(' | '); };
let fails = 0; const check = (n, ok, note = '') => { console.log((ok ? 'PASS ' : 'FAIL ') + n + ' ' + note); if (!ok) fails++; };
const finish = () => { console.log(fails === 0 ? 'ALL PASS' : 'SOME FAIL ' + fails); bot.quit(); setTimeout(() => process.exit(fails ? 1 : 0), 400); };

bot.once('spawn', async () => {
  await sleep(4000);
  await ask('/gamemode creative', 400); await ask('/emberfall rift clear', 200); await ask('/emberfall rift closeall', 200);
  // A swallowed /fill leaves no floor and the bot falls, so READ the floor back and retry until it is stone (see rift_select_test).
  let floor = false;
  for (let i = 0; i < 6 && !floor; i++) {
    await ask('/fill 94 199 94 106 199 106 minecraft:stone', 900); await ask('/fill 94 200 94 106 235 106 minecraft:air', 900);
    floor = /minecraft:stone/.test(await ask('/emberfall blockat 100 199 100', 500));
  }
  check('F0 the stone floor is really there before anyone stands on it', floor);
  if (!floor) return finish();
  await ask('/tp @s 100 200 100', 800); await sleep(1500);
  await ask('/gamemode survival', 400); await ask('/effect give @s minecraft:resistance 999 4 true', 300);
  await ask('/character select juggernaut', 700); await ask('/clear @s', 300);

  check('A1 a Rift opens', /RIFT opening/.test(await ask('/emberfall rift open', 900)));
  await sleep(6500); // the 5 s opening: a click before it ends is refused
  const t = targets()[0];
  check('A2 the Rift has its one click target', !!t, `n=${targets().length}`);
  if (!t) return finish();
  const NEAR = '/tp @s 100.5 200 103.5', FAR = '/tp @s 100.5 200 90.5';
  await ask(NEAR, 600); await sleep(1200); // settle, as rift_stepin_test does, before the entity is clicked
  const st = await ask('/emberfall rift state', 600);
  check('A3 the Rift reports itself open before the click', /open=true/.test(st), st.slice(-60));

  // 1 a full hold: click the Rift and stand still. The run starts after 3 s + the map build.
  await ask('/say MARK_GATE_HOLD_BEGIN', 300);
  console.log('TARGET', JSON.stringify({ id: t.id, name: t.name, pos: t.position, valid: t.isValid, me: bot.entity.position, n: targets().length }));
  const go1 = await click(t);
  console.log('CLICK1', go1.slice(-110));
  check('K0 the click was accepted ("hold still")', /hold still|stirs/i.test(go1), go1.slice(-90));
  let last = ''; for (let i = 0; i < 80; i++) { await sleep(2000); last = await ask('/data get entity EmberTester Dimension', 500); if (inExpedition(last)) break; }
  await ask('/say MARK_GATE_HOLD_END', 300);
  check('K1 the hold put the player in an expedition (the countdown ran to its end)', inExpedition(last), last.slice(0, 80));
  await sleep(2500); await ask('/expedition leave', 1500); await sleep(14000); // out of the 10 s lockout

  // 2 control: the Rift may still be open beside us; click it, then walk away after about one bell
  const t2 = targets()[0];
  check('K1b the Rift is still there to click again', !!t2);
  if (!t2) return finish();
  await ask(NEAR, 600); await sleep(1200);
  await ask('/say MARK_GATE_CANCEL_BEGIN', 300);
  const go2 = await click(t2);
  console.log('CLICK2', go2.slice(-110));
  check('K1c the control click was accepted too', /hold still|stirs/i.test(go2), go2.slice(-90));
  await sleep(300);
  await ask(FAR, 300);
  await sleep(4500);
  await ask('/say MARK_GATE_CANCEL_END', 300);
  check('K2 the walk-away did not start a run', (await dim()) === 'other');
  finish();
});
setTimeout(() => { console.log('TIMEOUT'); process.exit(2); }, 280000);
