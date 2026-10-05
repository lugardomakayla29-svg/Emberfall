const mineflayer = require('mineflayer'); const sleep = ms => new Promise(r => setTimeout(r, ms));
const b = mineflayer.createBot({ host: '127.0.0.1', port: 25565, username: 'EmberTester', version: '1.21.11', auth: 'offline' });
const chat = []; b.on('message', m => chat.push(m.toString()));
b.once('spawn', async () => { await sleep(5000); b.chat('/op EmberTester'); await sleep(600);
  const say = async (c, w = 800) => { chat.length = 0; b.chat(c); await sleep(w); return chat.join(' | '); };
  console.log('A1', (await say('/emberfall relic unlocks EmberTester add open_25_chests 25')).slice(0, 80));
  console.log('A2', (await say('/emberfall relic unlocks EmberTester add clear_3_challenges 2')).slice(0, 80));
  console.log('A3', (await say('/emberfall relic unlocks EmberTester')).slice(0, 160)); b.quit(); process.exit(0); });
