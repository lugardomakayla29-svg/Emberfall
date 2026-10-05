// Pink slime: grows 1 -> 5 over 40 ticks keeping its health FRACTION, ends 2.6 blocks tall/wide (0.52 x 5), keeps its real
// stats (setSize overwrites them), has its own stats at full size, and bursts into ONE puddle with NO children on death.
const fs = require('fs');
const mineflayer = require('mineflayer');
const sleep = ms => new Promise(r => setTimeout(r, ms));
const bot = mineflayer.createBot({ host: '127.0.0.1', port: 25565, username: 'EmberTester', version: '1.21.11', auth: 'offline' });
const lines = []; bot.on('message', m => lines.push(m.toString())); bot.on('error', e => console.log('ERR', e.message));
const ask = async (x, w = 400) => { const n = lines.length; bot.chat(x); await sleep(w); return lines.slice(n).join(' | '); };
let fails = 0; const R = (n, ok, extra = '') => { console.log(`${ok ? 'PASS' : 'FAIL'} ${n} ${extra}`); if (!ok) fails++; };
const LOG = (process.env.EMBERFALL_HOME || '.') + '/run/server_run.log';
const trace = () => fs.readFileSync(LOG, 'utf8').split('\n').filter(l => l.includes('PINK_TEST'));
const count = async sel => { await ask(`/execute store result score #c emberfall_t if entity ${sel}`, 150); const r = await ask('/scoreboard players get #c emberfall_t', 250); const m = /has (-?\d+)/.exec(r); return m ? +m[1] : NaN; };
const attr = async (a) => { const r = await ask(`/attribute @e[type=emberfall:pink_slime,limit=1] minecraft:${a} get`, 250); const m = /is ([\d.]+)/.exec(r); return m ? +m[1] : NaN; };
bot.once('spawn', async () => {
  await sleep(5000);
  await ask('/gamemode survival'); await ask('/character select juggernaut'); await ask('/expedition', 2500);
  for (let i = 0; i < 40; i++) { await sleep(2000); if (/expedition/.test(await ask('/data get entity @s Dimension', 400))) break; }
  await sleep(2500);
  await ask('/scoreboard objectives add emberfall_t dummy', 200);
  await ask('/emberfall wavestop 0', 300); await ask('/time set midnight', 200);
  await ask('/effect give @s minecraft:resistance 999 4 true', 200); await ask('/effect give @s minecraft:regeneration 999 4 true', 200);
  await ask('/kill @e[type=!player,type=!minecraft:item_display,type=!minecraft:interaction]', 900);
  const bx = bot.entity.position.x, by = bot.entity.position.y, bz = bot.entity.position.z;
  let hold = setInterval(() => bot.chat(`/tp @s ${bx.toFixed(2)} ${by} ${bz.toFixed(2)} 0 0`), 400);
  const t0 = trace().length;
  await ask('/execute at @s positioned ~9 ~ ~ run emberfall spawnelite pink_slime', 400);
  await ask('/tag @e[type=emberfall:pink_slime,limit=1] add ps', 200);
  await ask('/data merge entity @e[tag=ps,limit=1] {Invulnerable:1b,PersistenceRequired:1b}', 300);
  await sleep(3500);                                   // growth lasts 40 ticks = 2 s
  const G = trace().slice(t0).filter(l => l.includes('grow') || l.includes('grown'));
  const sizes = G.filter(l => l.includes('grow size=')).map(l => +/size=(\d+)/.exec(l)[1]);
  const hps = G.filter(l => l.includes('grow size=')).map(l => { const m = /hp=([\d.]+)\/([\d.]+)/.exec(l); return +m[1] / +m[2]; });
  console.log(`  growth steps: sizes ${sizes.join(' ')}   hp fraction ${hps.map(x => x.toFixed(2)).join(' ')}`);
  R('P1 it grows through every size 2,3,4,5 in order', JSON.stringify(sizes) === JSON.stringify([2, 3, 4, 5]), sizes.join(','));
  R('P2 health FRACTION survives every setSize (full hp stays full)', hps.length === 4 && hps.every(f => Math.abs(f - 1) < 0.001), hps.map(x => x.toFixed(3)).join(','));
  const mh = await attr('max_health'), sp = await attr('movement_speed'), dm = await attr('attack_damage');
  console.log(`  full-size stats: max_health ${mh}, speed ${sp}, damage ${dm}`);
  R('P3 setSize did NOT leave the vanilla stats (hp 5, speed 0.7, dmg 5 would be the overwrite)', mh === 140 && Math.abs(sp - 0.26) < 0.001 && dm === 5, `hp ${mh} speed ${sp} dmg ${dm}`);
  const grown = G.filter(l => l.includes('grown')).length;
  R('P4 the growth finished exactly once (grown trace) at the last size step 5', grown === 1 && sizes[sizes.length - 1] === 5, `${grown} grown lines, last size ${sizes[sizes.length - 1]}`);
  // hitbox: a 2.6 block body is hit by a selector box; test two probes just inside and outside 1.3 blocks of its centre
  // growth is finished: freeze it (NoAI only stops the AI step, and this probe only measures size) so it cannot drift between the Pos read and the probes
  await ask('/data merge entity @e[tag=ps,limit=1] {NoAI:1b}', 300); await sleep(500);
  const pos = await ask('/data get entity @e[tag=ps,limit=1] Pos', 250); const pm = /\[(-?[\d.]+)d, (-?[\d.]+)d, (-?[\d.]+)d\]/.exec(pos);
  const px = +pm[1], py = +pm[2], pz = +pm[3];
  const inside = await count(`@e[tag=ps,x=${px + 1.2},y=${py + 0.3},z=${pz},dx=0,dy=0,dz=0]`);
  const outside = await count(`@e[tag=ps,x=${px + 1.6},y=${py + 0.3},z=${pz},dx=0,dy=0,dz=0]`);
  const tall = await count(`@e[tag=ps,x=${px},y=${py + 2.4},z=${pz},dx=0,dy=0,dz=0]`);
  const above = await count(`@e[tag=ps,x=${px},y=${py + 2.8},z=${pz},dx=0,dy=0,dz=0]`);
  console.log(`  hitbox probes: 1.2 to the side ${inside}, 1.6 to the side ${outside}, 2.4 up ${tall}, 2.8 up ${above}`);
  R('P5 the body is about 2.6 blocks across and tall (not 5)', inside === 1 && outside === 0 && tall === 1 && above === 0, `${inside}${outside}${tall}${above}`);
  // death: kill it and count every slime left, and the burst trace
  await ask('/data merge entity @e[tag=ps,limit=1] {Invulnerable:0b}', 250);
  const b0 = trace().length;
  await ask('/kill @e[tag=ps]', 300); await sleep(800);
  const left = await count('@e[type=emberfall:pink_slime]'), vanilla = await count('@e[type=minecraft:slime]');
  const B = trace().slice(b0); const bursts = B.filter(l => l.includes('PINK_TEST burst size=')).length, pools = B.filter(l => l.includes('pool add kind=burst') || l.includes('pool refresh kind=burst')).length;
  console.log(`  after death: pink slimes ${left}, vanilla slimes ${vanilla}, burst traces ${bursts}, pool events ${pools}`);
  R('P6 death leaves no children (no split into 2 to 4 copies)', left === 0 && vanilla === 0, `${left} pink, ${vanilla} vanilla`);
  R('P7 death bursts exactly once and the burst reaches the puddle list (new or merged)', bursts === 1 && pools === 1, `${bursts} burst traces, ${pools} burst pool events`);
  clearInterval(hold);
  await ask('/kill @e[type=!player,type=!minecraft:item_display,type=!minecraft:interaction]', 400); await ask('/expedition leave', 800);
  console.log(fails === 0 ? 'ALL PASS' : 'SOME FAIL ' + fails);
  bot.quit(); setTimeout(() => process.exit(0), 300);
});
