const mineflayer = require('mineflayer');
const bot = mineflayer.createBot({ host: '127.0.0.1', port: 25565, username: 'EmberTester', version: '1.21.11', auth: 'offline' });
const sleep = ms => new Promise(r => setTimeout(r, ms));
const lines = []; bot.on('message', m => lines.push(m.toString()));
const ask = async (x, w = 500) => { const n = lines.length; bot.chat(x); await sleep(w); return lines.slice(n).join(' | '); };
const res = []; const check = (n, ok, note) => { res.push(ok); console.log((ok ? 'PASS ' : 'FAIL ') + n + ' ' + note); };
const count = async sel => {
  await ask('/scoreboard objectives add gnt dummy', 200); await ask('/scoreboard players set #n gnt -1', 200);
  await ask(`/execute store result score #n gnt if entity ${sel}`, 300);
  const r = await ask('/scoreboard players get #n gnt', 500); const m = /has (-?\d+) \[gnt\]/.exec(r) || /(-?\d+)/.exec(r); return m ? +m[1] : NaN; };
const countR = async sel => { for (let k = 0; k < 4; k++) { const v = await count(sel); if (!Number.isNaN(v) && v >= 0) return v; } return NaN; };
const G = '@e[type=emberfall:ember_guardian,limit=1]';
const PYL = '@e[type=emberfall:cinder_pylon]';
const hp1 = async () => { const r = await ask(`/data get entity ${G} Health`, 500); const m = /: ([\d.]+)f/.exec(r); return m ? +m[1] : NaN; };
const hp = async () => { for (let k = 0; k < 4; k++) { const v = await hp1(); if (!Number.isNaN(v)) return v; } return NaN; };
bot.once('spawn', async () => {
  await sleep(6500);
  await ask('/kill @e[type=!player]', 600); await ask('/kill @e[type=minecraft:item_display]', 600); await sleep(1500);
  await ask('/gamemode survival'); await ask('/effect give @s minecraft:resistance 999 4 true', 300); await ask('/effect give @s minecraft:regeneration 999 4 true', 300);
  await ask('/character select juggernaut', 600); await ask('/expedition', 1500); await sleep(1500);
  // Bury the whole play area: solid stone from 7 below to 9 above the player, so no column has a floor with 2 clear blocks.
  // The player is moved into a tiny pocket first so the run is not suffocated.
  const r0 = await ask('/data get entity @s Pos', 700); const m = /\[(-?[\d.]+)d, (-?[\d.]+)d, (-?[\d.]+)d\]/.exec(r0);
  const px = Math.floor(+m[1]), py = Math.floor(+m[2]), pz = Math.floor(+m[3]); console.log('player at', px, py, pz);
  // 6x6 tiles of 30x30 = 180x180, centred on the player: the boss can spawn up to about 30 blocks away and its ring reaches 12 more.
  for (let ix = -3; ix < 3; ix++) for (let iz = -3; iz < 3; iz++) await ask(`/fill ${px+ix*30} ${py-7} ${pz+iz*30} ${px+ix*30+29} ${py+9} ${pz+iz*30+29} minecraft:stone`, 350);
  await ask(`/setblock ${px} ${py-1} ${pz} minecraft:stone`, 300);
  await ask(`/fill ${px} ${py} ${pz} ${px} ${py+1} ${pz} minecraft:air`, 400);   // 1x2 pocket for the player
  await ask(`/tp @s ${px}.5 ${py} ${pz}.5`, 400);
  const baseP = await countR(PYL); console.log('baseline pylons', baseP);
  console.log('boss:', (await ask('/emberfall boss 0', 1200)).slice(0, 80)); await sleep(2500);
  await ask('/emberfall wavestop 0', 400);
  const g = await countR('@e[type=emberfall:ember_guardian]'); check('N0 the boss spawned', g === 1, `guardians=${g}`);
  const p = await countR(PYL); check('N1 no standable ground -> ZERO pylons placed', p === 0, `pylons=${p}`);
  // The run player's auto-weapon also hits the boss, so first MEASURE the background drop over an idle window with no command,
  // then require that my 100-damage command moved hp by at least 100 MORE than that background (not an exact 100).
  const i0 = await hp(); await sleep(3000); const i1 = await hp(); const bg = i0 - i1;
  console.log('idle background drop over 3s (auto-weapon):', bg);
  const h0 = await hp(); await ask(`/execute as ${G} run damage @s 100 minecraft:generic`, 800); const h1 = await hp();
  check('N2 NO SOFT-LOCK: with no pylons a 100 hit lands in full', (h0 - h1) >= 100 && (h0 - h1) <= 100 + Math.max(bg, 0) + 6, `hp ${h0} -> ${h1}, drop ${h0 - h1}, idle background ${bg}`);
  await ask(`/data modify entity ${G} Health set value 390f`, 500); await sleep(1500);
  const p2 = await countR(PYL); check('N3 no pylon appears at 66% either (still no ground)', p2 === 0, `pylons=${p2}`);
  await ask('/kill @e[type=emberfall:ember_guardian]', 800); await sleep(3500);
  const ge = await countR('@e[type=emberfall:ember_guardian]'); check('N4 boss removed', ge === 0, `guardians=${ge}`);
  console.log(res.every(Boolean) ? 'ALL PASS' : 'SOME FAIL');
  bot.quit(); setTimeout(() => process.exit(0), 300);
});
bot.on('error', e => console.log('ERR', e.message));
