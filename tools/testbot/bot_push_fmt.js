const mineflayer = require('mineflayer');
const sleep = ms => new Promise(r => setTimeout(r, ms));
const mk = name => new Promise(res => { const b = mineflayer.createBot({ host: '127.0.0.1', port: 25565, username: name, version: '1.21.11', auth: 'offline' }); b.chat_ = []; b.on('message', m => b.chat_.push(m.toString())); b.once('spawn', () => res(b)); });
(async () => {
  const op = await mk('EmberTester'); await sleep(4000);
  const say = async (cmd, w = 900) => { op.chat_.length = 0; op.chat(cmd); await sleep(w); return op.chat_.join(' | '); };
  await say('/emberfall bot spawn FmtBot', 1800);
  console.log('RAW1 ' + await say('/data get entity @a[name=FmtBot] Pos', 800));
  console.log('RAW2 ' + await say('/data get entity FmtBot Pos', 800));
  console.log('RAW3 ' + await say('/execute as @a[name=FmtBot] run data get entity @s Pos', 800));
  console.log('RAW4 ' + await say('/data get entity @e[type=player,name=FmtBot,limit=1] Pos', 800));
  await say('/emberfall bot remove FmtBot', 800);
  op.quit(); setTimeout(() => process.exit(0), 300);
})();
