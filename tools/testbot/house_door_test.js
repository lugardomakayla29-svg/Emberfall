// Can a real mob walk through each village house door? For each house: build the map, place the player bot INSIDE the house
// (2 cells behind the front door), spawn a zombie 2 cells OUTSIDE the door with the player as its target, and watch the zombie's
// position. PASS when the zombie ends up inside the house footprint and within 3 blocks of the player. The zombie uses the real
// vanilla navigator (one block step up, door opening), so this proves mobs can enter, and so can a player.
const mineflayer = require('mineflayer'); const fs = require('fs');
const D = JSON.parse(fs.readFileSync('../mod/src/main/resources/data/emberfall/map/expedition_map.json', 'utf8'));
const P = JSON.parse(fs.readFileSync('/tmp/door_points.json', 'utf8'));
const bot = mineflayer.createBot({ host: '127.0.0.1', port: 25565, username: 'EmberTester', version: '1.21.11', auth: 'offline' });
const sleep = ms => new Promise(r => setTimeout(r, ms));
const lines = []; bot.on('message', m => lines.push(m.toString())); bot.on('error', e => console.log('ERR', e.message));
const ask = async (x, w = 500) => { const n = lines.length; bot.chat(x); await sleep(w); return lines.slice(n).join(' | '); };
let fails = 0; const check = (n, ok, note = '') => { console.log((ok ? 'PASS ' : 'FAIL ') + n + ' ' + note); if (!ok) fails++; };
const zpos = async () => {
  const r = await ask('/data get entity @e[type=minecraft:zombie,tag=doorz,limit=1] Pos', 450);
  const m = /\[(-?[\d.]+)d, (-?[\d.]+)d, (-?[\d.]+)d\]/.exec(r); return m ? m.slice(1, 4).map(Number) : null;
};
bot.once('spawn', async () => {
  await sleep(6000);
  await ask('/gamemode creative'); await ask('/op EmberTester');
  await ask('/emberfall mapbuild 0', 1500);
  for (let i = 0; i < 90; i++) { await sleep(2000); if (lines.some(l => /MAPDONE/.test(l))) break; }
  await sleep(1500);
  await ask('/execute in emberfall:expedition run forceload add -10 -70 70 10', 3000);
  await ask('/difficulty normal'); await ask('/gamerule doMobSpawning false');
  for (const h of P) {
    const house = D.houses.find(x => x[0] === h.name); const [hx, hz, r] = [house[1], house[2], house[3]];
    await ask('/kill @e[type=minecraft:zombie,tag=doorz]', 300);
    const fy = 65 + h.floorY;       // origin y 64, surface grass at y=0 -> world 64, player stands on 65; a door layer at floorY sits at 64+1+floorY
    // player inside, in survival so the zombie targets it; resistance so it survives
    await ask(`/execute in emberfall:expedition run tp @s ${h.inside[0] + 0.5} ${fy} ${h.inside[1] + 0.5}`, 900);
    await ask('/gamemode survival'); await ask('/effect give @s minecraft:resistance 999 4 true', 200);
    await ask(`/execute in emberfall:expedition run summon minecraft:zombie ${h.outside[0] + 0.5} 65 ${h.outside[1] + 0.5} {Tags:["doorz"],PersistenceRequired:1b,CanBreakDoors:0b,Attributes:[{id:"minecraft:follow_range",base:40}]}`, 600);
    let last = null, got = false;
    for (let t = 0; t < 24; t++) {
      await sleep(1000); last = await zpos(); if (!last) continue;
      const inside = Math.abs(last[0] - hx) <= r && Math.abs(last[2] - hz) <= r;
      const dPl = Math.hypot(last[0] - (h.inside[0] + 0.5), last[2] - (h.inside[1] + 0.5));
      if (inside && dPl < 3.5) { got = true; break; }
    }
    check(`D ${h.name}: zombie walked from outside the door to the player inside`, got, JSON.stringify(last));
    await ask('/gamemode creative', 400);
  }
  await ask('/kill @e[type=minecraft:zombie,tag=doorz]', 300);
  console.log(fails === 0 ? 'ALL PASS' : 'SOME FAIL ' + fails);
  bot.quit(); setTimeout(() => process.exit(0), 400);
});
