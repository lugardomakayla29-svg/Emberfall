// V4 live, part 1 (in a map run): four of the five new gameplay cues, each with a control that must NOT fire.
//   tome_picked, free_chest_appears, shrine_trial_cleared, swarm_begins.  (gate_countdown is cues_gate_test.js: it needs the hub.)
// The verdict comes from the SERVER LOG (cues_grade.py reads the SOUND_TEST lines); this script prints what it did and checks the
// state the cues depend on. A SOUND_TEST line proves the cue's code ran. It does not prove anyone can hear it.
const mineflayer = require('mineflayer'); const fs = require('fs');
const D = JSON.parse(fs.readFileSync('src/main/resources/data/emberfall/map/expedition_map.json', 'utf8'));
const sleep = ms => new Promise(r => setTimeout(r, ms));
const bot = mineflayer.createBot({ host: '127.0.0.1', port: 25565, username: 'EmberTester', version: '1.21.11', auth: 'offline' });
const lines = []; bot.on('message', m => lines.push(m.toString())); bot.on('error', e => console.log('ERR', e.message));
const ask = async (x, w = 600) => { const n = lines.length; bot.chat(x); await sleep(w); return lines.slice(n).join(' | '); };
let fails = 0; const check = (n, ok, note = '') => { console.log((ok ? 'PASS ' : 'FAIL ') + n + ' ' + note); if (!ok) fails++; };
const vi = n => { const out = []; do { let b = n & 0x7f; n >>>= 7; if (n) b |= 0x80; out.push(b); } while (n); return Buffer.from(out); };
const sendTome = (level, idx) => bot._client.write('custom_payload', { channel: 'emberfall:choose_tome', data: Buffer.concat([vi(level), vi(idx < 0 ? 0xffffffff : idx)]) });
const sendShrine = (type, option) => { const t = Buffer.from(type, 'utf8'); bot._client.write('custom_payload', { channel: 'emberfall:choose_shrine', data: Buffer.concat([Buffer.from([t.length]), t, Buffer.from([option])]) }); };
const C = D.shrines.find(s => s.type === 'challenge');
const MODE = process.env.CUES_MODE || 'main'; // 'main' = tome, free chest, shrine (director stopped); 'swarm' = swarm start (director alive)
const mark = async tag => { await ask(`/say MARK_${tag}`, 400); };
bot.once('spawn', async () => {
  await sleep(6500);
  await ask('/gamemode survival'); await ask('/effect give @s minecraft:resistance 999 4 true', 300); await ask('/effect give @s minecraft:regeneration 999 4 true', 300);
  await ask('/character select juggernaut', 600); await ask('/expedition', 1500);
  for (let i = 0; i < 60; i++) { await sleep(2000); if (/expedition/.test(await ask('/data get entity @s Dimension', 400))) break; }
  await sleep(2000);
  if (MODE === 'swarm') {
    // The director must be ALIVE for the swarm (as in a real run), so no wavestop here. Same hooks as swarm_test.js.
    const st0 = await ask('/emberfall wavestatus 0', 700); console.log('director before swarm:', st0.slice(0, 90));
    await mark('SWARM_BEGIN');
    const s1 = await ask('/emberfall relic swarm EmberTester start', 1500); console.log('swarm#1:', s1.slice(0, 60));
    await mark('SWARM_END');
    await mark('SWARM_AGAIN_BEGIN');
    const s2 = await ask('/emberfall relic swarm EmberTester start', 1500); console.log('swarm#2:', s2.slice(0, 60));
    await mark('SWARM_AGAIN_END');
    check('W0 the first start really started a swarm (not "norun")', /started/.test(s1), s1.slice(0, 50));
    await ask('/kill @e[tag=emberfall_swarm]', 500);
    await ask('/expedition leave', 1500); await sleep(1200);
    console.log(fails === 0 ? 'ALL PASS' : 'SOME FAIL ' + fails); bot.quit(); setTimeout(() => process.exit(fails ? 1 : 0), 400);
    return;
  }
  await ask('/emberfall wavestop 0', 500);
  await ask('/kill @e[type=!player,type=!minecraft:item_display,type=!minecraft:interaction]', 600);

  // 1 TOME. A real pick plays the cue; a skip (index -1) must not.
  await mark('TOME_PICK_BEGIN');
  await ask('/emberfall tomeopen EmberTester 2', 900); sendTome(2, 0); await sleep(1200);
  await mark('TOME_PICK_END');
  await mark('TOME_SKIP_BEGIN');
  await ask('/emberfall tomeopen EmberTester 3', 900); sendTome(3, -1); await sleep(1200);
  await mark('TOME_SKIP_END');

  // 2 FREE CHEST. A boss source drops one (cue). Ask for more than the run cap allows: the refused ones must not cue.
  await mark('FREE_BEGIN');
  const f1 = await ask('/emberfall relic free EmberTester boss', 900); console.log('free#1:', f1.slice(0, 80));
  await mark('FREE_END');
  await mark('FREE_REFUSE_BEGIN');
  const frs = []; for (let i = 0; i < 14; i++) frs.push((await ask('/emberfall relic free EmberTester boss', 450)).match(/placed=(\w+) given=(\d+)/));
  await mark('FREE_REFUSE_END');
  const placedCount = frs.filter(m => m && m[1] === 'true').length; console.log('boss free chests placed in 14 more tries:', placedCount, 'last given:', frs[13] && frs[13][2]);

  // 3 SHRINE TRIAL. Start the small trial, then end it by killing every foe: clearing it is the cue.
  await ask(`/tp @s ${C.x + 4} 65 ${C.z + 4}`, 1500);
  await mark('SHRINE_BEGIN');
  sendShrine('challenge', 0); await sleep(2000);
  await ask('/kill @e[type=!player,type=!minecraft:item_display,type=!minecraft:interaction]', 800); await sleep(2500);
  await mark('SHRINE_END');

  await ask('/expedition leave', 1500); await sleep(1200);
  check('S0 the test reached the end (every command above ran)', true);
  console.log('DONE'); bot.quit(); setTimeout(() => process.exit(0), 400);
});
setTimeout(() => { console.log('TIMEOUT'); process.exit(2); }, 280000);
