// RunMobSweeper: vanilla hostiles inside a live run must vanish; named / tame / passive / villager mobs must stay.
const mineflayer = require('mineflayer');
const bot = mineflayer.createBot({ host: '127.0.0.1', port: 25565, username: 'EmberTester', version: '1.21.11', auth: 'offline' });
const sleep = ms => new Promise(r => setTimeout(r, ms));
const lines = []; bot.on('message', m => lines.push(m.toString()));
const ask = async (x, w = 400) => { const n = lines.length; bot.chat(x); await sleep(w); return lines.slice(n).join(' | '); };
const res = []; const check = (n, ok, note) => { res.push(ok); console.log((ok ? 'PASS ' : 'FAIL ') + n + ' ' + note); };
const count = async sel => {
  await ask('/scoreboard objectives add swc dummy', 150); await ask('/scoreboard players set #n swc -1', 150);
  await ask(`/execute store result score #n swc if entity ${sel}`, 300);
  const r = await ask('/scoreboard players get #n swc', 400); const m = /has (-?\d+) \[swc\]/.exec(r); return m ? +m[1] : NaN;
};
const HOSTILE = '@e[tag=sw_h]', KEEP = '@e[tag=sw_k]';
bot.once('spawn', async () => {
  await sleep(6500);
  await ask('/gamemode survival'); await ask('/effect give @s minecraft:resistance 999 4 true', 300); await ask('/effect give @s minecraft:regeneration 999 4 true', 300);
  await ask('/kill @e[type=!player]', 600); await sleep(1200);
  // BEFORE any run: hostiles must NOT be swept (the sweeper only acts inside a live run)
  await ask('/summon minecraft:zombie ~3 ~ ~ {Tags:["sw_h"],NoAI:1b,PersistenceRequired:1b,Silent:1b,ArmorItems:[{},{},{},{id:"minecraft:stone_button",count:1}]}', 400);
  await sleep(2500);
  check('T0 a hostile OUTSIDE a run is left alone', (await count(HOSTILE)) === 1, `zombies=${await count(HOSTILE)}`);
  await ask('/kill @e[tag=sw_h]', 500);
  await ask('/character select juggernaut', 600); await ask('/expedition', 1500);
  // The map builds async (about 32 s). Without this wait the bot is still in the hub: the removal checks fail loudly, but the
  // keep-checks pass because nothing is removed there. Poll the dimension and assert it, so a keep-check cannot be vacuous.
  let inRun = false; for (let i = 0; i < 80 && !inRun; i++) { await sleep(1500); inRun = /expedition/.test(await ask('/data get entity @s Dimension', 400)); }
  await sleep(1500);
  check('R0 the bot is inside a run (the keep-checks below mean nothing in the hub)', inRun, `inRun=${inRun}`);
  await ask('/kill @e[type=!player,type=!minecraft:item_display]', 700);
  // place EVERYTHING in one server tick: a single command that runs 8 summons via execute, then count at once
  const S = (t, nbt, off) => `execute at @s run summon minecraft:${t} ${off} {${nbt}}`;
  const base = 'NoAI:1b,PersistenceRequired:1b,Silent:1b';
  const cmds = [
    S('zombie', `Tags:["sw_h"],${base}`, '~4 ~ ~'), S('creeper', `Tags:["sw_h"],${base}`, '~4 ~ ~1'),
    S('skeleton', `Tags:["sw_h"],${base}`, '~4 ~ ~-1'), S('spider', `Tags:["sw_h"],${base}`, '~5 ~ ~'),
    S('zombie', `Tags:["sw_k","named"],${base},CustomName:'"Bob"'`, '~-4 ~ ~'),
    // RunMobPurge.shouldPurge spares only a named or leashed mob, a tame pet, or a mod mob. An UNNAMED cow or villager is purged by design
    // (purge_test T2 holds the same rule), so these keepers carry a custom name. The old test left them unnamed and could never pass T1/T3.
    S('cow', `Tags:["sw_k","named"],${base},CustomName:'"Daisy"'`, '~ ~ ~4'), S('villager', `Tags:["sw_k","named"],${base},CustomName:'"Elder"'`, '~ ~ ~-4'),
    S('wolf', `Tags:["sw_k"],${base},Owner:[I;1,2,3,4]`, '~2 ~ ~2')];
  const n0 = lines.length;
  for (const c of cmds) bot.chat('/' + c);           // fire back to back, no waits between
  await sleep(120);
  // Proof that every hostile was really summoned (the purge may remove them before the first count): the server confirms each summon.
  const fb = lines.slice(n0).join(' | '); const said = t => (fb.match(new RegExp('Summoned new ' + t, 'g')) || []).length;
  const hostSummoned = said('Zombie') + said('Creeper') + said('Skeleton') + said('Spider');   // the named zombie reads "Bob", not Zombie
  const h0 = await count(HOSTILE), k0 = await count(KEEP);
  console.log(`placed: hostiles=${h0} keepers=${k0}`);
  for (const t of ['zombie', 'cow', 'villager', 'wolf']) console.log('   keeper type', t, await count(`@e[tag=sw_k,type=minecraft:${t}]`));
  // The purge also acts on spawn, so the 4 hostiles are normally already gone at this first count (h0 0 to 4 are all valid). What must hold
  // is that all 4 keepers exist, otherwise T3 proves nothing. The hostiles' removal itself is judged by T2 below.
  check('T1 setup: server confirmed all 4 hostile summons and all 4 keepers exist', hostSummoned === 4 && k0 === 4, `hostile summons confirmed=${hostSummoned} h=${h0} keepers=${k0}`);
  await sleep(3500);                                   // several sweeps
  const h1 = await count(HOSTILE), k1 = await count(KEEP);
  check('T2 every vanilla hostile in the run was removed', h1 === 0, `hostiles left=${h1}`);
  check('T3 named zombie, named cow, named villager and tame wolf all survived', k1 === 4, `keepers left=${k1} of 4`);
  await ask('/summon minecraft:zombie ~5 ~ ~ {Tags:["sw_h"],NoAI:1b,PersistenceRequired:1b,Silent:1b}', 300);
  await sleep(2500);
  check('T4 a hostile that appears LATER is also removed', (await count(HOSTILE)) === 0, `hostiles=${await count(HOSTILE)}`);
  console.log(res.every(Boolean) ? 'ALL PASS' : 'SOME FAIL');
  bot.quit(); setTimeout(() => process.exit(0), 300);
});
bot.on('error', e => console.log('ERR', e.message));
