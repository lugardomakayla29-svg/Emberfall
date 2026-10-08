const mineflayer = require('mineflayer');
const bot = mineflayer.createBot({ host: '127.0.0.1', port: 25565, username: 'EmberTester', version: '1.21.11', auth: 'offline' });
const sleep = ms => new Promise(r => setTimeout(r, ms));
const lines = []; bot.on('message', m => lines.push(m.toString()));
const ask = async (x, w = 500) => { const n = lines.length; bot.chat(x); await sleep(w); return lines.slice(n).join(' | '); };
const res = []; const check = (n, ok, note) => { res.push(ok); console.log((ok ? 'PASS ' : 'FAIL ') + n + ' ' + note); };
// count via devdump: minis are DevourerSpawn, their heads are untagged displays riding them, so count entity types by /execute
const count = async sel => { // exact count via /execute store, read back from the scoreboard (chat replies can be split or late)
  await ask('/scoreboard objectives add mwc dummy', 200);
  await ask('/scoreboard players set #n mwc -1', 200);
  await ask(`/execute store result score #n mwc if entity ${sel}`, 300);
  const r = await ask('/scoreboard players get #n mwc', 500); const m = /has (-?\d+) \[mwc\]/.exec(r) || /(-?\d+)/.exec(r); return m ? +m[1] : NaN; };
const countR = async sel => { for (let k = 0; k < 4; k++) { const v = await count(sel); if (!Number.isNaN(v) && v >= 0) return v; } return NaN; };   // retry a swallowed or late reply
const setHp = async v => ask(`/data modify entity @e[type=emberfall:devourer_brain,limit=1] Health set value ${v}f`, 500);
bot.once('spawn', async () => {
  await sleep(6500);
  await ask('/kill @e[type=!player]', 600); await ask('/kill @e[type=minecraft:item_display]', 600); await sleep(1500);
  await ask('/gamemode survival'); await ask('/effect give @s minecraft:resistance 999 4 true', 300); await ask('/effect give @s minecraft:regeneration 999 4 true', 300);
  await ask('/character select juggernaut', 600); await ask('/expedition', 1500);
  // The map builds async (about 32 s). Poll the dimension so the checks below run in the RUN, not in the hub, and assert it (R0).
  let inRun = false; for (let i = 0; i < 80 && !inRun; i++) { await sleep(1500); inRun = /expedition/.test(await ask('/data get entity @s Dimension', 400)); }
  await sleep(1500);
  check('R0 the bot is inside a run (nothing below means anything in the hub)', inRun, `inRun=${inRun}`);
  const HEADSEL = '@e[type=minecraft:item_display,nbt={item:{id:"minecraft:player_head"}}]';
  const baseHeads = await countR(HEADSEL); console.log('baseline player-head displays in the run BEFORE the boss:', baseHeads);
  console.log('boss:', (await ask('/emberfall bossdevourer 0', 1200)).slice(0, 100)); await sleep(2500);
  await ask('/emberfall wavestop 0', 400); await ask('/kill @e[type=!player,type=!emberfall:devourer_brain,type=!emberfall:devourer_spawn,type=!minecraft:item_display]', 600);   // boss stays; wave mobs and their auto-weapon prey go
  const mx = 260;   // DevourerBrain MAX_HEALTH, read from the source (the attribute query does not work through /data)
  const heads = async () => countR('@e[type=minecraft:item_display,nbt={item:{id:"minecraft:player_head"}}]');
  const minis = async () => countR('@e[type=emberfall:devourer_spawn]');
  const hp1 = async () => { const r = await ask('/data get entity @e[type=emberfall:devourer_brain,limit=1] Health', 500); const m = /: ([\d.]+)f/.exec(r); return m ? +m[1] : NaN; };
  const hp = async () => { for (let k = 0; k < 4; k++) { const v = await hp1(); if (!Number.isNaN(v)) return v; } return NaN; };
  const h0 = await hp(); const m0 = await minis(); check('M0 brain alive at full health, no minis', h0 > 200 && m0 === 0, `hp=${h0} minis=${m0}`);
  const steps = [[0.60, 2, 'phase 2 at 66% spawns 2'], [0.30, 0, 'phase 3 at 33% adds none'], [0.15, 1, 'mini wave at 20% adds exactly 1'], [0.06, 1, 'mini wave at 10% adds exactly 1']];
  let i = 1, before = 0, clearedTo = 0;
  for (const [f, want, label] of steps) {
    if (want === 1) {   // clear old minis and PROVE the clear worked, so 'added' is a true delta (a stale mini once made +1 read as 0)
      for (let tries = 0; tries < 4; tries++) { await ask('/kill @e[type=emberfall:devourer_spawn]', 400); await sleep(700); before = await minis(); if (before === 0) break; }
      clearedTo = before;
      if (before !== 0) console.log(`     WARN could not clear minis before M${i}: ${before} still alive`);
    }  // clear old minis so an exact +1 is provable
    await setHp((mx * f).toFixed(1));
    // Shield the brain so a slow (loaded) server cannot let the run player's auto-weapon finish it mid-check; logs showed the boss dying 21s in.
    await ask('/effect give @e[type=emberfall:devourer_brain,limit=1] minecraft:resistance 60 4 true', 200);
    // Wait for the spawn instead of a fixed sleep: under load the tick that sees the threshold can run late, which read as alive=0.
    // The server logs show each mini lives 70+ ticks, so polling for up to 4s cannot miss one.
    let got = 0;
    for (let w = 0; w < 8; w++) { await sleep(500); got = await minis(); if (got - before >= want) break; } const now = await hp(); const added = got - before; before = got;
    // a mini can be killed by the run player between steps, so judge what each step ADDED against the ones alive before it (never more than designed)
    check(`M${i++} ${label}`, (want === 2 ? added === 2 : want === 0 ? added <= 0 : (added === 1 && clearedTo === 0)) && now > 0, `alive=${got}, added this step=${added} (designed ${want}) brain hp=${now}`);
  }
  const headsWhile = await heads(); console.log('player-head displays with 4 minis alive:', headsWhile);
  await ask('/kill @e[type=emberfall:devourer_brain]', 800); await sleep(4500);
  const c = await minis(); const d = await heads();
  check('M3 boss death leaves no minis', c === 0, `minis=${c}`);
  check('M4 no stray head displays', d <= baseHeads, `player-head displays=${d}, baseline before the boss was ${baseHeads}`);
  console.log(res.every(Boolean) ? 'ALL PASS' : 'SOME FAIL');
  bot.quit(); setTimeout(() => process.exit(0), 300);
});
bot.on('error', e => console.log('ERR', e.message));
