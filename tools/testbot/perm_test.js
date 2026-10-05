const mineflayer = require('mineflayer');
const bot = mineflayer.createBot({ host: '127.0.0.1', port: 25565, username: 'PlainPlayer', version: '1.21.11', auth: 'offline' });
const sleep = ms => new Promise(r => setTimeout(r, ms));
const chat = [];
bot.on('error', e => console.log('ERROR', e));
bot.on('kicked', r => console.log('KICKED', JSON.stringify(r)));
bot.on('message', m => { const t = m.toString(); if (t.trim()) chat.push(t); });
const say = async (cmd, w = 1100) => { chat.length = 0; bot.chat(cmd); await sleep(w); return chat.join(' | '); };
let fails = 0;
const check = (label, ok, extra = '') => { console.log((ok ? 'PASS ' : 'FAIL ') + label + (extra ? '  ' + extra : '')); if (!ok) fails++; };
bot.once('spawn', async () => {
  await sleep(6000);
  const unknown = t => /Unknown or incomplete command/i.test(t);
  const hint = await say('/expedition');
  check('non-op bare /expedition is refused with the hub hint (and no run starts)', /Ember Hearth/i.test(hint) && !/Expedition started/i.test(hint), hint.slice(0, 80));
  for (const cmd of ['/character list', '/shop']) {
    const r = await say(cmd);
    check(`non-op cannot use ${cmd}`, unknown(r), r.slice(0, 70));
  }
  const leave = await say('/expedition leave');
  check('non-op CAN reach /expedition leave (any real reply, not "Unknown")', !unknown(leave) && leave.length > 0, leave.slice(0, 90));
  console.log(fails ? `RESULT: ${fails} FAILED` : 'RESULT: ALL PASSED');
  bot.quit(); process.exit(fails ? 1 : 0);
});
