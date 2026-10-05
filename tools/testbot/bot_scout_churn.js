// Scout churn: how many DIFFERENT scout entities does one 25 s walk use? One is right; more means the purge keeps killing it.
const mineflayer = require('mineflayer');
const sleep = ms => new Promise(r => setTimeout(r, ms));
const mk = name => new Promise(res => { const b = mineflayer.createBot({ host: '127.0.0.1', port: 25565, username: name, version: '1.21.11', auth: 'offline' }); b.chat_ = []; b.on('message', m => b.chat_.push(m.toString())); b.once('spawn', () => res(b)); });
(async () => {
  const op = await mk('EmberTester'); await sleep(4000);
  const say = async (cmd, w = 600) => { op.chat_.length = 0; op.chat(cmd); await sleep(w); return op.chat_.join(' | '); };
  await say('/gamemode creative', 300);
  await say('/emberfall bot spawn ChurnBot', 2000);
  await say('/emberfall bot run ChurnBot ranger', 1500);
  for (let i = 0; i < 70; i++) { await sleep(2000); const s = await say('/emberfall bot state ChurnBot', 700); if (/run=\d/.test(s) && /weapons=\w/.test(s)) break; }
  await say('/emberfall wavestop 0', 500); await sleep(2000);
  await say('/execute as ChurnBot at @s run summon emberfall:horde_zombie ~30 ~ ~ {Tags:["walk_target"],attributes:[{id:"minecraft:movement_speed",base:0.0}],PersistenceRequired:1b}', 800);
  const ids = new Set();
  let samples = 0, present = 0;
  for (let i = 0; i < 50; i++) {
    await sleep(500);
    const r = await say('/data get entity @e[tag=emberfall_bot_scout,limit=1] UUID', 350);
    samples++;
    const m = /\[I; (-?\d+), (-?\d+), (-?\d+), (-?\d+)\]/.exec(r);
    if (m) { present++; ids.add(m.slice(1).join(',')); }
  }
  console.log('CHURN samples=' + samples + ' present=' + present + ' distinctScouts=' + ids.size);
  console.log(ids.size === 1 && present === samples ? 'PASS one steady scout' : 'FAIL scout churns (distinct=' + ids.size + ', present ' + present + '/' + samples + ')');
  await say('/emberfall bot remove ChurnBot', 1500);
  op.quit(); setTimeout(() => process.exit(0), 400);
})();
