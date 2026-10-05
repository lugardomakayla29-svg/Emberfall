const mineflayer = require('mineflayer');
const bot = mineflayer.createBot({ host: '127.0.0.1', port: 25565, username: 'EmberTester', version: '1.21.11', auth: 'offline' });
const sleep = ms => new Promise(r => setTimeout(r, ms)); const lines = []; bot.on('message', m => lines.push(m.toString()));
bot.once('spawn', async () => { await sleep(5000); bot.chat('/emberfall balance EmberTester'); await sleep(900); console.log('REPLY:', JSON.stringify(lines.slice(-2))); bot.quit(); setTimeout(() => process.exit(0), 300); });
