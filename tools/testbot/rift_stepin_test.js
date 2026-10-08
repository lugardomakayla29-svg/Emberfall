// Rift step-in, live, BARE-HANDED, with a REAL use_entity packet (bot.activateEntity) on the Rift's one invisible Interaction.
// Verdicts for starts/ends come from the SERVER LOG (rift_stepin_grade.py), not from chat; this file prints the facts it can see itself.
// Every claim has a control: a refusal is shown beside an acceptance with only the thing under test different.
const mineflayer = require('mineflayer');
const bot = mineflayer.createBot({ host: '127.0.0.1', port: 25565, username: 'EmberTester', version: '1.21.11', auth: 'offline' });
const sleep = ms => new Promise(r => setTimeout(r, ms));
const lines = []; bot.on('message', m => lines.push(m.toString())); bot.on('error', e => console.log('ERR', e.message));
const ask = async (x, w = 600) => { const n = lines.length; bot.chat(x); await sleep(w); return lines.slice(n).join(' | '); };
let fails = 0, total = 0; const R = (n, ok, extra = '') => { total++; console.log(`${ok ? 'PASS' : 'FAIL'} ${n} ${extra}`); if (!ok) fails++; };
const INTERACTION = 69;
let spawned = [];
bot._client.on('packet', (d, m) => { if (m.name === 'spawn_entity') spawned.push({ id: d.entityId, type: d.type, x: d.x, y: d.y, z: d.z }); });
// The NBT VALUE only ("emberfall:expedition"): the word alone also appears in unrelated chat such as 'your next expedition starts with them'.
const dim = async () => /entity data: "emberfall:expedition"/.test(await ask('/data get entity EmberTester Dimension', 500)) ? 'expedition' : 'other';
const state = async () => { const r = await ask('/emberfall rift state', 600); const m = /RIFT active=(\d+) entities=(\d+) rifts=(\d+)/.exec(r); return m ? { rifts: +m[3], raw: r } : { rifts: -1, raw: r }; };
const targets = () => Object.values(bot.entities).filter(e => e.name === 'interaction');
const click = async (e) => { const n = lines.length; await bot.activateEntity(e); await sleep(900); return lines.slice(n).join(' | '); };

bot.once('spawn', async () => {
  await sleep(4000);
  await ask('/gamemode creative', 400); await ask('/emberfall rift clear', 200); await ask('/emberfall rift closeall', 200);
  await ask('/fill 94 199 94 106 199 106 minecraft:stone', 900); await ask('/fill 94 200 94 106 235 106 minecraft:air', 900);
  await ask('/tp @s 100 200 100', 800); await sleep(1500);
  await ask('/gamemode survival', 400); await ask('/effect give @s minecraft:resistance 999 4 true', 300);
  console.log('CHARACTER', JSON.stringify((await ask('/character select juggernaut', 700)).slice(0, 150))); await ask('/clear @s', 300);
  R('S0 the bot is bare-handed (the case an air click cannot reach the server)', !bot.heldItem, bot.heldItem ? bot.heldItem.name : 'empty');

  spawned = [];
  const o = await ask('/emberfall rift open', 900);
  R('A1 a Rift opens', /RIFT opening/.test(o), o.slice(0, 50));
  await sleep(700);
  const first = targets();
  R('A2 exactly ONE invisible Interaction target exists for the Rift', first.length === 1 && spawned.filter(s => s.type === INTERACTION).length === 1, `n=${first.length} spawns=${spawned.filter(s => s.type === INTERACTION).length}`);
  const t = first[0];
  // The Rift stands 6 blocks ahead of the opening spot (z 100.5 -> 106.5). NEAR is 3 blocks from it, inside the reach of 4; the opening spot is 6 away.
  const NEAR = '/tp @s 100.5 200 103.5', FAR = '/tp @s 100.5 200 100.5';
  await ask(NEAR, 300);

  // B: during the 5 s opening a click is refused (control for C: the same click, only the Rift's age differs).
  const early = await click(t);
  R('B1 a click while the Rift is still opening is REFUSED with its reason', /still opening/i.test(early), early.slice(-90));
  R('B2 the early click started no countdown', (await dim()) === 'other' && !/hold still/i.test(early), '');

  // C: after it has opened, a bare-handed click starts the countdown.
  await sleep(5500);
  // Too far, but OPEN: now the distance is the first thing wrong (the opening check comes first, so this could not be tested earlier).
  await ask(FAR, 600); await sleep(1200);
  const tooFar = await click(t);
  R('A3 a click from 6 blocks away on an OPEN Rift is REFUSED as too far (control for C1: only the distance differs)', /too far/i.test(tooFar), tooFar.slice(-90));
  await ask(NEAR, 600); await sleep(1200);
  const st = await state(); R('C0 the Rift is open now', /open=true/.test(st.raw), st.raw.slice(-40));
  const go = await click(t);
  R('C1 a bare-handed click on the open Rift starts the departure ("hold still")', /hold still|stirs/i.test(go), go.slice(-110));
  // D: walking away cancels (control for E: the same click, but holding still).
  await ask('/tp @s 100.5 200 98.5', 600); await sleep(1500);
  R('D1 walking away cancels the countdown', /stepped away|falls still/i.test(lines.slice(-6).join(' | ')), lines.slice(-3).join(' | ').slice(-110));
  await sleep(4500);
  R('D2 after the cancel NO run started (still home)', (await dim()) === 'other', '');

  // E: click again and hold still: ONE run starts.
  await ask(NEAR, 600); await sleep(1200);
  const go2 = await click(t);
  R('E1 clicking again is accepted (a cancel leaves no stale lockout)', /hold still|stirs/i.test(go2), go2.slice(-110));
  let inRun = false;
  for (let i = 0; i < 45; i++) { await sleep(2000); if ((await dim()) === 'expedition') { inRun = true; break; } }
  R('E2 holding still for the countdown puts the player in an expedition', inRun, '');
  await sleep(3000);
  console.log('DIMRAW', JSON.stringify((await ask('/data get entity EmberTester Dimension', 500)).slice(0, 160)), 'POS', JSON.stringify((await ask('/data get entity EmberTester Pos', 500)).slice(0, 160)));
  console.log('RUNSTATE', JSON.stringify((await ask('/emberfall relic swarm EmberTester state', 700)).slice(0, 160)));
  console.log('MARK leaving');
  const lv = await ask('/expedition leave', 1500); await sleep(2000);
  R('E2b the player really was in a run: the product itself answers "You left the expedition."', /You left the expedition/.test(lv), lv.slice(0, 80));
  R('E3 leaving returns the player home', (await dim()) === 'other', '');

  // F: the lockout. The Rift was closed by... nothing, it is still there unless it idled out: re-find the target.
  // The client drops entities of the dimension it left, and the target may need a moment to be re-sent: ask the SERVER how many Rifts exist, then wait for the client to see the target again.
  const afterRun = await state(); console.log('AFTERRUN server rifts=' + afterRun.rifts);
  for (let i = 0; i < 10 && targets().length === 0; i++) await sleep(500);
  const again = targets();
  if (again.length === 1) {
    const refused = await click(again[0]);
    R('F1 a click right after a run is REFUSED (the lockout)', /settling|wait|moment|recent/i.test(refused), refused.slice(-110));
    await sleep(12000);
    const accepted = await click(again[0]);
    R('F2 CONTROL: the same click after the lockout is accepted', /hold still|stirs/i.test(accepted), accepted.slice(-110));
    await ask('/tp @s 100.5 200 98.5', 600); await sleep(1200);
  } else {
    console.log('F skipped: target gone after the run (n=' + again.length + ')');
  }

  // G: cleanup. closeall discards the target; nothing is left behind.
  await ask(FAR, 600); await sleep(800);
  await ask('/emberfall rift closeall', 600); await sleep(1500);
  const sg = await state();
  R('G1 closeall empties the Rift list', sg.rifts === 0, sg.raw.slice(-40));
  R('G2 the click target is GONE (no invisible leftover)', targets().length === 0, `n=${targets().length}`);
  const dead = await ask('/emberfall rift state', 400);
  console.log('SUMMARY', total - fails, 'of', total);
  console.log(fails === 0 ? 'ALL PASS' : 'FAILED ' + fails);
  process.exit(fails === 0 ? 0 : 1);
});
