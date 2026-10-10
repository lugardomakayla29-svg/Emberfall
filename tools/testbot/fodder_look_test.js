// Fodder LOOK live test. Judge = SERVER REPLIES only (/attribute, /data). Proves what a server can: the right pieces are on each mob, the armour does NOT
// change how much armour the mob has (the points are cancelled), the size is right and a veteran is larger. How it LOOKS on a real client is UNSEEN.
// Needs a server started with -Demberfall.testMode=true.
const mineflayer = require('mineflayer');
const { Vec3 } = require('vec3');
const sleep = ms => new Promise(r => setTimeout(r, ms));
const bot = mineflayer.createBot({ host: '127.0.0.1', port: 25565, username: 'EmberTester', version: '1.21.11', auth: 'offline' });
const lines = []; bot.on('message', m => lines.push(m.toString())); bot.on('error', e => console.log('ERR', e.message));
const ask = async (x, w = 500) => { const n = lines.length; bot.chat(x); await sleep(w); return lines.slice(n).join(' | '); };
let fails = 0; const check = (n, ok, note = '') => { console.log((ok ? 'PASS ' : 'FAIL ') + n + ' ' + note); if (!ok) fails++; };
const num = r => { const m = /(?:is|value[^\d-]*)\s*(-?[\d.]+)\s*$/i.exec(r.trim()) || /(-?[\d.]+)\s*$/.exec(r.trim()); return m ? +m[1] : NaN; };
// A chat line from the game (a hit, a log echo) can land between the command and its reply and make one read unparseable. Retry the same read-only query
// until it parses; a value that never parses still fails the check (NaN), so this cannot turn a real problem green.
const attr = async (sel, a) => {
  for (let k = 0; k < 4; k++) { const v = num(await ask(`/attribute ${sel} minecraft:${a} get`, 600)); if (Number.isFinite(v)) return v; await sleep(300); }
  return NaN;
};
const slot = async (sel, s) => await ask(`/data get entity ${sel} equipment.${s}`, 600);
// Expected values typed here from the owner's design, NOT read from the code.
const WANT = {
  horde_shieldbearer: { normal: 1.00, vet: 1.15, pieces: ['chest', 'legs', 'feet'], kind: 'leather', dye: 102 << 16 | 73 << 8 | 80 },
  horde_charger:      { normal: 1.15, vet: 1.30, pieces: ['chest'],                 kind: 'leather', dye: 119 << 16 | 43 << 8 | 43, none: ['legs', 'feet'] },
  horde_spitter:      { normal: 0.85, vet: 0.95, pieces: ['chest', 'legs', 'feet'], kind: 'chainmail' },
};
bot.once('spawn', async () => {
  await sleep(6500);
  await ask('/kill @e[type=!player]', 600); await sleep(800);
  await ask('/gamemode creative'); await ask('/character select juggernaut', 600); await ask('/expedition', 1500);
  for (let i = 0; i < 60; i++) { await sleep(2000); if (/expedition/.test(await ask('/data get entity @s Dimension', 400))) break; }
  await sleep(1500); await ask('/emberfall wavestop 0', 400);
  // CONTROL: a plain Horde Zombie wears nothing and has the vanilla zombie armour value. Every armour reading below is compared with this.
  await ask('/emberfall spawnveteran horde_zombie', 900);
  const SEL0 = '@e[type=emberfall:horde_zombie,limit=1]';
  const baseArmor = await attr(SEL0, 'armor');
  const baseTough = await attr(SEL0, 'armor_toughness');
  check('C0 control: the plain zombie has a readable armour value', Number.isFinite(baseArmor) && Number.isFinite(baseTough), `armor=${baseArmor} toughness=${baseTough}`);
  const baseScale = await attr(SEL0, 'scale');
  check('C1 control: a plain zombie is scale 1', Math.abs(baseScale - 1) < 1e-6, `scale=${baseScale}`);
  // The VETERAN tier comes from the command; the NORMAL tier is what a wave spawns, reached through the real spawn egg (MobSpawner.spawn).
  const spawnTier = async (name, tier) => {
    await ask('/kill @e[type=!player]', 500); await sleep(500);
    if (tier === 'vet') { const r0 = await ask(`/emberfall spawnveteran ${name}`, 900); return /Spawned/.test(r0); }
    await ask(`/item replace entity @s hotbar.0 with emberfall:${name}_egg`, 450);
    bot.setQuickBarSlot(0); await sleep(250);
    const block = bot.blockAt(new Vec3(70, 64, 64));
    if (!block || block.name === 'air') console.log('NOTE egg target block is', block ? block.name : 'null');
    try { await Promise.race([bot.activateBlock(block, new Vec3(0, 1, 0)), sleep(2500)]); } catch (e) { /* the entity check below decides */ }
    await sleep(900);
    return true;
  };
  await ask('/tp @s 68 65 64', 700); await sleep(1500);
  for (const name of Object.keys(WANT)) {
    const w = WANT[name]; const type = 'emberfall:' + name; const SEL = `@e[type=${type},limit=1]`;
    const seen = {};
    for (const tier of ['normal', 'vet']) {
      const spawned = await spawnTier(name, tier);
      await sleep(1500);   // let a few ticks pass: worn-armour modifiers only reach the attribute on a tick
      const exists = await ask(`/data get entity ${SEL} Health`, 600);
      check(`${name} ${tier}: spawned`, spawned && /entity data/.test(exists), exists.slice(0, 60));
      const want = tier === 'vet' ? w.vet : w.normal;
      const sc = await attr(SEL, 'scale');
      check(`${name} ${tier}: scale is ${want}`, Math.abs(sc - want) < 0.001, `scale=${sc}`);
      seen[tier] = sc;
      const arm = await attr(SEL, 'armor'); const tough = await attr(SEL, 'armor_toughness');
      check(`${name} ${tier}: armour value equals the plain zombie's (the worn points are cancelled)`, Math.abs(arm - baseArmor) < 0.001, `armor=${arm} base=${baseArmor}`);
      // POSITIVE proof the worn pieces really carry armour points that were cancelled (a plain "armour == 0" would also pass if nothing were ever worn):
      // the cancel modifier exists on the mob and its amount is the negative of what the pieces add.
      const mods = await ask(`/data get entity ${SEL} attributes[{id:"minecraft:armor"}]`, 700);
      const cm = /fodder_look_armor_cancel[^}]*?amount:\s*(-?[\d.]+)/.exec(mods) || /amount:\s*(-?[\d.]+)[^}]*?fodder_look_armor_cancel/.exec(mods);
      check(`${name} ${tier}: the armour cancel modifier is present and negative (the pieces really add points)`, !!cm && +cm[1] < 0, cm ? `cancel=${cm[1]}` : mods.slice(-140));
      check(`${name} ${tier}: armour toughness equals the plain zombie's`, Math.abs(tough - baseTough) < 0.001, `toughness=${tough} base=${baseTough}`);
      for (const p of w.pieces) {
        const r = await slot(SEL, p);
        check(`${name} ${tier}: wears ${w.kind} ${p}`, new RegExp(`minecraft:${w.kind}_`).test(r), r.slice(-90));
        if (w.dye !== undefined) {
          const m = /dyed_color:\s*(-?\d+)/.exec(r) || /"minecraft:dyed_color":\s*(-?\d+)/.exec(r);
          check(`${name} ${tier}: ${p} dye is the head's colour`, !!m && ((+m[1]) & 0xFFFFFF) === w.dye, m ? `got ${(+m[1] & 0xFFFFFF).toString(16)} want ${w.dye.toString(16)}` : r.slice(-80));
        }
      }
      for (const p of (w.none || [])) {
        const r = await slot(SEL, p);
        check(`${name} ${tier}: wears nothing on ${p} (the charger gets ONE piece)`, /no elements matching|Found no/i.test(r), r.slice(-70));
      }
      const head = await slot(SEL, 'head');
      check(`${name} ${tier}: the custom skull is still on the head`, /player_head/.test(head), head.slice(0, 70));
      const dc = await ask(`/data get entity ${SEL} drop_chances`, 600);
      check(`${name} ${tier}: no worn piece can drop`, !/(chest|legs|feet):\s*[1-9.]/.test(dc) && !/(chest|legs|feet):\s*0\.[1-9]/.test(dc), dc.slice(-100));
    }
    check(`${name}: the measured veteran is LARGER than the measured normal one`, seen.vet > seen.normal, `normal=${seen.normal} vet=${seen.vet}`);
  }
  console.log('fodder_look_test | fails', fails); console.log(fails === 0 ? 'ALL PASS' : 'FAILED');
  bot.quit(); process.exit(fails === 0 ? 0 : 1);
});
