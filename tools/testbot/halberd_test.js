// War Halberd growth. juggernaut starts with ONLY the halberd. Foes are 100000 hp frozen horde zombies, pinned player, fixed layout.
//   H0  the halberd is the only weapon, slot 0
//   H1  level 1: a foe 3.6 blocks from the primary is OUTSIDE the 2.5 cleave radius (untouched)
//   H2  level 10: the same foe is INSIDE the 5.0 radius and is hurt
//   H3  level 10 cleaves more foes than level 1 from the same tight pack (count of pack members that lost hp)
//   U1  with the meter one hit short of full, the War Slam hurts a foe 4.6 blocks from the player (outside cleave and halberd reach)
//   U2  that foe is slowed (Slowness) by the slam, and the meter was consumed
const mineflayer = require('mineflayer');
const bot = mineflayer.createBot({ host: '127.0.0.1', port: 25565, username: 'EmberTester', version: '1.21.11', auth: 'offline' });
const sleep = ms => new Promise(r => setTimeout(r, ms));
const lines = []; bot.on('message', m => lines.push(m.toString())); bot.on('error', e => console.log('ERR', e.message));
const ask = async (x, w = 500) => { const n = lines.length; bot.chat(x); await sleep(w); return lines.slice(n).join(' | '); };
let fails = 0; const R = (n, ok, extra = '') => { console.log(`${ok ? 'PASS' : 'FAIL'} ${n} ${extra}`); if (!ok) fails++; };
const BIG = 1024;                                                     // Minecraft clamps max_health at 1024
const hp = async tag => { const r = await ask(`/data get entity @e[tag=${tag},limit=1] Health`, 420); const m = /entity data: (-?[\d.]+)f/.exec(r); return m ? +m[1] : null; };
const meter = async () => { const r = await ask('/emberfall debugweapongrowth EmberTester', 700); const m = /growth slot 0 war_halberd kills=(\d+) level=(\d+) meter=(\d+)/.exec(r); return m ? { kills: +m[1], level: +m[2], meter: +m[3] } : null; };
const foe = async (tag, dx, dz) => {
  await ask(`/execute at @s run summon emberfall:horde_zombie ~${dx} ~ ~${dz} {Tags:["${tag}","keep"],NoAI:1b,Silent:1b,PersistenceRequired:1b}`, 300);
  await ask(`/attribute @e[tag=${tag},limit=1] minecraft:max_health base set ${BIG}`, 120);
  await ask(`/data modify entity @e[tag=${tag},limit=1] Health set value ${BIG}.0f`, 120);
};
const g = async (kills, m) => ask(`/emberfall debugweapongrowth EmberTester grant 0 ${kills} ${m}`, 400);
const clear = async () => { await ask('/kill @e[tag=keep]', 500); };
bot.once('spawn', async () => {
  await sleep(5000);
  await ask('/gamemode survival'); await ask('/effect clear @s');
  await ask('/character select juggernaut'); await ask('/expedition', 2500);
  for (let i = 0; i < 40; i++) { await sleep(2000); if (/expedition/.test(await ask('/data get entity @s Dimension', 400))) break; }
  await sleep(2000);
  await ask('/effect give @s minecraft:resistance 999 4 true', 200); await ask('/effect give @s minecraft:regeneration 999 4 true', 200);
  await ask('/emberfall wavestop 0', 300); await ask('/time set midnight', 200);
  await ask('/kill @e[type=!player,type=!minecraft:item_display,type=!minecraft:interaction]', 900);
  const lo = await ask('/emberfall debugloadout EmberTester', 600);
  const slot0 = /Loadout: (.*?) \|/.exec(lo)?.[1].split(' ')[0];
  R('H0 the halberd is the only weapon, slot 0', slot0 === 'war_halberd' && !/war_halberd \S/.test(/Loadout: (.*?) \|/.exec(lo)?.[1] + ' '), lo.slice(-100));
  const bx = bot.entity.position.x, by = bot.entity.position.y, bz = bot.entity.position.z;
  const pin = setInterval(() => bot.chat(`/tp @s ${bx.toFixed(2)} ${by.toFixed(2)} ${bz.toFixed(2)} 0 0`), 800);

  // primary is 1.5 south of the player; 'far' sits 3.6 further south of the primary (5.1 from the player)
  // ---------- level 1
  await g(0, 0);
  await foe('prim', 0, 1.5); await foe('far', 3.6, 1.5);          // far is 3.6 east of the primary
  const f0 = await hp('far'); await sleep(8000); const f1 = await hp('far'), p1 = await hp('prim');
  R('H1a the primary was hit (non vacuous)', p1 !== null && p1 < BIG, `prim ${p1}`);
  R('H1 level 1: a foe 3.6 from the primary is outside the 2.5 cleave radius (untouched)', f0 === BIG && f1 === BIG, `${f0} -> ${f1}`);
  await clear();

  // ---------- one swing at LEVEL 1: measured before any grant, because grant only ever ADDS kills and never lowers a level
  // Swings cannot be frozen (the attack loop does not advance under /tick step), so count per swing by ARITHMETIC instead: the halberd swings
  // every ~1.2 s and each cleave victim takes the same damage as the primary. After the run, swings = primary hp lost / primary hit, and
  // the cleave victims per swing = (total hp lost by the ring) / (hit damage * swings). Foes are big enough that nobody dies.
  async function packHits(label) {
    // the primary must be the NEAREST foe (the auto attack targets the nearest), so it sits at 1.0 and the ring is centred 2.3 away with
    // radius 1.0: the closest ring point is 1.3 from the player, always farther than the primary
    await foe('prim', 0, 1.0);
    for (let i = 0; i < 8; i++) { const a = Math.PI * 2 * i / 8; await foe('r' + i, +(Math.cos(a) * 1.0).toFixed(2), +(2.3 + Math.sin(a) * 1.0).toFixed(2)); }
    const p0 = await hp('prim'); const ring0 = []; for (let i = 0; i < 8; i++) ring0.push(await hp('r' + i));
    await sleep(9000);
    const p1 = await hp('prim'); let lost = 0; for (let i = 0; i < 8; i++) { const v = await hp('r' + i); if (v !== null && ring0[i] !== null) lost += ring0[i] - v; }
    const primLost = p0 - p1, swings = Math.round(primLost / 5.9);                              // one plain hit is about 5.9 to 6.0
    const perSwing = swings > 0 ? lost / (primLost / swings) / swings : NaN;                   // ring foes hit per swing
    console.log(label, `primary lost ${primLost.toFixed(1)} (~${swings} swings), ring lost ${lost.toFixed(1)} => ${perSwing.toFixed(2)} ring foes per swing`);
    await clear(); return perSwing;
  }
  const l1 = []; for (let k = 0; k < 2; k++) l1.push(await packHits('L1'));
  // ---------- level 10
  await g(108, 0);
  const m10 = await meter();
  R('H2a the halberd is level 10', m10 && m10.level === 10, JSON.stringify(m10));
  await foe('prim', 0, 1.5); await foe('far', 3.6, 1.5);
  await sleep(8000); const g1 = await hp('far');
  R('H2 level 10: the same foe is inside the 5.0 radius and is hurt', g1 !== null && g1 < BIG, `far ${g1}`);
  await clear();

  // ---------- count: a tight ring of 8 around the primary, counting who lost hp in the first ~3 s (about one or two swings)
  // ONE swing, exactly: freeze the game, place the pack, then step one tick at a time until the primary has lost hp. Nothing can
  // land a second swing while frozen, so the count of hurt foes is the cleave of that single swing.
  const l10 = []; for (let k = 0; k < 2; k++) l10.push(await packHits('L10'));
  const a1 = l1.reduce((a, b) => a + b, 0) / l1.length, a10 = l10.reduce((a, b) => a + b, 0) / l10.length;
  R('H3 ring foes hit per swing: level 1 is about 2 (cap 2), level 10 is clearly more', a1 >= 1.5 && a1 <= 2.6 && a10 >= 4.5, `L1 avg ${a1} (${l1}), L10 avg ${a10} (${l10})`);

  // ---------- ultimate: isolate it, prove the slam target exists and is out of plain reach first
  await clear();
  await g(108, 0);
  await foe('slam', 0, -4.6);
  const s0 = await hp('slam'); await sleep(3000); const s0b = await hp('slam');
  R('U0 the slam target exists 4.6 away and the halberd cannot reach it alone', s0 === BIG && s0b === BIG, `${s0} ${s0b}`);
  await g(108, 999);
  const full = await meter();
  await foe('trig', 0, 1.5);
  let slowed = false, s1 = BIG;
  for (let k = 0; k < 40 && !slowed; k++) {                               // poll fast: the stun only lasts 2 s
    const e = await ask('/effect clear @e[tag=slam,limit=1] minecraft:slowness', 220);
    if (/Took|Removed/i.test(e)) slowed = true;
    s1 = (await hp('slam')) ?? s1;
  }
  const after = await meter();
  R('U1 the War Slam hurt the foe 4.6 blocks away', s1 < BIG, `${BIG} -> ${s1}`);
  R('U2 the slam target was slowed, and the meter was consumed (started ' + (full && full.meter) + ')', slowed && after && full && after.meter < 1000, `slowed ${slowed} | ${full && full.meter} -> ${after && after.meter}`);

  clearInterval(pin);
  await clear(); await ask('/expedition leave', 800);
  console.log(fails === 0 ? 'ALL PASS' : 'SOME FAIL ' + fails);
  bot.quit(); setTimeout(() => process.exit(0), 400);
});
