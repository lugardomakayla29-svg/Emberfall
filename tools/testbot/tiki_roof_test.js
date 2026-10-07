// The owner asked for NO roof on the Tiki. For every tier: the pole has its mask heads (item displays) and NO block display at all
// (the old roof was a dark oak slab, a block display). CONTROL: a block display summoned beside the Tiki IS counted by the same selector,
// so a count of 0 cannot come from a selector that sees nothing. File name kept so the regress scripts still run it.
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
  // distance 12, not 6: a Corrupted pole stands over 4 blocks tall and its roof sat outside the 6 block sphere (the first version of this test
  // used 6 and could not see the elite or corrupted roofs on the shipped jar). The control is placed HIGH (y 205) where a roof would be.
  const blocksSel = '@e[type=minecraft:block_display,x=0,y=200,z=0,distance=..12]';
  const count = async sel => { const r = await ask(`/execute if entity ${sel}`, 450); return /Test passed/.test(r) ? parseInt((/Count: (\d+)/.exec(r) || [0, 1])[1], 10) : 0; };
  const heads = await count('@e[type=minecraft:item_display,x=0,y=200,z=0,distance=..12]');
  const roofs = await count(blocksSel);
  check(label + ' has its mask heads (so an empty/absent Tiki cannot pass)', heads >= wantHeads, `item displays ${heads} (want >= ${wantHeads})`);
  check(label + ' has NO roof: zero block displays near the Tiki', roofs === 0, `block displays ${roofs}`);
  await ask('/summon minecraft:block_display 0.5 205 0.5 {block_state:{Name:"minecraft:dark_oak_slab"},Tags:["ctl"]}', 700);
  const withCtl = await count(blocksSel);
  check(label + ' CONTROL: a summoned block display IS seen by the same selector', withCtl === 1, `block displays after summon ${withCtl}`);
  await ask('/kill @e[tag=ctl]', 300);
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
