// V13 isolation: pinned (NoAI) pink slimes with NO party and NO wave. Do they make pools? Does killing one make the death burst?
const mineflayer = require('mineflayer'); const sleep = ms => new Promise(r => setTimeout(r, ms));
const mk = name => new Promise(res => { const b = mineflayer.createBot({ host: '127.0.0.1', port: 25565, username: name, version: '1.21.11', auth: 'offline' }); b.chat_ = []; b.on('message', m => b.chat_.push(m.toString())); b.once('spawn', () => res(b)); });
(async () => {
  const op = await mk('EmberTester'); await sleep(3500);
  const say = async (cmd, w = 700) => { op.chat_.length = 0; op.chat(cmd); await sleep(w); return op.chat_.join(' | '); };
  await say('/gamemode survival', 300);
  await say('/effect give @s minecraft:resistance 999 0 true', 300);
  await say('/execute at @s run emberfall spawnelite pink_slime', 800);
  await say('/execute as @e[type=emberfall:pink_slime] run data merge entity @s {NoAI:1b}', 500);
  const n1 = await say('/execute if entity @e[type=emberfall:pink_slime]', 400);
  console.log('SLIME_EXISTS', /passed/.test(n1));
  await sleep(12000);
  console.log('PHASE pinned_12s_done');
  await say('/tp @s ~ ~ ~', 200);
  await say('/kill @e[type=emberfall:pink_slime]', 800);
  await sleep(3000);
  const n2 = await say('/execute if entity @e[type=emberfall:pink_slime]', 400);
  console.log('SLIME_EXISTS_AFTER_KILL', /passed/.test(n2));
  console.log('DONE'); process.exit(0);
})();
