// Probe (not a suite): which commands end a BOT's run? One fresh bot per candidate, same setup, reads bot state after.
const mineflayer = require('mineflayer');
const sleep = ms => new Promise(r => setTimeout(r, ms));
const mk = name => new Promise(res => { const b = mineflayer.createBot({ host: '127.0.0.1', port: 25565, username: name, version: '1.21.11', auth: 'offline' }); b.chat_ = []; b.on('message', m => b.chat_.push(m.toString())); b.on('error', () => {}); b.once('spawn', () => res(b)); });
(async () => {
  const op = await mk('EmberTester'); await sleep(4000);
  const say = async (cmd, w = 900) => { op.chat_.length = 0; op.chat(cmd); await sleep(w); return op.chat_.join(' | '); };
  const bst = async n => (await say(`/emberfall bot state ${n}`, 700)).replace(/.*BOT state /, '');
  const cands = [['K1', '/kill NAME'], ['K2', '/damage NAME 1000 minecraft:generic'], ['K3', '/damage NAME 1000 minecraft:generic_kill'], ['K4', '/damage NAME 1000 minecraft:mob_attack']];
  for (const [tag, tmpl] of cands) {
    const n = 'Probe' + tag;
    await say(`/emberfall bot spawn ${n}`, 2000);
    await say(`/emberfall bot run ${n} ranger`, 1500);
    let s = '';
    for (let i = 0; i < 70; i++) { await sleep(2000); s = await bst(n); if (/run=\d/.test(s) && /weapons=\w/.test(s)) break; }
    const slot = (/run=(\d+)/.exec(s) || [])[1];
    await say(`/emberfall wavestop ${slot}`, 500);
    await say('/kill @e[type=!player,type=!minecraft:item_display,type=!minecraft:interaction]', 600);
    const before = await bst(n);
    const r = await say(tmpl.replace('NAME', n), 1500); await sleep(2500);
    const after = await bst(n);
    console.log(`${tag} ${tmpl.replace('NAME', n)} -> reply: ${r.slice(0, 70)} | BEFORE ${before.slice(0, 50)} | AFTER ${after.slice(0, 50)}`);
    await say(`/emberfall bot remove ${n}`, 1500);
    await sleep(2000);
  }
  process.exit(0);
})();
