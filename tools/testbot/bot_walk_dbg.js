const mineflayer = require('mineflayer');
const sleep = ms => new Promise(r => setTimeout(r, ms));
const mk = name => new Promise(res => { const b = mineflayer.createBot({ host: '127.0.0.1', port: 25565, username: name, version: '1.21.11', auth: 'offline' }); b.chat_ = []; b.on('message', m => b.chat_.push(m.toString())); b.once('spawn', () => res(b)); });
(async () => {
  const op = await mk('EmberTester'); await sleep(4000);
  const say = async (cmd, w = 900) => { op.chat_.length = 0; op.chat(cmd); await sleep(w); return op.chat_.join(' | '); };
  await say('/gamemode creative', 300);
  await say('/emberfall bot spawn DbgBot', 2000);
  await say('/emberfall bot run DbgBot ranger', 1500);
  for (let i = 0; i < 70; i++) { await sleep(2000); const s = await say('/emberfall bot state DbgBot', 700); if (/run=\d/.test(s) && /weapons=\w/.test(s)) break; }
  await say('/emberfall wavestop 0', 500);
  await sleep(3000);
  console.log('DIM ' + await say('/execute as DbgBot run data get entity @s Dimension', 600));
  console.log('POS ' + await say('/data get entity DbgBot Pos', 600));
  console.log('SUMMON ' + await say('/execute as DbgBot at @s run summon minecraft:zombie ~30 ~ ~ {Tags:["walk_target"],PersistenceRequired:1b}', 900));
  console.log('COUNT1 ' + await say('/execute as DbgBot at @s run execute if entity @e[tag=walk_target]', 700));
  await sleep(2000);
  console.log('COUNT2 ' + await say('/execute as DbgBot at @s run execute if entity @e[tag=walk_target]', 700));
  console.log('ANY ' + await say('/execute as DbgBot at @s run execute if entity @e[type=minecraft:zombie,distance=..100]', 700));
  console.log('STATE ' + await say('/emberfall bot state DbgBot', 700));
  await say('/emberfall bot remove DbgBot', 1500);
  op.quit(); setTimeout(() => process.exit(0), 400);
})();
