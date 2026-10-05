// The roof slab must rest ON the top head, not inside it. Measured in a frozen tick for every tier:
// slab underside = roof anchor + translation.y (slab fills the lower half of its block, so its underside is the block's bottom)
// head top = highest head centre + half a head (head height = 0.5 * scale; scale read from each display's transformation).
const mineflayer = require('mineflayer');
const sleep = ms => new Promise(r => setTimeout(r, ms));
const bot = mineflayer.createBot({ host: '127.0.0.1', port: 25565, username: 'EmberTester', version: '1.21.11', auth: 'offline' });
const chat = []; bot.on('message', m => chat.push(m.toString())); bot.on('error', e => console.log('ERR', e.message));
const ask = async (c, w = 450) => { chat.length = 0; bot.chat(c); await sleep(w); return chat.join(' | '); };
let fails = 0; const check = (n, ok, d = '') => { console.log((ok ? 'PASS ' : 'FAIL ') + n + (d ? '  ' + d : '')); if (!ok) fails++; };
const nums = r => [...r.matchAll(/(-?\d+\.?\d*(?:E-?\d+)?)[df]/g)].map(m => parseFloat(m[1]));
const SEL = (type, extra = '') => `@e[type=${type},x=0,y=200,z=0,distance=..6${extra}]`;
async function measure(label, cmd, wantHeads) {
  await ask('/tick unfreeze', 300); await ask('/kill @e[type=!player]', 700); await sleep(2500);
  await ask(cmd, 1500);
  await ask('/execute at @s run tp @e[type=emberfall:tiki_magma,limit=1] 0.5 200 0.5', 600);
  await ask('/attribute @e[type=emberfall:tiki_magma,limit=1] minecraft:movement_speed base set 0', 2200);
  await ask('/tick freeze', 400);
  const heads = [];
  for (let i = 0; i < 6; i++) {
    const sel = SEL('minecraft:item_display', `,limit=1,sort=nearest,tag=!rd`);
    const pos = nums(await ask(`/data get entity ${sel} Pos`, 400));
    if (pos.length < 3) break;
    const xf = nums(await ask(`/data get entity ${sel} transformation.scale`, 400));
    await ask(`/tag ${sel} add rd`, 200);
    heads.push({ y: pos[1], s: xf.length ? xf[0] : NaN });
  }
  const roofSel = '@e[type=minecraft:block_display,x=0,y=200,z=0,distance=..12,limit=1]';   // Corrupted stands over 4 blocks tall and the roof can leave the 6 block sphere
  let rawRoof = await ask(`/data get entity ${roofSel} Pos`, 500);
  if (nums(rawRoof).length < 3) { await sleep(1500); rawRoof = await ask(`/data get entity ${roofSel} Pos`, 600); }   // retry once: the rig may still be building
  const rp = nums(rawRoof);
  if (rp.length < 3) console.log('   raw roof reply:', rawRoof.slice(0, 160), '| block displays near:', (await ask(`/execute if entity @e[type=minecraft:block_display,x=0,y=200,z=0,distance=..12]`, 500)).slice(0, 60));
  const rt = nums(await ask(`/data get entity ${roofSel} transformation.translation`, 400));
  await ask('/tick unfreeze', 300);
  if (rp.length < 3 || rt.length < 3 || !heads.length) { check(label + ' readings', false, `heads ${heads.length} roof ${rp} xf ${rt}`); return; }
  const top = Math.max(...heads.map(h => h.y + 0.25 * h.s));   // head is 0.5*scale tall, centred on its anchor
  const under = rp[1] + rt[1];
  check(label + ' head count', heads.length === wantHeads, `${heads.length} (want ${wantHeads})`);
  check(label + ' slab rests on the top head', Math.abs(under - top) < 0.03, `underside ${under.toFixed(3)} head top ${top.toFixed(3)} gap ${(under - top).toFixed(3)}`);
}
bot.once('spawn', async () => {
  await sleep(5000); await ask('/op EmberTester'); await ask('/gamemode creative');
  await ask('/fill -6 199 -6 6 199 14 minecraft:stone', 1200); await ask('/tp @s 0 201 10', 1500);
  await measure('FODDER', '/emberfall spawnveteran tiki_magma', 1);
  await measure('ELITE', '/emberfall spawnelite tiki_magma', 3);
  await measure('CORRUPTED', '/emberfall spawnelite tiki_magma_corrupted', 4);
  console.log(fails === 0 ? 'ALL PASS' : 'SOME FAIL ' + fails);
  bot.quit(); setTimeout(() => process.exit(0), 300);
});
