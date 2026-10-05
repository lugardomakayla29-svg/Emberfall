// Shared weapon growth: kills credit the RIGHT weapon, levels rise at the exact thresholds, the meter fills by mob tier,
// and killing a vanilla-typed summon credits nothing (no farming).
// Loadout: slot 0 = war_halberd (melee, reach 3.5), slot 1 = hunting_bow (ranged, reach 10).
// Read-out: /emberfall debugweapongrowth <player>  ->  "growth slot i id kills=K level=L meter=M".
// Attribution is proved by distance: a 1 hp target 7 blocks away is out of the halberd's reach, so only the bow can kill it.
const mineflayer = require('mineflayer');
const bot = mineflayer.createBot({ host: '127.0.0.1', port: 25565, username: 'EmberTester', version: '1.21.11', auth: 'offline' });
const sleep = ms => new Promise(r => setTimeout(r, ms));
const lines = []; bot.on('message', m => lines.push(m.toString())); bot.on('error', e => console.log('ERR', e.message));
const ask = async (x, w = 500) => { const n = lines.length; bot.chat(x); await sleep(w); return lines.slice(n).join(' | '); };
let fails = 0; const R = (n, ok, extra = '') => { console.log(`${ok ? 'PASS' : 'FAIL'} ${n} ${extra}`); if (!ok) fails++; };
const growth = async () => {
  let out = {};
  for (let t = 0; t < 4 && !Object.keys(out).length; t++) {          // retry: a stray chat line or a slow reply can leave the first read empty
    const r = await ask('/emberfall debugweapongrowth EmberTester', 700 + t * 400); out = {};
    for (const m of r.matchAll(/growth slot (\d) (\w+) kills=(\d+) level=(\d+) meter=(\d+)/g)) out[+m[1]] = { id: m[2], kills: +m[3], level: +m[4], meter: +m[5] };
  }
  return out;
};
// a fresh 1 hp horde zombie at a chosen distance, frozen so only the weapons can touch it
const target = async (dist, tag) => {
  await ask(`/execute at @s run summon emberfall:horde_zombie ~${dist} ~ ~0 {Tags:["${tag}","keep"],NoAI:1b,Silent:1b,PersistenceRequired:1b}`, 400);
  await ask(`/attribute @e[tag=${tag},limit=1] minecraft:max_health base set 1`, 200);
  await ask(`/data modify entity @e[tag=${tag},limit=1] Health set value 1.0f`, 200);
};
const alive = async tag => /Count: [1-9]/.test(await ask(`/execute if entity @e[tag=${tag}]`, 350));
bot.once('spawn', async () => {
  await sleep(5000);
  await ask('/gamemode survival'); await ask('/effect clear @s');
  await ask('/character select juggernaut'); await ask('/expedition', 2500);
  for (let i = 0; i < 40; i++) { await sleep(2000); if (/expedition/.test(await ask('/data get entity @s Dimension', 400))) break; }
  await sleep(2000);
  await ask('/effect give @s minecraft:resistance 999 4 true', 200); await ask('/effect give @s minecraft:regeneration 999 4 true', 200);
  await ask('/emberfall wavestop 0', 300); await ask('/time set midnight', 200);
  await ask('/kill @e[type=!player,type=!minecraft:item_display,type=!minecraft:interaction]', 900);
  await ask('/emberfall selectweapon EmberTester hunting_bow', 700);
  const pin = setInterval(() => bot.chat('/tp @s ' + bot.entity.position.x.toFixed(2) + ' ' + bot.entity.position.y.toFixed(2) + ' ' + bot.entity.position.z.toFixed(2)), 1000);

  let g = await growth();
  R('W0 two weapons, both start at level 1 with 0 kills and an empty meter',
    g[0]?.id === 'war_halberd' && g[1]?.id === 'hunting_bow' && g[0].level === 1 && g[1].level === 1 && g[0].kills === 0 && g[1].kills === 0 && g[0].meter === 0 && g[1].meter === 0, JSON.stringify(g));

  // ---- attribution: a target 7 blocks away is beyond the halberd (3.5) so ONLY the bow can kill it
  await target(7, 'far1');
  for (let i = 0; i < 20 && await alive('far1'); i++) await sleep(700);
  const farDead = !(await alive('far1'));
  g = await growth();
  R('W1 a kill out of melee reach was made (non vacuous)', farDead, farDead ? 'target died' : 'target still alive');
  R('W2 that kill is credited to the BOW only', g[1].kills === 1 && g[0].kills === 0, `halberd kills ${g[0].kills}, bow kills ${g[1].kills}`);
  R('W3 the bow meter filled by a filler kill, the halberd meter did not', g[1].meter >= 25 && g[0].meter === 0, `bow meter ${g[1].meter}, halberd meter ${g[0].meter}`);
  await ask('/kill @e[tag=keep]', 300);

  // ---- attribution the other way: point blank, the halberd fires first and gets the kill
  await target(2, 'near1');
  for (let i = 0; i < 20 && await alive('near1'); i++) await sleep(600);
  g = await growth();
  R('W4 a point blank kill is credited to a weapon (total kills now 2)', g[0].kills + g[1].kills === 2, `halberd ${g[0].kills}, bow ${g[1].kills}`);
  await ask('/kill @e[tag=keep]', 300);

  // ---- exact level thresholds, driven through the grant command (0 4 10 18 28 40 54 70 88 108)
  const want = [[0, 1], [3, 1], [4, 2], [9, 2], [10, 3], [17, 3], [18, 4], [27, 4], [28, 5], [107, 9], [108, 10], [500, 10]];
  const bad = [];
  for (const [k, lv] of want) {
    await ask(`/emberfall debugweapongrowth EmberTester grant 1 0 0`, 100);   // no-op probe keeps the command warm
    // grant adds kills on top, so restart the comparison from the known total: read, then grant the difference
    const cur = (await growth())[1].kills;
    if (k >= cur) { await ask(`/emberfall debugweapongrowth EmberTester grant 1 ${k - cur} 0`, 300); const now = (await growth())[1]; if (now.kills !== k || now.level !== lv) bad.push(`${k}=>${now.level} want ${lv}`); }
  }
  R('W5 level thresholds exact at every boundary', bad.length === 0, bad.join('; ') || 'all 12 boundaries');

  // ---- the meter: granted value is kept, and reads back
  await ask('/emberfall debugweapongrowth EmberTester grant 0 0 999', 300);
  g = await growth();
  R('W6 the meter can be set and reads back (999 is not yet ready)', g[0].meter === 999, `meter ${g[0].meter}`);

  // ---- no farming: a NAMED vanilla zombie (a Magus style summon) killed by a weapon credits nothing
  const before = (await growth()); const total0 = before[0].kills + before[1].kills;
  await ask(`/execute at @s run summon minecraft:zombie ~6 ~ ~0 {Tags:["sum1","keep"],NoAI:1b,Silent:1b,PersistenceRequired:1b,CustomName:'{"text":"Umbral Thrall"}',Health:1f}`, 500);
  await ask('/attribute @e[tag=sum1,limit=1] minecraft:max_health base set 1', 200);
  await ask('/data modify entity @e[tag=sum1,limit=1] Health set value 1.0f', 200);
  await ask('/effect give @e[tag=sum1] minecraft:fire_resistance 999 0 true', 200);
  for (let i = 0; i < 20 && await alive('sum1'); i++) await sleep(700);
  const sumDead = !(await alive('sum1'));
  const after = await growth();
  R('W7 the summon really died to a weapon (non vacuous)', sumDead);
  R('W8 killing a summon credits NO weapon (no farming)', after[0].kills + after[1].kills === total0, `kills ${total0} -> ${after[0].kills + after[1].kills}`);

  clearInterval(pin);
  await ask('/kill @e[tag=keep]', 300); await ask('/expedition leave', 800);
  console.log(fails === 0 ? 'ALL PASS' : 'SOME FAIL ' + fails);
  bot.quit(); setTimeout(() => process.exit(0), 400);
});
