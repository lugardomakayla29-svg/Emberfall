// DAMAGE METER. One weapon per run (env METER_CHAR = vanguard|duelist|juggernaut|gravedigger|reaper|ranger|battlemage|emberwarden, METER_SLOT = 0).
// At levels 1, 5, 10 (kills granted on the way up: grant only ever ADDS) it measures how much health the weapon removes in a fixed window from
//   SINGLE: one foe 2.5 blocks ahead      GROUP: five foes clustered 2.0-3.5 blocks ahead
// Foes are NoAI, 1,000,000 hp (never die, never move), so the number is pure weapon output. Output: METER <level> <scenario> dmg=<total> dps=<per second>.
const mineflayer = require('mineflayer');
const bot = mineflayer.createBot({ host: '127.0.0.1', port: 25565, username: 'EmberTester', version: '1.21.11', auth: 'offline' });
const sleep = ms => new Promise(r => setTimeout(r, ms));
const lines = []; bot.on('message', m => lines.push(m.toString())); bot.on('error', e => console.log('ERR', e.message));
const ask = async (x, w = 450) => { const n = lines.length; bot.chat(x); await sleep(w); return lines.slice(n).join(' | '); };
const CHAR = process.env.METER_CHAR || 'vanguard', WINDOW = +(process.env.METER_WINDOW || 10000);
const BIG = 1000000;
const hp = async tag => { for (let t = 0; t < 3; t++) { const r = await ask(`/data get entity @e[tag=${tag},limit=1] Health`, 380 + t * 250); const m = /has the following entity data: (-?[\d.]+)f/.exec(r); if (m) return +m[1]; if (process.env.METER_DEBUG) console.log('HPRAW', tag, JSON.stringify(r).slice(0, 160)); } return null; };
const foe = async (tag, dx, dz) => {
  await ask(`/execute at @s run summon emberfall:horde_zombie ~${dx} ~ ~${dz} {Tags:["${tag}","keep"],NoAI:1b,Silent:1b,PersistenceRequired:1b}`, 260);
  await ask(`/attribute @e[tag=${tag},limit=1] minecraft:max_health base set ${BIG}`, 110);
  await ask(`/data modify entity @e[tag=${tag},limit=1] Health set value ${BIG}.0f`, 110);
};
const clear = async () => { await ask('/kill @e[tag=keep]', 500); };
const measure = async (label, tags) => {
  // A dropped health read used to count as 0 damage (indistinguishable from a weapon that does nothing). Now: retry the whole scenario, then say INVALID.
  for (let attempt = 1; attempt <= 3; attempt++) {
    const before = []; for (const t of tags) before.push(await hp(t));
    await sleep(WINDOW);
    let total = 0, per = [], bad = 0;
    for (let i = 0; i < tags.length; i++) {
      const a = await hp(tags[i]);
      if (before[i] === null || a === null) { bad++; per.push(null); continue; }
      const d = before[i] - a; per.push(Math.round(d)); total += d;
    }
    if (bad === 0) {
      console.log(`METER ${lvl} ${label} dmg=${Math.round(total)} dps=${(total / (WINDOW / 1000)).toFixed(1)} perfoe=${JSON.stringify(per)}`);
      return;
    }
    console.log(`METER_RETRY ${lvl} ${label} attempt=${attempt} unreadable=${bad}`);
  }
  console.log(`METER ${lvl} ${label} INVALID (health unreadable after 3 attempts)`);
};
let lvl = 1;
bot.once('spawn', async () => {
  await sleep(5000);
  await ask('/gamemode survival'); await ask('/effect clear @s');
  await ask(`/character select ${CHAR}`); await ask('/expedition', 2500);
  for (let i = 0; i < 40; i++) { await sleep(2000); if (/expedition/.test(await ask('/data get entity @s Dimension', 400))) break; }
  await sleep(2000);
  await ask('/effect give @s minecraft:resistance 999 4 true', 200); await ask('/effect give @s minecraft:regeneration 999 4 true', 200);
  await ask('/emberfall wavestop 0', 300); await ask('/time set midnight', 200);
  await ask('/kill @e[type=!player,type=!minecraft:item_display,type=!minecraft:interaction]', 900);
  const bx = bot.entity.position.x, by = bot.entity.position.y, bz = bot.entity.position.z;
  const pin = setInterval(() => bot.chat(`/tp @s ${bx.toFixed(2)} ${by.toFixed(2)} ${bz.toFixed(2)} 0 0`), 700);   // facing south (+z): foes go to +z
  console.log('LOADOUT', (await ask('/emberfall debugloadout EmberTester', 700)).slice(0, 160));
  for (const target of [1, 5, 10]) {
    const have = 0;
    if (target > 1) {
      const need = { 5: 28, 10: 108 }[target] - { 5: 0, 10: 28 }[target];
      await ask(`/emberfall debugweapongrowth EmberTester grant 0 ${need} 0`, 500);
    }
    lvl = target;
    const g = await ask('/emberfall debugweapongrowth EmberTester', 700); console.log('STATE', g.slice(0, 140));
    // SINGLE
    await clear(); await foe('s1', 0, 2.5); await sleep(600); await measure('single', ['s1']); await clear();
    // GROUP of five
    // GROUP of nine (so a weapon with a cap of 4-6 extra targets is not saturated)
    await clear();
    const nine=[[-2.2,2.4],[-1.1,2.2],[0,2.0],[1.1,2.3],[2.2,2.5],[-1.6,3.5],[-0.5,3.4],[0.7,3.6],[1.8,3.3]];
    for (let i=0;i<nine.length;i++) await foe('n'+i, nine[i][0], nine[i][1]);
    await sleep(600); await measure('group9', nine.map((_,i)=>'n'+i)); await clear();
    await foe('g1', -1.0, 2.2); await foe('g2', 0.8, 2.6); await foe('g3', -0.3, 3.2); await foe('g4', 1.2, 3.4); await foe('g5', 0.0, 2.0);
    await sleep(600); await measure('group5', ['g1', 'g2', 'g3', 'g4', 'g5']); await clear();
  }
  clearInterval(pin); await ask('/expedition leave', 800);
  console.log('METER_DONE');
  bot.quit(); setTimeout(() => process.exit(0), 300);
});
