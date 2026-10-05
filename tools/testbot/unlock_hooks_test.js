// Unlock hooks (level 15 and 3 cleared challenges) on the real paths. Based on shrinedrop_test.
// Challenge payout: clearing the small trial drops 15 Gold + 20 XP as pickups the player collects, and the run's Silver
// is raised by the flat bonus. Numbers come from the server (pickupstate) and the run-end log line.
const mineflayer = require('mineflayer'); const fs = require('fs'); const { Vec3 } = require('vec3');
const D = JSON.parse(fs.readFileSync('../mod/src/main/resources/data/emberfall/map/expedition_map.json', 'utf8'));
const sleep = ms => new Promise(r => setTimeout(r, ms));
const bot = mineflayer.createBot({ host: '127.0.0.1', port: 25565, username: 'EmberTester', version: '1.21.11', auth: 'offline' });
const lines = []; bot.on('message', m => lines.push(m.toString())); bot.on('error', e => console.log('ERR', e.message));
const ask = async (x, w = 500) => { const n = lines.length; bot.chat(x); await sleep(w); return lines.slice(n).join(' | '); };
let fails = 0; const check = (n, ok, note = '') => { console.log((ok ? 'PASS ' : 'FAIL ') + n + ' ' + note); if (!ok) fails++; };
const sendChoose = (type, option) => { const t = Buffer.from(type, 'utf8'); bot._client.write('custom_payload', { channel: 'emberfall:choose_shrine', data: Buffer.concat([Buffer.from([t.length]), t, Buffer.from([option])]) }); };
const pk = async () => { for (let k = 0; k < 4; k++) { const r = await ask('/emberfall pickupstate EmberTester', 600); const m = /PICKUPSTATE gold=(\d+) xpTotal=(\d+) level=(\d+)/.exec(r); if (m) return { gold: +m[1], xp: +m[2], level: +m[3] }; } return null; };
const C = D.shrines.find(s => s.type === 'challenge');
const unl = async () => { const t = await ask('/emberfall relic unlocks EmberTester', 700); return { raw: t, lv: +(t.match(/reach_level_15=(\d+)/)?.[1] ?? NaN), ch: +(t.match(/clear_3_challenges=(\d+)/)?.[1] ?? NaN), set: (t.match(/unlocks \[([^\]]*)\]/)?.[1] ?? '?') }; };
const startRun = async () => {
  await ask('/expedition leave', 1200); await ask('/expedition', 1500);
  for (let i = 0; i < 60; i++) { await sleep(2000); if (/expedition/.test(await ask('/data get entity @s Dimension', 400))) break; }
  await sleep(1500); await ask('/emberfall wavestop 0', 500); await ask('/kill @e[type=!player,type=!minecraft:item_display,type=!minecraft:interaction]', 600);
};
const clearOne = async () => {
  await ask(`/tp @s ${C.x + 4} 65 ${C.z + 4}`, 1500);
  sendChoose('challenge', 0); await sleep(2000);
  await ask('/kill @e[type=!player,type=!minecraft:item_display,type=!minecraft:interaction]', 800); await sleep(3500);
};
bot.once('spawn', async () => {
  await sleep(6500);
  await ask('/gamemode survival'); await ask('/effect give @s minecraft:resistance 999 4 true', 300); await ask('/effect give @s minecraft:regeneration 999 4 true', 300);
  await ask('/character select juggernaut', 600);
  await startRun();
  let u = await unl();
  check('L0 fresh baseline: nothing unlocked, both counters 0', u.lv === 0 && u.ch === 0 && u.set === '', u.raw.slice(0, 150));
  // ---- reach level 15 on the real level detector
  let n = lines.length; await ask('/experience set @s 14 levels', 500); await sleep(2500);
  u = await unl();
  check('L1 level 14: progress 14, not unlocked, no announcement', u.lv === 14 && !u.set.includes('reach_level_15') && !lines.slice(n).join(' ').includes('Relic unlocked'), u.raw.slice(0, 150));
  n = lines.length; await ask('/experience set @s 15 levels', 500); await sleep(2500);
  u = await unl(); const said = lines.slice(n).join(' | ');
  check('L2 level 15: unlocked', u.lv === 15 && u.set.includes('reach_level_15'), u.raw.slice(0, 150));
  check('L2b the player is told which relic (Iron Boots) and why', /Relic unlocked: Iron Boots/.test(said) && /Reach level 15/.test(said), said.slice(0, 200));
  n = lines.length; await ask('/experience set @s 16 levels', 500); await sleep(2500);
  check('L3 control: level 16 does not announce it a second time', !lines.slice(n).join(' ').includes('Relic unlocked'), '');
  // ---- clear 3 challenges, one per run, across runs
  const counts = [];
  for (let r = 0; r < 3; r++) {
    await startRun();
    n = lines.length; await clearOne();
    u = await unl(); counts.push(u.ch);
    const msg = lines.slice(n).join(' | ');
    if (r < 2) check(`C${r + 1} cleared trial ${r + 1}: progress ${r + 1}, not unlocked, no announcement`, u.ch === r + 1 && !u.set.includes('clear_3_challenges') && !msg.includes('Relic unlocked'), u.raw.slice(0, 150));
    else {
      check('C3 third cleared trial (a different run): unlocked', u.ch === 3 && u.set.includes('clear_3_challenges'), u.raw.slice(0, 150));
      check('C3b the player is told: Anvil of Dawn and why', /Relic unlocked: Anvil of Dawn/.test(msg) && /Clear 3 Challenge Shrines/.test(msg), msg.slice(0, 200));
    }
  }
  check('C0 progress rose one step per clear across runs', counts.join() === '1,2,3', counts.join());
  await ask('/expedition leave', 1200);
  console.log(fails === 0 ? 'ALL PASS' : 'SOME FAIL ' + fails); bot.quit(); setTimeout(() => process.exit(0), 400);
});
