// RunMobPurge (world/RunMobPurge.java): inside a live run every foreign mob is removed on entry AND the moment it
// spawns; named, tamed and leashed mobs stay; outside a run nothing is touched.
const mineflayer = require('mineflayer');
const bot = mineflayer.createBot({ host: '127.0.0.1', port: 25565, username: 'EmberTester', version: '1.21.11', auth: 'offline' });
const sleep = ms => new Promise(r => setTimeout(r, ms));
const lines = []; bot.on('message', m => lines.push(m.toString()));
const ask = async (x, w = 400) => { const n = lines.length; bot.chat(x); await sleep(w); return lines.slice(n).join(' | '); };
const res = []; const check = (n, ok, note) => { res.push(ok); console.log((ok ? 'PASS ' : 'FAIL ') + n + ' ' + note); };
const count = async sel => {
  await ask('/scoreboard objectives add pgc dummy', 150); await ask('/scoreboard players set #n pgc -1', 150);
  await ask(`/execute store result score #n pgc if entity ${sel}`, 300);
  const r = await ask('/scoreboard players get #n pgc', 400); const m = /has (-?\d+) \[pgc\]/.exec(r); return m ? +m[1] : NaN;
};
const base = 'NoAI:1b,PersistenceRequired:1b,Silent:1b';
const put = (t, tags, extra, off) => bot.chat(`/execute at @s run summon minecraft:${t} ${off} {Tags:[${tags}],${base}${extra ? ',' + extra : ''}}`);
bot.once('spawn', async () => {
  await sleep(6500);
  await ask('/gamemode survival'); await ask('/effect give @s minecraft:resistance 999 4 true', 300); await ask('/effect give @s minecraft:regeneration 999 4 true', 300);
  await ask('/kill @e[type=!player]', 600); await sleep(1200);
  // T0: OUTSIDE a run, 4 foreign mobs must all still exist after 3s
  put('zombie', '"pg_x"', '', '~3 ~ ~'); put('cow', '"pg_x"', '', '~3 ~ ~2'); put('villager', '"pg_x"', '', '~3 ~ ~-2'); put('creeper', '"pg_x"', '', '~5 ~ ~');
  await sleep(3000);
  check('T0 outside a run nothing is removed', (await count('@e[tag=pg_x]')) === 4, `left=${await count('@e[tag=pg_x]')} of 4`);
  await ask('/kill @e[tag=pg_x]', 500);
  await ask('/character select juggernaut', 600); await ask('/expedition', 1500);
  // The map builds async (about 32 s). Without this wait the bot is still in the hub: the removal checks fail loudly, but the
  // keep-checks pass because nothing is removed there. Poll the dimension and assert it, so a keep-check cannot be vacuous.
  let inRun = false; for (let i = 0; i < 80 && !inRun; i++) { await sleep(1500); inRun = /expedition/.test(await ask('/data get entity @s Dimension', 400)); }
  await sleep(2000);
  check('R0 the bot is inside a run (the keep-checks below mean nothing in the hub)', inRun, `inRun=${inRun}`);
  await ask('/kill @e[type=!player,type=!minecraft:item_display]', 700);
  // T1: same-tick placement of foreign mobs INSIDE the run, count a moment later -> load hook and sweep must clear them
  put('zombie', '"pg_f"', '', '~4 ~ ~'); put('cow', '"pg_f"', '', '~4 ~ ~2'); put('villager', '"pg_f"', '', '~4 ~ ~-2'); put('creeper', '"pg_f"', '', '~6 ~ ~'); put('skeleton', '"pg_f"', '', '~6 ~ ~2');
  await sleep(2600);
  check('T1 all 5 foreign mobs inside the run were removed', (await count('@e[tag=pg_f]')) === 0, `left=${await count('@e[tag=pg_f]')}`);
  // T2: things that must be spared
  put('zombie', '"pg_k","named"', "CustomName:'\"Bob\"'", '~-4 ~ ~');
  put('cow', '"pg_k","named2"', "CustomName:'\"Bessie\"'", '~-4 ~ ~2');
  put('wolf', '"pg_k","tamed"', 'Owner:[I;1,2,3,4]', '~-4 ~ ~-2');
  await sleep(2600);
  const keep = async t => count(`@e[tag=pg_k,tag=${t}]`);
  const kz = await keep('named'), kc = await keep('named2'), kw = await keep('tamed');
  check('T2 a NAMED zombie and a NAMED cow are spared', kz === 1 && kc === 1, `named zombie=${kz} named cow=${kc}`);
  console.log('   tamed wolf survivors (fake Owner UUID):', kw);
  // T3: mobs that appear LATER (spawn-time hook)
  put('zombie', '"pg_l"', '', '~5 ~ ~'); put('phantom', '"pg_l"', '', '~5 ~2 ~');
  await sleep(300);
  check('T3 a mob that spawns mid-run is removed at once (300 ms)', (await count('@e[tag=pg_l]')) === 0, `left=${await count('@e[tag=pg_l]')}`);
  // T4: the run's own mobs are never touched
  await ask('/emberfall spawnelite umbral_magus', 900); await sleep(2600);
  check('T4 the run\'s own Umbral Magus survives the purge', (await count('@e[type=emberfall:umbral_magus]')) === 1, `magus=${await count('@e[type=emberfall:umbral_magus]')}`);
  console.log(res.every(Boolean) ? 'ALL PASS' : 'SOME FAIL');
  bot.quit(); setTimeout(() => process.exit(0), 300);
});
bot.on('error', e => console.log('ERR', e.message));
