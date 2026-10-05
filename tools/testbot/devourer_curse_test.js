// Boss Curse on the DEVOURER (the Guardian is covered by shrine_test S17). Curse IV goes through the real C2S shrine packet.
// Judge = server replies only: max health 260 x 1.5 = 390, and the phase 2 wave is 3 minis (base 2 x 1.5 = 3.0, no dice involved).
// usage: node devourer_curse_test.js <tier 0..4>   (tier 0 = control run, must show 260 health and exactly 2 minis)
const mineflayer = require('mineflayer'); const fs = require('fs');
const TIER = +(process.argv[2] || 4);
const D = JSON.parse(fs.readFileSync('../mod/src/main/resources/data/emberfall/map/expedition_map.json', 'utf8'));
const curse = D.shrines.find(s => s.type === 'curse');
const sleep = ms => new Promise(r => setTimeout(r, ms));
const bot = mineflayer.createBot({ host: '127.0.0.1', port: 25565, username: 'EmberTester', version: '1.21.11', auth: 'offline' });
const lines = []; bot.on('message', m => lines.push(m.toString())); bot.on('error', e => console.log('ERR', e.message));
const ask = async (x, w = 500) => { const n = lines.length; bot.chat(x); await sleep(w); return lines.slice(n).join(' | '); };
let fails = 0; const check = (n, ok, note = '') => { console.log((ok ? 'PASS ' : 'FAIL ') + n + ' ' + note); if (!ok) fails++; };
const sendChoose = (type, option) => { const t = Buffer.from(type, 'utf8'); bot._client.write('custom_payload', { channel: 'emberfall:choose_shrine', data: Buffer.concat([Buffer.from([t.length]), t, Buffer.from([option])]) }); };
const count = async sel => { await ask('/scoreboard objectives add dcc dummy', 200); await ask('/scoreboard players set #n dcc -1', 200);
  await ask(`/execute store result score #n dcc if entity ${sel}`, 300); const r = await ask('/scoreboard players get #n dcc', 500); const m = /has (-?\d+) \[dcc\]/.exec(r); return m ? +m[1] : NaN; };
const countR = async sel => { for (let k = 0; k < 4; k++) { const v = await count(sel); if (!Number.isNaN(v) && v >= 0) return v; } return NaN; };
const hp1 = async () => { const r = await ask('/data get entity @e[type=emberfall:devourer_brain,limit=1] Health', 500); const m = /: ([\d.]+)f/.exec(r); return m ? +m[1] : NaN; };
const hp = async () => { for (let k = 0; k < 4; k++) { const v = await hp1(); if (!Number.isNaN(v)) return v; } return NaN; };
bot.once('spawn', async () => {
  await sleep(6500);
  await ask('/kill @e[type=!player]', 600); await sleep(1000);
  await ask('/gamemode survival'); await ask('/effect give @s minecraft:resistance 999 4 true', 300); await ask('/effect give @s minecraft:regeneration 999 4 true', 300);
  await ask('/character select juggernaut', 600); await ask('/expedition', 1500);
  for (let i = 0; i < 60; i++) { await sleep(2000); if (/expedition/.test(await ask('/data get entity @s Dimension', 400))) break; }
  await sleep(1500);
  if (TIER > 0) {
    await ask(`/tp @s ${curse.x + 4} 66 ${curse.z + 4}`, 1500);
    sendChoose('curse', TIER); await sleep(900);
  }
  const st = await ask('/emberfall shrinestate 0', 700); console.log('state:', st.slice(0, 140));
  const m = /curse=(\d+).*?stat=([\d.]+) spawn=([\d.]+)/.exec(st);
  check(`C0 shrine state shows curse tier ${TIER}`, m && +m[1] === TIER, st.slice(0, 100));
  const wantHp = 260 * (TIER === 0 ? 1 : 1.1 + 0.1 * TIER), wantMinis = { 0: 2, 1: null, 2: null, 3: null, 4: 3 }[TIER];
  console.log('boss:', (await ask('/emberfall bossdevourer 0', 1200)).slice(0, 90)); await sleep(2500);
  await ask('/emberfall wavestop 0', 400); await ask('/kill @e[type=!player,type=!emberfall:devourer_brain,type=!emberfall:devourer_spawn,type=!minecraft:item_display]', 600);
  const h0 = await hp();
  check(`C1 Devourer max health is ${wantHp} (260 x ${(wantHp / 260).toFixed(1)})`, Math.abs(h0 - wantHp) < 6, `hp=${h0}`);
  const minis = () => countR('@e[type=emberfall:devourer_spawn]');
  const m0 = await minis(); check('C2 no minis before phase 2', m0 === 0, `minis=${m0}`);
  await ask(`/data modify entity @e[type=emberfall:devourer_brain,limit=1] Health set value ${(wantHp * 0.60).toFixed(1)}f`, 500);
  await ask('/effect give @e[type=emberfall:devourer_brain,limit=1] minecraft:resistance 60 4 true', 200);
  let got = 0; for (let w = 0; w < 10; w++) { await sleep(500); got = await minis(); if (got >= 2) break; } await sleep(800); got = await minis();
  console.log(`phase 2 wave at curse ${TIER}: ${got} minis`);
  if (wantMinis !== null) check(`C3 phase 2 spawns exactly ${wantMinis} minis`, got === wantMinis, `minis=${got}`);
  else check('C3 phase 2 spawns 2 or 3 minis (dice decides)', got === 2 || got === 3, `minis=${got}`);
  console.log(fails === 0 ? 'ALL PASS' : 'SOME FAIL ' + fails); bot.quit(); setTimeout(() => process.exit(0), 400);
});
