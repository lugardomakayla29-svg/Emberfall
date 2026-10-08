// A Rift cannot be opened inside a running expedition. Every active run is in the expedition dimension today, so the DIMENSION guard is what
// refuses; RiftManager.insideActiveRun is defence in depth for a future in-place mode (see the PR). Control: the same command works outside a run.
const mineflayer = require('mineflayer');
const bot = mineflayer.createBot({ host: '127.0.0.1', port: 25565, username: 'EmberTester', version: '1.21.11', auth: 'offline' });
const sleep = ms => new Promise(r => setTimeout(r, ms));
const lines = []; bot.on('message', m => lines.push(m.toString())); bot.on('error', e => console.log('ERR', e.message));
const ask = async (x, w = 500) => { const n = lines.length; bot.chat(x); await sleep(w); return lines.slice(n).join(' | '); };
let fails = 0, total = 0; const R = (n, ok, extra = '') => { total++; console.log(`${ok ? 'PASS' : 'FAIL'} ${n} ${extra}`); if (!ok) fails++; };
const state = async () => { const r = await ask('/emberfall rift state', 600); const m = /rifts=(\d+)/.exec(r); return { rifts: m ? +m[1] : -1, raw: r }; };
bot.once('spawn', async () => {
  await sleep(6500);
  await ask('/gamemode survival', 300); await ask('/effect give @s minecraft:resistance 999 4 true', 300); await ask('/emberfall rift closeall', 300); await sleep(2500);
  // control first: outside a run the same command opens a Rift (sky is open at the spawn height used by the other Rift tests)
  await ask('/fill 94 199 94 106 199 106 minecraft:stone', 700); await ask('/fill 94 200 94 106 230 106 minecraft:air', 700); await ask('/tp @s 100 200 100', 700); await sleep(1200);
  const before = await ask('/emberfall rift open', 700); R('I0 CONTROL: outside a run a Rift opens', /RIFT opening/.test(before), before.slice(0, 60));
  await ask('/emberfall rift closeall', 300); await sleep(2500);
  // now a real run
  await ask('/character select juggernaut', 600); await ask('/expedition', 1500);
  let inRun = false; for (let i = 0; i < 60; i++) { await sleep(2000); if (/expedition/.test(await ask('/data get entity @s Dimension', 400))) { inRun = true; break; } }
  R('I1 the bot is inside a running expedition', inRun, '');
  await sleep(2000);
  const during = await ask('/emberfall rift open', 700); const s = await state();
  // The command prints "RIFT refused: <reason>" (EmberfallCommands, via sendSuccess, a plain chat line). READ FROM HANDLER, NOT SEEN ARRIVING.
  R('I2 inside the run a Rift is refused FOR THE RUN (the expedition map is a run arena) and none exists', /RIFT refused: the expedition map is a run arena/.test(during) && s.rifts === 0, during.slice(0, 100) + ' ' + s.raw.slice(-30));
  const nat = await ask('/emberfall rift natural', 900); const s2 = await state();
  R('I3 inside the run the natural opening is refused too', /natural refused/.test(nat) && s2.rifts === 0, nat.slice(0, 60));
  console.log(fails === 0 ? `ALL PASS (${total})` : `FAILED ${fails} of ${total}`); bot.quit(); process.exit(fails ? 1 : 0);
});
