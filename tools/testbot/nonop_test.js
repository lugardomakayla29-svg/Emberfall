// Permissions, end to end on the real server (issue #11 / V1): a player who is NOT an operator cannot reach the mod's
// operator commands, and the two deliberately public pieces still work for them.
//
//   /emberfall, /character, /shop   root requires LEVEL_GAMEMASTERS: a non-op must get "Unknown or incomplete command"
//   /expedition                     root is open ON PURPOSE (a child's requirement is AND-ed with its parent's); the bare
//                                   command checks the level itself and answers a non-op with a hint, not "Unknown"
//   /expedition leave               public ON PURPOSE, or a player would be trapped inside a run
//
// Every "non-op is refused" check has a control: the same command run by the operator EmberTester must NOT be refused,
// so a dead connection or a typo in a command cannot pass as "correctly blocked".
//
// Safety rules for this file (each one was learnt by breaking a probe while writing it, see the hand-off on #11):
//   * It never sends /op, /deop or /stop and never starts an expedition, so it cannot change who is an operator.
//   * It reads run/server/ops.json first and FAILS if NoPermGuest is an operator (a left-over ops.json from an earlier
//     run makes every "blocked" check meaningless). setup.sh only ever ops EmberTester.
//   * The non-op is tested first, then the operator, each while the server is alive.
const fs = require('fs');
const path = require('path');
const mineflayer = require('mineflayer');
const sleep = ms => new Promise(r => setTimeout(r, ms));

const HOME = process.env.EMBERFALL_HOME || path.resolve(__dirname, '..', '..');
const NON_OP = 'NoPermGuest';
const OP = 'EmberTester';

function connect(name) {
  const bot = mineflayer.createBot({ host: '127.0.0.1', port: 25565, username: name, version: '1.21.11', auth: 'offline' });
  bot.lines = [];
  bot.on('message', m => bot.lines.push(m.toString()));
  bot.on('error', e => console.log(name + ' connection error: ' + e.message));
  bot.on('kicked', r => console.log(name + ' kicked: ' + JSON.stringify(r).slice(0, 160)));
  return bot;
}
// Send one chat line and return everything the server said back within `wait` ms.
async function ask(bot, line, wait = 900) {
  const n = bot.lines.length;
  bot.chat(line);
  await sleep(wait);
  return bot.lines.slice(n).join(' | ');
}
const UNKNOWN = /Unknown or incomplete command/;

let fails = 0;
const check = (label, ok, extra = '') => { console.log((ok ? 'PASS ' : 'FAIL ') + label + (extra ? '  ' + extra : '')); if (!ok) fails++; };
const short = s => JSON.stringify(s.length > 110 ? s.slice(0, 110) + '...' : s);

(async () => {
  // Precondition: the world must be set up the way setup.sh leaves it.
  let ops = [];
  try { ops = JSON.parse(fs.readFileSync(path.join(HOME, 'run', 'server', 'ops.json'), 'utf8')); } catch (e) { /* reported below */ }
  const level = n => (ops.find(o => o.name === n) || {}).level || 0;
  check('precondition: ' + OP + ' is an operator in ops.json', level(OP) >= 2, 'level ' + level(OP));
  check('precondition: ' + NON_OP + ' is NOT an operator in ops.json', level(NON_OP) === 0, 'level ' + level(NON_OP));
  if (fails > 0) { console.log('RESULT: ' + fails + ' FAILED (fix ops.json first: run tools/setup.sh on a clean run/server)'); process.exit(0); }

  const guest = connect(NON_OP);
  const op = connect(OP);
  await Promise.all([new Promise(r => guest.once('spawn', r)), new Promise(r => op.once('spawn', r))]);
  await sleep(1500);

  // ---- 1. The non-op is refused everywhere the mod gates by operator level.
  const gated = [
    '/emberfall mapreport',
    '/emberfall buildinfo ' + NON_OP,
    '/emberfall balance ' + NON_OP,
    '/emberfall relic state ' + NON_OP,
    '/emberfall relic gold ' + NON_OP + ' 999',
    '/character list',
    '/character select vanguard',
    '/shop',
  ];
  const guestReply = {};
  for (const cmd of gated) {
    guestReply[cmd] = await ask(guest, cmd);
    check('non-op refused: ' + cmd, UNKNOWN.test(guestReply[cmd]), short(guestReply[cmd]));
  }

  // ---- 2. The two public pieces still work for the non-op (and are NOT answered with "Unknown").
  const leave = await ask(guest, '/expedition leave');
  check('non-op: /expedition leave is reachable (not Unknown)', leave.length > 0 && !UNKNOWN.test(leave), short(leave));
  check('non-op: /expedition leave says they are not on a run', /not on an expedition/i.test(leave), short(leave));
  const start = await ask(guest, '/expedition');
  check('non-op: bare /expedition is reachable (not Unknown)', start.length > 0 && !UNKNOWN.test(start), short(start));
  check('non-op: bare /expedition points to the hub instead of starting a run', /Ember Hearth|departure plate/i.test(start), short(start));

  // ---- 2b. Tab completion (measured on #11 follow-up; raw lists kept in the PR). Two different things, deliberately kept apart:
  //   CHILDREN of a gated root are hidden from the non-op (`requires` works on children): asserted, each with an operator control.
  //   ROOT NAMES are NOT hidden by this server: the non-op's list for "/" is identical to the operator's (93 = 93) and includes
  //   every vanilla op-only root too (ban, op, stop, gamemode, ...). So this is server-wide behaviour, not an Emberfall leak.
  //   That is DOCUMENTED below with the vanilla comparison as its control, never asserted as a promise. A real client may
  //   filter the list itself; this test uses a mineflayer client, so what a human sees is unverified.
  const tab = async (bot, text) => {
    try { const r = await bot.tabComplete(text, false, false); return (r || []).map(x => typeof x === 'string' ? x : (x.match ?? x.text ?? JSON.stringify(x))); }
    catch (e) { return ['ERR ' + e.message]; }
  };
  const bad = l => l.some(x => /^ERR/.test(x));
  for (const [root, expectedOpChildren] of [['/emberfall ', 65], ['/character ', 2]]) {
    const g = await tab(guest, root), o = await tab(op, root);
    check('tab: operator control, ' + root.trim() + ' offers children (' + expectedOpChildren + ' expected)', !bad(o) && o.length === expectedOpChildren, 'op ' + o.length);
    check('tab: the non-op is offered NO children of ' + root.trim(), !bad(g) && g.length === 0, 'non-op ' + g.length + ' vs op ' + o.length);
  }
  const gRoots = await tab(guest, '/'), oRoots = await tab(op, '/');
  const vanillaOpOnly = ['ban', 'op', 'deop', 'stop', 'gamemode', 'give', 'kill', 'tp', 'summon', 'setblock', 'fill', 'execute', 'whitelist', 'kick', 'save-all', 'reload', 'data'];
  check('tab control: the root lists were read (operator sees the 4 mod roots)', !bad(oRoots) && ['emberfall', 'character', 'shop', 'expedition'].every(r => oRoots.includes(r)), 'op ' + oRoots.length);
  const modRootsSeen = ['emberfall', 'character', 'shop', 'expedition'].filter(r => gRoots.includes(r));
  const vanillaSeen = vanillaOpOnly.filter(r => gRoots.includes(r));
  console.log('DOC tab roots offered to the non-op: ' + JSON.stringify(modRootsSeen) + '; vanilla op-only roots offered too: ' + vanillaSeen.length + '/' + vanillaOpOnly.length + '; lists identical: ' + (JSON.stringify([...gRoots].sort()) === JSON.stringify([...oRoots].sort())));
  // The honest assertion: the mod is no different from vanilla. If this ever fails, the server started hiding roots (fine, update the note) or the mod exposes something vanilla does not.
  check('tab: the mod roots are treated exactly like vanilla op-only roots (both listed, or both hidden)', modRootsSeen.length === 0 ? vanillaSeen.length === 0 : (modRootsSeen.length === 4 && vanillaSeen.length === vanillaOpOnly.length), 'mod ' + modRootsSeen.length + '/4, vanilla ' + vanillaSeen.length + '/' + vanillaOpOnly.length);
  const expTab = await tab(guest, '/expedition ');
  check('tab: the non-op is offered /expedition leave (public on purpose)', expTab.includes('leave'), JSON.stringify(expTab));

  // ---- 3. Nothing the non-op typed changed who is an operator, their mode or their wallet.
  await sleep(500);
  let opsAfter = [];
  try { opsAfter = JSON.parse(fs.readFileSync(path.join(HOME, 'run', 'server', 'ops.json'), 'utf8')); } catch (e) { /* ignore */ }
  check('after the attempts, ' + NON_OP + ' is still not an operator', !opsAfter.some(o => o.name === NON_OP));
  check('after the attempts, ' + NON_OP + ' is still in survival', guest.game.gameMode === 'survival', String(guest.game.gameMode));

  // ---- 4. Controls: the operator is NOT refused for the same gated commands, so the checks above are not vacuous.
  const opControls = [
    ['/emberfall mapreport', /MAP /],
    ['/emberfall buildinfo ' + OP, /build/i],
    ['/emberfall balance ' + OP, /Silver/],
    ['/emberfall relic state ' + OP, /RELIC state/],
    ['/character list', /Characters/],
  ];
  for (const [cmd, expect] of opControls) {
    const r = await ask(op, cmd);
    check('operator control: ' + cmd, !UNKNOWN.test(r) && expect.test(r), short(r));
  }
  // /shop opens a window and prints nothing, so its control is "no Unknown" only.
  const shop = await ask(op, '/shop');
  check('operator control: /shop is not refused', !UNKNOWN.test(shop), short(shop));
  check('operator control: the operator stays an operator', JSON.parse(fs.readFileSync(path.join(HOME, 'run', 'server', 'ops.json'), 'utf8')).some(o => o.name === OP && o.level >= 2));

  console.log(fails === 0 ? 'RESULT: ALL PASS' : 'RESULT: ' + fails + ' FAILED');
  guest.quit(); op.quit();
  setTimeout(() => process.exit(0), 400);
})().catch(e => { console.log('FAIL suite crashed: ' + (e && e.stack || e)); console.log('RESULT: crashed'); process.exit(0); });
